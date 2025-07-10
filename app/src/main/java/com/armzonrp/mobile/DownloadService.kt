package com.armzonrp.mobile

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadService : JobService() {
    companion object {
        const val ACTION_PROGRESS = "com.armzonrp.mobile.DOWNLOAD_PROGRESS"
        const val ACTION_COMPLETE = "com.armzonrp.mobile.DOWNLOAD_COMPLETE"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SUCCESS = "success"
        const val ARCHIVE_NAME = "game_archive.zip"

        fun start(context: Context, url: String, downloadDir: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra("URL", url)
                putExtra("DOWNLOAD_DIR", downloadDir)
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        val url = params?.extras?.getString("URL") ?: return false
        val destDir = params?.extras?.getString("DOWNLOAD_DIR") ?: return false

        scope.launch {
            try {
                val outputFile = File(destDir, ARCHIVE_NAME).apply {
                    parentFile?.mkdirs()
                    if (exists()) delete()
                }

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MobileApp/1.0")
                }

                sendProgress(0, "Connecting...")

                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val totalBytes = connection.contentLength
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var lastProgress = -1

                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break

                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            val progress = (downloaded * 100 / totalBytes).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                sendProgress(progress, "Downloading... $progress%")
                            }
                        }
                    }
                }

                sendProgress(100, "Download complete")
                sendComplete(true, "File downloaded successfully")
            } catch (e: Exception) {
                sendComplete(false, "Download failed: ${e.message}")
            } finally {
                params?.let { jobFinished(it, false) }
            }
        }

        return true
    }

    private fun sendProgress(progress: Int, message: String) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(ACTION_PROGRESS)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    private fun sendComplete(success: Boolean, message: String) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(ACTION_COMPLETE)
                .putExtra(EXTRA_SUCCESS, success)
                .putExtra(EXTRA_MESSAGE, message)
        )
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}