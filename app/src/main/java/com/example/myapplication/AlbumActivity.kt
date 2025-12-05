package com.example.myapplication

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumActivity : AppCompatActivity() {

    private lateinit var recyclerMedia: RecyclerView
    private lateinit var previewContainer: FrameLayout
    private lateinit var imagePreview: ImageView
    private lateinit var videoPreview: VideoView
    private lateinit var btnBack: ImageButton
    private lateinit var btnConfirm: ImageButton

    private val mediaAdapter = MediaAdapter { item -> onMediaClicked(item) }

    private var currentMode: UiMode = UiMode.GRID
    private var selectedItem: MediaItem? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.any { it }
            if (granted) {
                loadMedia()
            } else {
                showPermissionDeniedDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album)

        recyclerMedia = findViewById(R.id.recyclerMedia)
        previewContainer = findViewById(R.id.previewContainer)
        imagePreview = findViewById(R.id.imagePreview)
        videoPreview = findViewById(R.id.videoPreview)
        btnBack = findViewById(R.id.btnBack)
        btnConfirm = findViewById(R.id.btnConfirm)

        setupRecyclerView()
        setupTopBar()

        if (hasMediaPermission()) {
            loadMedia()
        } else {
            requestMediaPermission()
        }
    }

    private fun setupRecyclerView() {
        recyclerMedia.apply {
            layoutManager = GridLayoutManager(this@AlbumActivity, 3)
            adapter = mediaAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupTopBar() {
        btnBack.setOnClickListener {
            when (currentMode) {
                UiMode.GRID -> finish()
                UiMode.PREVIEW_IMAGE, UiMode.PREVIEW_VIDEO -> switchToGrid()
            }
        }

        btnConfirm.setOnClickListener {
            if (currentMode == UiMode.PREVIEW_IMAGE && selectedItem != null) {
                val uri = selectedItem!!.uri.toString()
                val intent = Intent(this, EditorActivity::class.java)
                intent.putExtra("image_uri", uri)
                startActivity(intent)
            }
        }
        updateConfirmButton()
    }

    // ---------- 权限部分 ----------

    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val img = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val vid = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            img || vid
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("权限被拒绝")
            .setMessage("即图需要访问相册权限才能显示图片/视频")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 加载媒体部分 ----------

    private fun loadMedia() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                queryAllMedia()
            }

            Toast.makeText(
                this@AlbumActivity,
                "找到 ${items.size} 个媒体文件",
                Toast.LENGTH_SHORT
            ).show()

            mediaAdapter.submitList(items)

            if (items.isEmpty()) {
                Toast.makeText(
                    this@AlbumActivity,
                    "没有读取到图片/视频，可能是权限或存储位置问题",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 从 MediaStore 读取所有图片 + 视频，按时间倒序
    private fun queryAllMedia(): List<MediaItem> {
        val result = mutableListOf<MediaItem>()

        // --- 图片 ---
        val imgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imgProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        contentResolver.query(
            imgUri,
            imgProjection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            var count = 0
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(imgUri, id)
                result.add(MediaItem(uri, false, 0L, date))
                count++
                // 为了性能，如果照片特别多，你可以先限制前 N 条：
                if (count >= 2000) break
            }
        }

        // --- 视频 ---
        val vidUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION
        )

        contentResolver.query(
            vidUri,
            vidProjection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            var count = 0
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol)
                val dur = cursor.getLong(durCol)
                val uri = ContentUris.withAppendedId(vidUri, id)
                result.add(MediaItem(uri, true, dur, date))
                count++
                if (count >= 500) break
            }
        }

        // 按时间倒序
        return result.sortedByDescending { it.dateAdded }
    }

    // ---------- UI 切换部分 ----------

    private fun onMediaClicked(item: MediaItem) {
        selectedItem = item
        if (item.isVideo) {
            switchToVideo(item.uri)
        } else {
            switchToImage(item.uri)
        }
    }

    private fun switchToImage(uri: Uri) {
        currentMode = UiMode.PREVIEW_IMAGE
        recyclerMedia.visibility = View.GONE
        previewContainer.visibility = View.VISIBLE
        videoPreview.visibility = View.GONE
        imagePreview.visibility = View.VISIBLE

        Glide.with(this)
            .load(uri)
            .into(imagePreview)

        updateConfirmButton()
    }

    private fun switchToVideo(uri: Uri) {
        currentMode = UiMode.PREVIEW_VIDEO
        recyclerMedia.visibility = View.GONE
        previewContainer.visibility = View.VISIBLE
        imagePreview.visibility = View.GONE
        videoPreview.visibility = View.VISIBLE

        videoPreview.setVideoURI(uri)
        videoPreview.setOnPreparedListener { it.isLooping = true }
        videoPreview.start()

        updateConfirmButton()
    }

    private fun switchToGrid() {
        currentMode = UiMode.GRID
        recyclerMedia.visibility = View.VISIBLE
        previewContainer.visibility = View.GONE
        imagePreview.visibility = View.GONE
        videoPreview.visibility = View.GONE
        if (videoPreview.isPlaying) {
            videoPreview.stopPlayback()
        }
        selectedItem = null
        updateConfirmButton()
    }

    private fun updateConfirmButton() {
        val enabled = currentMode == UiMode.PREVIEW_IMAGE && selectedItem != null
        btnConfirm.isEnabled = enabled

        val color = if (enabled) {
            ContextCompat.getColor(this, R.color.ic_launcher_background) // 绿
        } else {
            ContextCompat.getColor(this, android.R.color.darker_gray) // 灰
        }
        btnConfirm.setColorFilter(color)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (videoPreview.isPlaying) {
            videoPreview.stopPlayback()
        }
    }
}

class MediaAdapter(
    private val onClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    private val items = mutableListOf<MediaItem>()

    fun submitList(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_thumbnail, parent, false)
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View, val onClick: (MediaItem) -> Unit) :
        RecyclerView.ViewHolder(itemView) {

        private val imgThumb: ImageView = itemView.findViewById(R.id.imgThumb)
        private val imgVideoBadge: ImageView = itemView.findViewById(R.id.imgVideoBadge)

        fun bind(item: MediaItem) {
            Glide.with(itemView.context)
                .load(item.uri)
                .centerCrop()
                .into(imgThumb)

            imgVideoBadge.visibility = if (item.isVideo) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onClick(item)
            }
        }
    }
}


enum class UiMode {
    GRID,
    PREVIEW_IMAGE,
    PREVIEW_VIDEO
}

data class MediaItem(
    val uri: Uri,
    val isVideo: Boolean,
    val durationMs: Long,
    val dateAdded: Long
)
