package com.lidesheng.hyperlyric.root.plugin

import android.app.Application
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import com.lidesheng.hyperlyric.plugin.api.HYPERLYRIC_PLUGIN_API_VERSION
import com.lidesheng.hyperlyric.plugin.core.PluginArchive
import com.lidesheng.hyperlyric.plugin.core.PluginArchiveReader
import com.lidesheng.hyperlyric.plugin.core.PluginConstants
import io.github.libxposed.api.XposedModule
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Reads, validates and materializes a remote plugin archive before ART sees its DEX files. */
internal class PluginArchiveLoader(
    private val module: XposedModule,
    private val application: Application,
) {
    companion object {
        private const val MAX_LOAD_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 100L
    }

    /**
     * Remote plugin files can be observed while the app is replacing them. The streaming ZIP
     * reader is intentionally bounded, but it can still accept a prefix containing the local
     * entries before the central directory is complete. Validate the materialized file with the
     * random-access ZIP reader before handing it to ART, and retry a transient replacement race.
     */
    fun load(pluginId: String, fileName: String): Pair<PluginArchive, File> {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < MAX_LOAD_ATTEMPTS) {
            try {
                val archiveBytes = module.openRemoteFile(fileName).useReadOnly { input ->
                    PluginArchiveReader.readBounded(input)
                }
                val archive = PluginArchiveReader.read(archiveBytes)
                require(archive.manifest.id == pluginId) {
                    "Plugin id does not match enabled registry"
                }
                require(archive.manifest.apiVersion <= HYPERLYRIC_PLUGIN_API_VERSION) {
                    "Plugin API is newer than host"
                }

                val archiveFile = materialize(fileName, archiveBytes)
                validateMaterialized(archiveFile, archive)
                return archive to archiveFile
            } catch (error: Exception) {
                lastError = error
                attempt++
                if (attempt < MAX_LOAD_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw interrupted
                    }
                }
            }
        }
        throw lastError ?: IllegalStateException("Unable to load plugin archive")
    }

    /**
     * ZipFile checks the central directory/local header relationship and reading each entry also
     * verifies its compressed data and CRC. This is the same shape of validation ART performs
     * when it opens a DEX from a ZIP.
     */
    private fun validateMaterialized(
        archiveFile: File,
        archive: PluginArchive,
    ) {
        ZipFile(archiveFile).use { zip ->
            val manifestEntry = zip.getEntry(PluginConstants.ZIP_MANIFEST)
                ?: error("Plugin ZIP has no manifest.json")
            consumeEntry(
                zip = zip,
                entry = manifestEntry,
                limit = PluginConstants.MAX_PLUGIN_MANIFEST_BYTES,
            )

            archive.dexFiles.forEachIndexed { index, dexBytes ->
                val entryName = if (index == 0) {
                    PluginConstants.ZIP_DEX
                } else {
                    "classes${index + 1}.dex"
                }
                val dexEntry = zip.getEntry(entryName)
                    ?: error("Plugin ZIP has no $entryName")
                require(dexEntry.size < 0L || dexEntry.size == dexBytes.size.toLong()) {
                    "$entryName size does not match the validated archive"
                }
                consumeEntry(
                    zip = zip,
                    entry = dexEntry,
                    limit = PluginConstants.MAX_PLUGIN_DEX_BYTES,
                )
            }
        }
    }

    private fun consumeEntry(
        zip: ZipFile,
        entry: ZipEntry,
        limit: Int,
    ) {
        require(entry.size < 0L || entry.size <= limit.toLong()) {
            "Plugin ZIP entry is too large: ${entry.name}"
        }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        zip.getInputStream(entry).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit.toLong()) {
                    "Plugin ZIP entry is too large: ${entry.name}"
                }
            }
        }
        require(entry.size < 0L || total == entry.size) {
            "Plugin ZIP entry size is inconsistent: ${entry.name}"
        }
    }

    /**
     * DelegateLastClassLoader loads dex files from a path, so persist the already validated ZIP
     * before creating the loader. The file is process-local cache data and can be rebuilt after a
     * SystemUI restart.
     */
    private fun materialize(fileName: String, archiveBytes: ByteArray): File {
        val directory = File(application.codeCacheDir, "hyperlyric_plugin_dex")
        require(directory.exists() || directory.mkdirs()) {
            "Unable to create plugin dex cache directory"
        }
        val file = File(directory, fileName)
        val atomicFile = AtomicFile(file)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(archiveBytes)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            output?.let(atomicFile::failWrite)
            throw error
        }
        return file
    }
}

private inline fun <T> ParcelFileDescriptor.useReadOnly(
    block: (java.io.InputStream) -> T
): T = ParcelFileDescriptor.AutoCloseInputStream(this).use(block)
