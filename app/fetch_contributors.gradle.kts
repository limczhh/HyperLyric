import groovy.json.JsonSlurper
import java.net.URL
import java.io.File

tasks.register("generateContributors") {
    doLast {
        try {
            val url = URL("https://api.github.com/repos/limczhh/HyperLyric/contributors")
            val connection = url.openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            
            val jsonParser = JsonSlurper()
            val contributors = jsonParser.parseText(response) as List<Map<String, Any>>
            
            var content = """package com.lidesheng.hyperlyric.ui.utils

data class ContributorItem(
    val name: String,
    val summary: String,
    val githubUrl: String,
    val avatarRes: Int
)

object ContributorsProvider {
    val list = listOf(
"""
            for (user in contributors) {
                val login = user["login"] as String
                val htmlUrl = user["html_url"] as String
                // GitHub 头像 URL 默认带有 ?v=4 等参数，我们直接追加 &s=120 限制分辨率为 120x120
                val avatarUrl = (user["avatar_url"] as String) + "&s=120"
                if (login.endsWith("[bot]")) continue
                
                val safeLogin = login.lowercase().replace("-", "_")
                val drawableName = "contributor_" + safeLogin
                
                try {
                    val drawableDir = File(projectDir, "src/main/res/drawable")
                    drawableDir.listFiles()?.filter { it.name.startsWith(drawableName + ".") }?.forEach { it.delete() }
                    val imgBytes = URL(avatarUrl).readBytes()
                    File(drawableDir, "$drawableName.png").writeBytes(imgBytes)
                } catch(e: Exception) {
                    println("Failed to download avatar for " + login)
                }
                
                var name = login
                try {
                    val userUrl = URL("https://api.github.com/users/" + login)
                    val userConn = userUrl.openConnection()
                    userConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    val userResp = userConn.getInputStream().bufferedReader().use { it.readText() }
                    val userDetail = jsonParser.parseText(userResp) as Map<String, Any>
                    val actualName = userDetail["name"] as? String
                    if (!actualName.isNullOrEmpty()) {
                        name = actualName
                    }
                } catch(e: Exception) {
                    // Fallback to login
                }
                
                content += "        ContributorItem(\"" + name + "\", \"@" + login + "\", \"" + htmlUrl + "\", com.lidesheng.hyperlyric.R.drawable." + drawableName + "),\n"
            }
            content += "    )\n}\n"
            
            val file = File(projectDir, "src/main/java/com/lidesheng/hyperlyric/ui/utils/ContributorsProvider.kt")
            file.writeText(content)
            println("ContributorsProvider.kt generated.")
        } catch (e: Exception) {
            println("Failed to fetch contributors: ${e.message}")
        }
    }
}
