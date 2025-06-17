package com.example.camerabandit

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.camerabandit.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    // --- Variables globales ---
    private lateinit var photoUri: Uri
    private lateinit var videoUri: Uri

    private lateinit var photoLauncher: ActivityResultLauncher<Uri>
    private lateinit var videoLauncher: ActivityResultLauncher<Intent>

    // --- onCreate ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPhoto = findViewById<Button>(R.id.btnPhoto)
        val btnVideo = findViewById<Button>(R.id.btnVideo)

        if (!hasPermissions()) {
            requestPermissions()
        }

        // Initialisation des launchers
        photoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                Toast.makeText(this, "Photo enregistrée dans la galerie", Toast.LENGTH_SHORT).show()
            }
        }

        videoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Vidéo enregistrée", Toast.LENGTH_SHORT).show()
            }
        }

        btnPhoto.setOnClickListener {
            photoUri = createMediaFileUri("IMG_", ".jpg")
            photoLauncher.launch(photoUri)
        }

        btnVideo.setOnClickListener {
            val videoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            videoUri = createMediaFileUri("VID_", ".mp4")
            videoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
            videoLauncher.launch(videoIntent)
        }
    }

    // --- Fonction pour créer un fichier media ---
    private fun createMediaFileUri(prefix: String, suffix: String): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, prefix + System.currentTimeMillis())
            put(MediaStore.MediaColumns.MIME_TYPE, if (suffix == ".jpg") "image/jpeg" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraGalleryApp")
        }

        val collection = if (suffix == ".jpg") {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        return contentResolver.insert(collection, contentValues)!!
    }

    // --- Vérifie les permissions ---
    private fun hasPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Demande les permissions ---
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, permissions, 100)
    }

}
