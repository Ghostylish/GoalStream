package com.example.footballlive.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val required: Boolean
)

class UpdateRepository {
    // URL файла update.json. Если хранить его как release asset, замени на прямую ссылку GitHub Releases.
    private val updateManifestUrl =
        "https://raw.githubusercontent.com/Ghostylish/GoalStream/master/update.json"

    suspend fun checkForUpdate(context: Context): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val currentVersionCode = getCurrentVersionCode(context)
            val connection = (URL(updateManifestUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }

            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                AppUpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.getString("versionName"),
                    apkUrl = json.getString("apkUrl"),
                    changelog = json.optString("changelog"),
                    required = json.optBoolean("required", false)
                ).takeIf { it.versionCode > currentVersionCode }
            }
        }
    }

    suspend fun downloadApk(context: Context, updateInfo: AppUpdateInfo): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val apkDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(apkDir, "GoalStream-${updateInfo.versionName}.apk")

                val connection = (URL(updateInfo.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                }

                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                apkFile
            }
        }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }

        context.startActivity(intent)
    }

    private fun getCurrentVersionCode(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
}
