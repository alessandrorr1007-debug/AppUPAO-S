package com.example.upaos.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import java.io.File

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size") val size: Long
)

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList()
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseNotes: String?,
    val downloadUrl: String?,
    val releasePageUrl: String
)

object AppUpdater {
    private const val TAG = "AppUpdater"
    private const val GITHUB_REPO = "alessandrorr1007-debug/AppUPAO-S"
    private val client = OkHttpClient()
    private val gson = Gson()

    /**
     * Consulta a GitHub Releases para ver si hay una versión superior a la instalada.
     */
    suspend fun checkForUpdates(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersionName(context)
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "UPAO-S-Android-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateInfo(
                        hasUpdate = false,
                        latestVersion = currentVersion,
                        currentVersion = currentVersion,
                        releaseNotes = null,
                        downloadUrl = null,
                        releasePageUrl = "https://github.com/$GITHUB_REPO/releases"
                    )
                }

                val body = response.body?.string() ?: return@withContext UpdateInfo(
                    hasUpdate = false,
                    latestVersion = currentVersion,
                    currentVersion = currentVersion,
                    releaseNotes = null,
                    downloadUrl = null,
                    releasePageUrl = "https://github.com/$GITHUB_REPO/releases"
                )

                val release = gson.fromJson(body, GitHubRelease::class.java)
                val rawTag = release.tagName.trim()
                val latestClean = rawTag.removePrefix("v").trim()
                val currentClean = currentVersion.removePrefix("v").trim()

                val hasUpdate = isNewerVersion(latestClean, currentClean)

                // Buscar el APK en los assets
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                val apkUrl = apkAsset?.downloadUrl ?: "https://github.com/$GITHUB_REPO/releases/latest/download/UPAO-S.apk"

                return@withContext UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = rawTag,
                    currentVersion = currentVersion,
                    releaseNotes = release.body,
                    downloadUrl = apkUrl,
                    releasePageUrl = release.htmlUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking updates", e)
            return@withContext UpdateInfo(
                hasUpdate = false,
                latestVersion = currentVersion,
                currentVersion = currentVersion,
                releaseNotes = null,
                downloadUrl = null,
                releasePageUrl = "https://github.com/$GITHUB_REPO/releases"
            )
        }
    }

    /**
     * Descarga el APK usando DownloadManager de Android e inicia la instalación al terminar.
     */
    fun downloadAndInstall(context: Context, downloadUrl: String, versionName: String) {
        val fileName = "UPAO-S-${versionName.replace('/', '_')}.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Actualizando UPAO S")
            .setDescription("Descargando versión $versionName...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destinationFile))
            .setMimeType("application/vnd.android.package-archive")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try {
                        installApk(context, destinationFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error iniciando instalador", e)
                    } finally {
                        try {
                            context.unregisterReceiver(this)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    /**
     * Lanza el Intent nativo de Android para instalar el APK descargado mediante FileProvider.
     */
    fun installApk(context: Context, file: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(intent)
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.5"
        } catch (_: Exception) {
            "1.5"
        }
    }

    /**
     * Compara números de versiones en formato SemVer (ej. "1.5.1" > "1.5.0").
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val lParts = latest.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val cParts = current.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(lParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val l = lParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (_: Exception) {}
        return false
    }
}
