package com.armzonrp.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class DownloadCompleteReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DownloadReceiver"

        // Download constants
        const val ACTION_PROGRESS = "com.armzonrp.mobile.DOWNLOAD_PROGRESS"
        const val ACTION_COMPLETE = "com.armzonrp.mobile.DOWNLOAD_COMPLETE"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_SPEED = "download_speed"
        const val EXTRA_TIME_REMAINING = "time_remaining"
        const val EXTRA_DOWNLOADED_SIZE = "downloaded_size"
        const val EXTRA_TOTAL_SIZE = "total_size"
        const val EXTRA_ERROR_MESSAGE = "error_message"

        // Install constants
        const val ACTION_INSTALL_STARTED = "com.armzonrp.INSTALL_STARTED"
        const val ACTION_INSTALL_PROGRESS = "com.armzonrp.INSTALL_PROGRESS"
        const val ACTION_INSTALL_FINISHED = "com.armzonrp.INSTALL_FINISHED"
        const val EXTRA_INSTALL_SUCCESS = "install_success"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PROGRESS -> {
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val speed = intent.getStringExtra(EXTRA_SPEED) ?: ""
                val remaining = intent.getStringExtra(EXTRA_TIME_REMAINING) ?: ""
                val downloadedSize = intent.getStringExtra(EXTRA_DOWNLOADED_SIZE) ?: ""
                val totalSize = intent.getStringExtra(EXTRA_TOTAL_SIZE) ?: ""

                Log.d(TAG, "Download progress: $progress% | Speed: $speed | Remaining: $remaining")

                // Create new intent with all download info
                val broadcastIntent = Intent(ACTION_PROGRESS).apply {
                    putExtra(EXTRA_PROGRESS, progress)
                    putExtra(EXTRA_SPEED, speed)
                    putExtra(EXTRA_TIME_REMAINING, remaining)
                    putExtra(EXTRA_DOWNLOADED_SIZE, downloadedSize)
                    putExtra(EXTRA_TOTAL_SIZE, totalSize)
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
            }

            ACTION_COMPLETE -> {
                val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)
                Log.d(TAG, "Download completed, success: $success")

                if (success) {
                    // Notify about install start
                    LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(Intent(ACTION_INSTALL_STARTED))

                    // Simulate installation process
                    Thread {
                        for (progress in 0..100 step 5) {
                            Thread.sleep(150)
                            val progressIntent = Intent(ACTION_INSTALL_PROGRESS).apply {
                                putExtra(EXTRA_PROGRESS, progress)
                            }
                            LocalBroadcastManager.getInstance(context)
                                .sendBroadcast(progressIntent)
                        }
                        val finishIntent = Intent(ACTION_INSTALL_FINISHED).apply {
                            putExtra(EXTRA_INSTALL_SUCCESS, true)
                        }
                        LocalBroadcastManager.getInstance(context)
                            .sendBroadcast(finishIntent)
                    }.start()
                } else {
                    val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE)
                        ?: "Ошибка загрузки"
                    val errorIntent = Intent(ACTION_INSTALL_FINISHED).apply {
                        putExtra(EXTRA_INSTALL_SUCCESS, false)
                        putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
                    }
                    LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(errorIntent)
                }
            }
        }
    }
}