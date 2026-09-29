package com.example.a_dex

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.a_dex.service.DexService
import com.example.a_dex.ui.theme.ADexTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber

data class AppInfo(val name: String, val packageName: String)

class MainActivity : ComponentActivity() {
    private var isStreaming by mutableStateOf(false)
    private var shizukuReady by mutableStateOf(false)
    private var installedApps by mutableStateOf(emptyList<AppInfo>())

    private val mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val serviceIntent = Intent(this, DexService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            isStreaming = true
            Toast.makeText(this, "Streaming Started", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestNotificationPermission()
        checkShizuku()
        loadApps()

        setContent {
            ADexTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                        Text("A-DeX Phone Host", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Shizuku Status: ${if (shizukuReady) "Ready" else "Not Ready (Required to launch apps)"}")
                        Text("Streaming Status: ${if (isStreaming) "Active" else "Stopped"}")
                        
                        Spacer(Modifier.height(16.dp))
                        
                        if (!isStreaming) {
                            Button(onClick = { startMediaProjection() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Start Streaming to TV")
                            }
                        } else {
                            Button(onClick = { stopStreaming() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Stop Streaming")
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Text("Installed Apps (Tap to launch on TV):", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        
                        LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                            items(installedApps) { app ->
                                Card(
                                    modifier = Modifier.padding(4.dp).clickable { launchAppOnTv(app.packageName) },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Text(
                                        text = app.name,
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun checkShizuku() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    shizukuReady = true
                } else {
                    Shizuku.requestPermission(0)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun startMediaProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun stopStreaming() {
        stopService(Intent(this, DexService::class.java))
        isStreaming = false
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = packageManager.queryIntentActivities(intent, 0)
        installedApps = apps.map {
            AppInfo(it.loadLabel(packageManager).toString(), it.activityInfo.packageName)
        }.sortedBy { it.name }
    }

    private fun launchAppOnTv(packageName: String) {
        val displayId = DexService.activeVirtualDisplayId
        if (displayId == -1) {
            Toast.makeText(this, "Start streaming first to create Virtual Display", Toast.LENGTH_SHORT).show()
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val component = launchIntent?.component?.flattenToShortString()

        lifecycleScope.launch(Dispatchers.IO) {
            var launched = false

            // Launch via Shizuku shell with explicit task flags & windowing mode
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                try {
                    val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                    method.isAccessible = true

                    val cmd = if (component != null) {
                        arrayOf("am", "start", "--display", displayId.toString(), "--windowingMode", "5", "-f", "0x18000000", "-n", component)
                    } else {
                        arrayOf("monkey", "--display", displayId.toString(), "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1")
                    }

                    val process = method.invoke(null, cmd, null, null) as Process
                    val exitCode = process.waitFor()
                    Timber.d("Shizuku am start exitCode: $exitCode")
                    if (exitCode == 0) {
                        launched = true
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Shizuku launch failed")
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (Shizuku.pingBinder()) {
                        Shizuku.requestPermission(0)
                        Toast.makeText(this@MainActivity, "Granting Shizuku permission required", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Shizuku service is not running!", Toast.LENGTH_LONG).show()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (launched) {
                    Toast.makeText(this@MainActivity, "Launched $packageName on TV (Display $displayId)", Toast.LENGTH_SHORT).show()
                } else if (shizukuReady) {
                    Toast.makeText(this@MainActivity, "Shizuku am start failed for $packageName", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
