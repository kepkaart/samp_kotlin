package com.armzonrp.mobile

import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.armzonrp.mobile.databinding.ActivityMainBinding
import com.armzonrp.mobile.databinding.DialogServerSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var dialogBinding: DialogServerSettingsBinding
    private val gameManager: GameManager by lazy { GameManager.getInstance(this) }
    private var isDownloading = false
    private var serverIp = "your.server.ip"
    private var serverPort = 7777
    private val gameArchiveUrl = "https://zakaz11.kepka-art.ru/game/mobile.zip"

    private val storagePermission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    private val permissionsRequestCode = 101

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.armzonrp.mobile.DOWNLOAD_PROGRESS" -> {
                    val progress = intent.getIntExtra("progress", 0)
                    val message = intent.getStringExtra("message") ?: ""
                    updateProgress(progress, message)
                }
                "com.armzonrp.mobile.DOWNLOAD_COMPLETE" -> {
                    val success = intent.getBooleanExtra("success", false)
                    val message = intent.getStringExtra("message") ?: ""

                    isDownloading = false
                    setLoading(false)

                    if (success) {
                        onDownloadComplete()
                    } else {
                        showError(message)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        dialogBinding = DialogServerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameManager.setLaunchCallback(object : GameManager.GameLaunchCallback {
            override fun onError(message: String) {
                runOnUiThread { showError(message) }
            }

            override fun showLoading(show: Boolean) {
                runOnUiThread { setLoading(show) }
            }

            override fun onProgress(progress: Int, message: String) {
                runOnUiThread { updateProgress(progress, message) }
            }
        })

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        with(binding) {
            playButton.setOnClickListener {
                if (isDownloading) return@setOnClickListener

                if (gameManager.areGameFilesValid()) {
                    launchGame()
                } else {
                    startInstallation()
                }
            }

            settingsButton.setOnClickListener {
                showServerSettingsDialog()
            }

            progressBar.max = 100
            setLoading(false)
        }
    }

    private fun launchGame() {
        setLoading(true, "Launching game...")
        try {
            gameManager.launchGame()
            Toast.makeText(this, "Game launched!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError("Launch failed: ${e.localizedMessage}")
        } finally {
            setLoading(false)
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, storagePermission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(storagePermission), permissionsRequestCode)
        } else {
            checkGameFiles()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionsRequestCode && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkGameFiles()
        } else {
            showError("Необходимы разрешения для работы приложения")
        }
    }

    private fun checkGameFiles() {
        binding.playButton.text = if (gameManager.areGameFilesValid()) {
            "PLAY"
        } else {
            "INSTALL"
        }
    }

    private fun startInstallation() {
        if (!isNetworkAvailable()) {
            showError("No internet connection")
            return
        }

        isDownloading = true
        setLoading(true, "Starting download...")
        updateProgress(0, "Preparing download...")

        val intent = Intent(this, DownloadService::class.java).apply {
            putExtra("URL", gameArchiveUrl)
            putExtra("DOWNLOAD_DIR", gameManager.getGameDirectory().absolutePath)
        }
        startService(intent)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun onDownloadComplete() {
        val archive = File(gameManager.getGameDirectory(), "game_archive.zip")
        if (archive.exists() && archive.length() > 0) {
            gameManager.installFromArchive(archive, object : GameManager.GameInstallCallback {
                override fun onProgress(progress: Int, message: String) {
                    runOnUiThread {
                        updateProgress(progress, message)
                    }
                }

                override fun onSuccess() {
                    runOnUiThread {
                        checkGameFiles()
                        Toast.makeText(
                            this@MainActivity,
                            "Installation completed!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        showError(message)
                        checkGameFiles()
                    }
                }
            })
        } else {
            showError("Downloaded file not found")
        }
    }

    private fun updateProgress(progress: Int, message: String) {
        runOnUiThread {
            with(binding) {
                progressBar.progress = progress
                progressText.text = message
                progressText.visibility = View.VISIBLE
            }
        }
    }

    private fun setLoading(show: Boolean, message: String? = null) {
        runOnUiThread {
            with(binding) {
                progressBar.visibility = if (show) View.VISIBLE else View.GONE
                progressText.visibility = if (show) View.VISIBLE else View.GONE
                playButton.isEnabled = !show
                message?.let { progressText.text = it }
            }
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            setLoading(false)
        }
    }

    private fun showServerSettingsDialog() {
        with(dialogBinding) {
            ipEditText.setText(serverIp)
            portEditText.setText(serverPort.toString())

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Server Settings")
                .setView(root)
                .setPositiveButton("Save") { _, _ ->
                    serverIp = ipEditText.text.toString()
                    serverPort = portEditText.text.toString().toIntOrNull() ?: 7777
                    Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(downloadReceiver, IntentFilter().apply {
            addAction("com.armzonrp.mobile.DOWNLOAD_PROGRESS")
            addAction("com.armzonrp.mobile.DOWNLOAD_COMPLETE")
        })
        checkGameFiles()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(downloadReceiver)
    }
}