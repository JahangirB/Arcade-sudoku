package com.arcade.sudoku.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.arcade.sudoku.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A published build, as described by version.json next to the APK in the latest release. */
data class UpdateInfo(val versionCode: Int, val versionName: String) {
    val isNewer get() = versionCode > BuildConfig.VERSION_CODE
}

/**
 * Checks GitHub for a newer release, downloads it and hands it to the Android installer.
 * Android only accepts the update if it is signed with the same key as the installed app.
 */
class Updater(context: Context) {
    private val context = context.applicationContext
    private val dir get() = File(context.cacheDir, "updates")

    suspend fun latest(): UpdateInfo = withContext(Dispatchers.IO) {
        val json = JSONObject(open("$BASE/version.json").inputStream.bufferedReader().use { it.readText() })
        UpdateInfo(json.getInt("versionCode"), json.optString("versionName"))
    }

    /** Downloads the latest APK, reporting progress from 0 to 1 when the size is known. */
    suspend fun download(onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val part = File(dir, "ArcadeSudoku.apk.part")
        val apk = File(dir, "ArcadeSudoku.apk")
        val conn = open("$BASE/ArcadeSudoku.apk")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        apk.delete()
        check(part.renameTo(apk)) { "Could not save the update." }
        apk
    }

    /** Whether the user has allowed this app to install updates. */
    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows this app to install updates. */
    fun permissionIntent() = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Shows the Android "update this app?" screen for the downloaded APK. */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun start(intent: Intent) = context.startActivity(intent)

    private fun open(url: String): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            // GitHub redirects release downloads to its file servers.
            instanceFollowRedirects = true
            setRequestProperty("Cache-Control", "no-cache")
        }
        val status = conn.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IOException("Update server answered HTTP $status")
        }
        return conn
    }

    private companion object {
        val BASE: String = BuildConfig.UPDATE_BASE_URL
    }
}
