package com.example.myapplication

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnCamera: LinearLayout
    private lateinit var btnAlbum: LinearLayout
    private lateinit var rvHistory: RecyclerView

    companion object {
        private const val REQ_TAKE_PHOTO = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCamera = findViewById(R.id.btnCamera)
        btnAlbum = findViewById(R.id.btnAlbum)
        rvHistory = findViewById(R.id.rvHistory)

        rvHistory.layoutManager = GridLayoutManager(this, 3)

        // 相机按钮
        btnCamera.setOnClickListener {
            dispatchTakePicture()
        }

        // 相册按钮
        btnAlbum.setOnClickListener {
            val intent = Intent(this, AlbumActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    /**
     * 打开系统相机（旧 API 版，稳定优先）
     */
    private fun dispatchTakePicture() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val resolved = intent.resolveActivity(packageManager)
        if (resolved == null) {
            Toast.makeText(this, "当前系统不支持标准相机调用，请使用相册导入图片", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            @Suppress("DEPRECATION")
            run {
                startActivityForResult(intent, REQ_TAKE_PHOTO)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "打开相机失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 接收相机返回的缩略图，并保存到内部存储，再进入编辑页
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_TAKE_PHOTO && resultCode == RESULT_OK) {
            val bmp = data?.extras?.get("data") as? Bitmap
            if (bmp == null) {
                Toast.makeText(this, "拍照失败：未返回图片数据", Toast.LENGTH_SHORT).show()
                return
            }

            try {
                // 保存到 app 内部目录
                val dir = File(filesDir, "camera")
                if (!dir.exists()) dir.mkdirs()

                val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                val file = File(dir, "CAM_$name.jpg")

                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // 进入编辑页
                val intent = Intent(this, EditorActivity::class.java)
                intent.putExtra("image_path", file.absolutePath)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "保存拍照图片失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 历史记录：从内部保存的导出图片中加载
     */
    private fun loadHistory() {
        val list = HistoryManager.getHistory(this)
        val adapter = HistoryAdapter(list) { path ->
            val intent = Intent(this, EditorActivity::class.java)
            intent.putExtra("image_path", path)
            startActivity(intent)
        }
        rvHistory.adapter = adapter
    }
}
