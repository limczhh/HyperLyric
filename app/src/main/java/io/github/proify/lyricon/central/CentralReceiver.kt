/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.proify.lyricon.central.provider.ProviderManager
import io.github.proify.lyricon.central.provider.RemoteProvider
import io.github.proify.lyricon.provider.IProviderBinder
import io.github.proify.lyricon.provider.ProviderInfo

internal object CentralReceiver : BroadcastReceiver() {

    private const val TAG = "CentralReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive action: ${intent?.action}")
        if (context == null) {
            Log.e(TAG, "Context is null in onReceive!")
            return
        }

        if (intent?.action == Constants.ACTION_REGISTER_PROVIDER) {
            Log.d(TAG, "Action matches REGISTER_PROVIDER, start registering...")
            registerProvider(intent)
        }
    }

    private inline fun <reified T> getBinder(intent: Intent): T? {
        val binder = intent.getBundleExtra(Constants.EXTRA_BUNDLE)
            ?.getBinder(Constants.EXTRA_BINDER) ?: return null

        return when (T::class) {
            IProviderBinder::class -> IProviderBinder.Stub.asInterface(binder) as? T
            else -> {
                Log.e(TAG, "Unknown binder type")
                null
            }
        }
    }

    private fun registerProvider(intent: Intent) {
        val bundle = intent.getBundleExtra(Constants.EXTRA_BUNDLE)
        if (bundle == null) {
            Log.e(TAG, "Extra bundle is null!")
            return
        }
        val binder = getBinder<IProviderBinder>(intent)
        if (binder == null) {
            Log.e(TAG, "Failed to extract IProviderBinder from intent!")
            return
        }
        Log.d(TAG, "Successfully extracted binder, fetching provider info...")
        var provider: RemoteProvider? = null

        try {
            val providerInfo = binder.providerInfo
                ?.toString(Charsets.UTF_8)
                ?.let { json.decodeFromString(ProviderInfo.serializer(), it) }

            if (providerInfo?.providerPackageName.isNullOrBlank() ||
                providerInfo.playerPackageName.isBlank()
            ) {
                Log.e(TAG, "Provider info is invalid")
                return
            }

            val registered = ProviderManager.getProvider(providerInfo)
            if (registered != null) {
                provider = registered
                Log.e(
                    TAG,
                    "Provider already registered, Sharing the same player service $providerInfo"
                )
            } else {
                provider = RemoteProvider(binder, providerInfo)
                ProviderManager.register(provider)
                Log.d(TAG, "Provider registered: $providerInfo")
            }

            binder.onRegistrationCallback(provider.service)

        } catch (e: Exception) {
            Log.e(TAG, "Provider registration failed", e)
            provider?.let { ProviderManager.unregister(it) }
        }
    }
}