package com.example.camerabandit

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class VideoRecordService : LifecycleService() {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        const val CHANNEL_ID = "VideoRecordChannel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor() as ExecutorService
        createNotificationChannel()
        acquireWakeLock()
    }

    @SuppressLint("MissingSuperCall")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_START -> {
                    if (hasRequiredPermissions()) {
                        startForeground(NOTIFICATION_ID, createNotification())
                        startRecording()
                    } else {
                        Log.e("VideoRecordService", "Permissions manquantes")
                        stopSelf()
                    }
                }
                ACTION_STOP -> {
                    stopRecording()
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Enregistrement vidéo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Enregistrement vidéo en cours"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Enregistrement en cours")
            .setContentText("L'application enregistre une vidéo")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CameraBandit::VideoWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L /*10 minutes*/)
        }
    }

    private fun startRecording() {
        if (recording != null || !hasRequiredPermissions()) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                // Ne pas afficher la preview dans le service
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture
                )

                val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
                    .format(System.currentTimeMillis())
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.mp4")
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/CameraBandit")
                    }
                }

                val outputOptions = MediaStoreOutputOptions.Builder(
                    contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(contentValues).build()

                recording = videoCapture?.output
                    ?.prepareRecording(this, outputOptions)
                    ?.withAudioEnabled()
                    ?.start(ContextCompat.getMainExecutor(this)) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                Log.d("VideoRecordService", "Enregistrement démarré")
                            }
                            is VideoRecordEvent.Finalize -> {
                                if (!event.hasError()) {
                                    Log.d("VideoRecordService", "Enregistrement terminé: ${event.outputResults.outputUri}")
                                } else {
                                    Log.e("VideoRecordService", "Erreur enregistrement: ${event.error}")
                                }
                                recording = null
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("VideoRecordService", "Erreur démarrage enregistrement", e)
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopRecording() {
        try {
            recording?.stop()
        } catch (e: Exception) {
            Log.e("VideoRecordService", "Erreur arrêt enregistrement", e)
        }
        recording = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        cameraExecutor.shutdown()
    }

    @SuppressLint("MissingSuperCall")
    override fun onBind(intent: Intent): IBinder? = null
}