package com.example.a_dex.service

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.a_dex.DesktopPresentation
import com.example.a_dex.network.InputServer
import com.example.a_dex.network.NsdBroadcaster
import com.example.a_dex.network.VideoStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

class DexService : Service() {
    private val nsdBroadcaster: NsdBroadcaster by inject()
    private val videoStreamer: VideoStreamer by inject()
    private val inputServer: InputServer by inject()
    
    @Volatile
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var desktopPresentation: DesktopPresentation? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    @Volatile
    private var isCreatingVirtualDisplay = false

    companion object {
        var activeVirtualDisplayId: Int = -1
    }

    override fun onCreate() {
        super.onCreate()
        // MUST start foreground in onCreate() for Android 14+ FGS
        startForegroundNotification()

        nsdBroadcaster.startAdvertising()

        serviceScope.launch {
            videoStreamer.startServer()
        }
        serviceScope.launch {
            inputServer.startServer()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("DATA")
        }
        
        startForegroundNotification()

        if (resultCode != 0 && data != null && mediaProjection == null) {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(resultCode, data)
            if (projection != null) {
                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        Timber.d("MediaProjection stopped")
                        virtualDisplay?.release()
                        virtualDisplay = null
                        activeVirtualDisplayId = -1
                    }
                }, Handler(Looper.getMainLooper()))
                
                mediaProjection = projection
                serviceScope.launch { setupVirtualDisplay() }
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("DEX_STREAM", "Desktop Stream", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, "DEX_STREAM")
            .setContentTitle("Custom DeX Streaming")
            .setContentText("Streaming desktop to TV")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } catch (e: Exception) {
                Timber.e(e, "Error starting foreground service with mediaProjection type")
                startForeground(1, notification)
            }
        } else {
            startForeground(1, notification)
        }
    }
    
    private suspend fun setupVirtualDisplay() {
        synchronized(this) {
            if (virtualDisplay != null || isCreatingVirtualDisplay) return
            isCreatingVirtualDisplay = true
        }

        try {
            var retries = 0
            while ((videoStreamer.inputSurface == null || mediaProjection == null) && retries < 50) {
                delay(100)
                retries++
            }

            val surface = videoStreamer.inputSurface
            val projection = mediaProjection

            if (surface != null && projection != null) {
                Timber.d("Creating VirtualDisplay now...")
                val vd = projection.createVirtualDisplay(
                    "DeX_Virtual_Display",
                    1920, 1080, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                    surface, null, null
                )
                virtualDisplay = vd

                var displayId = -1
                for (i in 0..20) {
                    val d = vd?.display
                    if (d != null && d.displayId != -1) {
                        displayId = d.displayId
                        break
                    }
                    delay(100)
                }

                activeVirtualDisplayId = displayId
                inputServer.virtualDisplayId = displayId
                Timber.d("Virtual Display Created successfully: ID $activeVirtualDisplayId")

                val d = vd?.display
                if (d != null && displayId != -1) {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            desktopPresentation?.dismiss()

                            // Pass displayContext to Presentation so window type matches Presentation's internal layout params seamlessly
                            val displayContext = createDisplayContext(d)

                            val presentation = DesktopPresentation(displayContext, d, displayId)
                            presentation.show()
                            desktopPresentation = presentation
                            Timber.d("Liquid Glass DeX Presentation shown successfully on display $displayId")
                        } catch (e: Exception) {
                            Timber.e("Failed to show DesktopPresentation on display $displayId: ${e.toString()}\n${e.stackTraceToString()}")
                        }
                    }
                }
            } else {
                Timber.e("Could not create Virtual Display: surface=$surface, projection=$projection")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create VirtualDisplay")
        } finally {
            isCreatingVirtualDisplay = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdBroadcaster.stopAdvertising()
        videoStreamer.stopServer()
        inputServer.stopServer()
        Handler(Looper.getMainLooper()).post {
            try {
                desktopPresentation?.dismiss()
            } catch (e: Exception) {}
            desktopPresentation = null
        }
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        activeVirtualDisplayId = -1
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
