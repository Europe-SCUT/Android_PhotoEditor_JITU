package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class CameraPreviewActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var btnBack: ImageButton
    private lateinit var btnConfirm: ImageButton

    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)

        imagePreview = findViewById(R.id.imagePreview)
        btnBack = findViewById(R.id.btnBack)
        btnConfirm = findViewById(R.id.btnConfirm)

        val uriString = intent.getStringExtra("image_uri")
        imageUri = uriString?.let { Uri.parse(it) }

        if (imageUri != null) {
            Glide.with(this)
                .load(imageUri)
                .into(imagePreview)
        }

        btnBack.setOnClickListener {
            // 返回主页面（相机/相册入口）
            finish()
        }

        btnConfirm.setOnClickListener {
            imageUri?.let { uri ->
                val intent = Intent(this, EditorActivity::class.java).apply {
                    putExtra("image_uri", uri.toString())
                }
                startActivity(intent)
            }
        }
    }
}
