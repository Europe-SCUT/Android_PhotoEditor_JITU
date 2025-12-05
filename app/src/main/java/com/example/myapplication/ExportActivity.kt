package com.example.myapplication

import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class ExportActivity : AppCompatActivity() {

    private lateinit var ivResult: ImageView
    private lateinit var btnSave: TextView
    private lateinit var btnContinue: TextView

    private var imagePath: String? = null  // 编辑后图片在 app 内部的路径

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)

        ivResult = findViewById(R.id.ivResult)
        btnSave = findViewById(R.id.btnSave)
        btnContinue = findViewById(R.id.btnContinue)

        imagePath = intent.getStringExtra("image_path")
        if (imagePath == null) {
            Toast.makeText(this, "未找到导出图片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 展示图片
        val file = File(imagePath!!)
        if (!file.exists()) {
            Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        ivResult.setImageBitmap(bitmap)

        // 进入导出页时，记录到历史列表
        HistoryManager.addHistory(this, file.absolutePath)

        btnSave.setOnClickListener {
            saveToGallery(file)
        }

        btnContinue.setOnClickListener {
            // 返回主页（从主页的历史记录里可以重新编辑）
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
    }

    private fun saveToGallery(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val resolver = contentResolver
        val fileName = "JiTu_${System.currentTimeMillis()}.jpg"
        val mimeType = "image/jpeg"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 及以上：使用 RELATIVE_PATH
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/JiTu"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val collection =
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri: Uri? = resolver.insert(collection, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(file).use { input ->
                            input.copyTo(out)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Android 9 及以下
                val picturesDir = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "JiTu")
                if (!targetDir.exists()) targetDir.mkdirs()
                val destFile = File(targetDir, fileName)

                FileInputStream(file).use { input ->
                    destFile.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, destFile.absolutePath)
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                }
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
