package com.example.myapplication

import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.ArrayDeque
import kotlin.math.min
import kotlin.math.roundToInt

class EditorActivity : AppCompatActivity() {

    private lateinit var canvasContainer: FrameLayout
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var glRenderer: GLImageRenderer
    private lateinit var cropOverlay: CropOverlayView

    private lateinit var btnBack: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnConfirm: ImageButton
    private lateinit var btnRotate: ImageButton

    private lateinit var layoutMainTools: LinearLayout
    private lateinit var layoutCropOptions: View

    private lateinit var btnToolCrop: View
    private lateinit var btnToolDoodle: View
    private lateinit var btnToolText: View
    private lateinit var btnToolMosaic: View

    private lateinit var btnCropFree: View
    private lateinit var btnCrop1_1: View
    private lateinit var btnCrop3_4: View
    private lateinit var btnCrop4_3: View
    private lateinit var btnCrop9_16: View
    private lateinit var btnCrop16_9: View
    private lateinit var btnCrop2_3: View
    private lateinit var btnCrop3_2: View

    private var imageUri: Uri? = null
    private var imagePath: String? = null

    // 当前编辑中的图
    private var currentBitmap: Bitmap? = null

    // 撤销 / 重做栈
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()

    // 裁剪模式
    private var inCropMode = false
    private var workBitmap: Bitmap? = null // 裁剪页使用的位图（可被旋转）

    // 当前裁剪比例（宽/高），null = 自由
    private var currentCropRatio: Float? = null
    private var currentCropLabel: String = "自由"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        bindViews()
        initGL()
        setupButtons()

        // 入口 1：历史记录 / 导出页回来的本地路径
        imagePath = intent.getStringExtra("image_path")
        if (imagePath != null) {
            loadBitmapFromPath(imagePath!!)
            return
        }

        // 入口 2：相册 / 预览传来的 Uri
        val uriStr = intent.getStringExtra("image_uri")
        imageUri = uriStr?.let { Uri.parse(it) }

        if (imageUri == null) {
            Toast.makeText(this, "没有收到图片地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadBitmapFromUri()
    }

    private fun bindViews() {
        canvasContainer = findViewById(R.id.canvasContainer)
        glSurfaceView = findViewById(R.id.glSurface)
        cropOverlay = findViewById(R.id.cropOverlay)

        btnBack = findViewById(R.id.btnBack)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnRotate = findViewById(R.id.btnRotate)

        layoutMainTools = findViewById(R.id.layoutMainTools)
        layoutCropOptions = findViewById(R.id.layoutCropOptions)

        btnToolCrop = findViewById(R.id.btnToolCrop)
        btnToolDoodle = findViewById(R.id.btnToolDoodle)
        btnToolText = findViewById(R.id.btnToolText)
        btnToolMosaic = findViewById(R.id.btnToolMosaic)

        btnCropFree = findViewById(R.id.btnCropFree)
        btnCrop1_1 = findViewById(R.id.btnCrop1_1)
        btnCrop3_4 = findViewById(R.id.btnCrop3_4)
        btnCrop4_3 = findViewById(R.id.btnCrop4_3)
        btnCrop9_16 = findViewById(R.id.btnCrop9_16)
        btnCrop16_9 = findViewById(R.id.btnCrop16_9)
        btnCrop2_3 = findViewById(R.id.btnCrop2_3)
        btnCrop3_2 = findViewById(R.id.btnCrop3_2)
    }

    private fun initGL() {
        glRenderer = GLImageRenderer()
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(glRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    private fun setupButtons() {
        btnBack.setOnClickListener {
            if (inCropMode) {
                exitCropMode()
            } else {
                finish()
            }
        }

        btnConfirm.setOnClickListener {
            if (inCropMode) {
                animateCropAndApply()
            } else {
                openExportPage()
            }
        }

        btnUndo.setOnClickListener { undo() }
        btnRedo.setOnClickListener { redo() }

        btnRotate.setOnClickListener { animateRotateInCropMode() }

        // 主工具栏
        btnToolCrop.setOnClickListener { enterCropMode() }
        btnToolDoodle.setOnClickListener {
            Toast.makeText(this, "涂鸦功能暂未实现", Toast.LENGTH_SHORT).show()
        }
        btnToolText.setOnClickListener {
            Toast.makeText(this, "文字功能暂未实现", Toast.LENGTH_SHORT).show()
        }
        btnToolMosaic.setOnClickListener {
            Toast.makeText(this, "马赛克功能暂未实现", Toast.LENGTH_SHORT).show()
        }

        // 裁剪比例按钮
        btnCropFree.setOnClickListener { selectCropRatio(null, "自由") }
        btnCrop1_1.setOnClickListener { selectCropRatio(1f, "1:1") }
        btnCrop3_4.setOnClickListener { selectCropRatio(3f / 4f, "3:4") }
        btnCrop4_3.setOnClickListener { selectCropRatio(4f / 3f, "4:3") }
        btnCrop9_16.setOnClickListener { selectCropRatio(9f / 16f, "9:16") }
        btnCrop16_9.setOnClickListener { selectCropRatio(16f / 9f, "16:9") }
        btnCrop2_3.setOnClickListener { selectCropRatio(2f / 3f, "2:3") }
        btnCrop3_2.setOnClickListener { selectCropRatio(3f / 2f, "3:2") }

        updateUndoRedoState()
    }

    // ------------------ 加载图片：路径 & Uri ------------------

    private fun loadBitmapFromPath(path: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(path)
            }
            if (bitmap == null) {
                Toast.makeText(this@EditorActivity, "加载图片失败", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            currentBitmap = bitmap
            glRenderer.setBitmap(bitmap)
            glSurfaceView.requestRender()
        }
    }

    private fun loadBitmapFromUri() {
        val uri = imageUri ?: return

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeSampledBitmapFromUri(uri, 2000, 2000)
            }

            if (bitmap == null) {
                Toast.makeText(this@EditorActivity, "加载图片失败", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            currentBitmap = bitmap
            glRenderer.setBitmap(bitmap)
            glSurfaceView.requestRender()
        }
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            var input: InputStream? = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input, null, options)
            input?.close()

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            input = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(input, null, options)
            input?.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (width, height) = options.outWidth to options.outHeight
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // ------------------ 编辑页 / 裁剪页 ------------------

    private fun enterCropMode() {
        val src = currentBitmap ?: return
        inCropMode = true

        layoutMainTools.visibility = View.GONE
        layoutCropOptions.visibility = View.VISIBLE
        btnRotate.visibility = View.VISIBLE
        cropOverlay.visibility = View.VISIBLE

        workBitmap = src
        glRenderer.setBitmap(src)
        glSurfaceView.requestRender()

        currentCropRatio = null
        currentCropLabel = "自由"

        canvasContainer.post {
            val bounds = calculateImageBounds(workBitmap!!)
            cropOverlay.setImageBounds(bounds)
            cropOverlay.setAspectRatio(currentCropRatio)
        }

        updateUndoRedoState()
    }

    private fun exitCropMode() {
        inCropMode = false

        layoutMainTools.visibility = View.VISIBLE
        layoutCropOptions.visibility = View.GONE
        btnRotate.visibility = View.GONE
        cropOverlay.visibility = View.GONE

        canvasContainer.scaleX = 1f
        canvasContainer.scaleY = 1f
        canvasContainer.translationX = 0f
        canvasContainer.translationY = 0f
        canvasContainer.rotation = 0f

        currentBitmap?.let {
            glRenderer.setBitmap(it)
            glSurfaceView.requestRender()
        }

        workBitmap = null
        updateUndoRedoState()
    }

    private fun selectCropRatio(ratio: Float?, label: String) {
        currentCropRatio = ratio
        currentCropLabel = label
        val bmp = workBitmap ?: return

        canvasContainer.post {
            val bounds = calculateImageBounds(bmp)
            cropOverlay.setImageBounds(bounds)
            cropOverlay.setAspectRatio(ratio)
        }
    }

    private fun calculateImageBounds(bitmap: Bitmap): RectF {
        val vw = canvasContainer.width.toFloat()
        val vh = canvasContainer.height.toFloat()
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()

        val scale = min(vw / bw, vh / bh)
        val dw = bw * scale
        val dh = bh * scale

        val left = (vw - dw) / 2f
        val top = (vh - dh) / 2f

        return RectF(left, top, left + dw, top + dh)
    }

    // ------------------ 旋转动画 ------------------

    private fun animateRotateInCropMode() {
        if (!inCropMode) return
        val src = workBitmap ?: return

        canvasContainer.animate()
            .rotationBy(90f)
            .setDuration(250)
            .withEndAction {
                val matrix = Matrix()
                matrix.postRotate(90f)
                val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
                workBitmap = rotated

                glRenderer.setBitmap(rotated)
                glSurfaceView.requestRender()

                canvasContainer.rotation = 0f

                canvasContainer.post {
                    val bounds = calculateImageBounds(rotated)
                    cropOverlay.setImageBounds(bounds)
                    cropOverlay.setAspectRatio(currentCropRatio)
                }
            }
            .start()
    }

    // ------------------ 裁剪确认动画 ------------------

    private fun animateCropAndApply() {
        val base = workBitmap ?: return
        val rel = cropOverlay.getRelativeCropRect() ?: return
        val rectOnView = cropOverlay.getAbsoluteCropRect() ?: return

        // 先做真正的裁剪
        val result = performCrop(base, rel)
        if (result == null) {
            Toast.makeText(this, "裁剪区域太小", Toast.LENGTH_SHORT).show()
            return
        }
        val (cropped, old) = result
        if (old != null) {
            undoStack.addLast(old)
            redoStack.clear()
        }
        currentBitmap = cropped
        glRenderer.setBitmap(cropped)
        glSurfaceView.requestRender()

        cropOverlay.visibility = View.GONE

        canvasContainer.post {
            val container = canvasContainer
            val cw = container.width.toFloat()
            val ch = container.height.toFloat()

            if (cw == 0f || ch == 0f) {
                exitCropMode()
                return@post
            }

            val startScale = min(
                rectOnView.width() / cw,
                rectOnView.height() / ch
            ).coerceAtMost(1f)

            val startTx = rectOnView.centerX() - cw / 2f
            val startTy = rectOnView.centerY() - ch / 2f

            container.scaleX = startScale
            container.scaleY = startScale
            container.translationX = startTx
            container.translationY = startTy

            container.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(260)
                .withEndAction {
                    Toast.makeText(this, "已应用裁剪：$currentCropLabel", Toast.LENGTH_SHORT).show()
                    exitCropMode()
                }
                .start()
        }
    }

    private fun performCrop(base: Bitmap, rel: RectF): Pair<Bitmap, Bitmap?>? {
        val leftPx = (rel.left * base.width).roundToInt().coerceIn(0, base.width - 1)
        val topPx = (rel.top * base.height).roundToInt().coerceIn(0, base.height - 1)
        val rightPx = (rel.right * base.width).roundToInt().coerceIn(leftPx + 1, base.width)
        val bottomPx = (rel.bottom * base.height).roundToInt().coerceIn(topPx + 1, base.height)

        val width = rightPx - leftPx
        val height = bottomPx - topPx

        if (width <= 0 || height <= 0) return null

        val old = currentBitmap
        val cropped = Bitmap.createBitmap(base, leftPx, topPx, width, height)
        return cropped to old
    }

    // ------------------ 导出页 ------------------

    private fun openExportPage() {
        val bmp = currentBitmap
        if (bmp == null || bmp.isRecycled) {
            Toast.makeText(this, "当前没有可导出的图片", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // 在 IO 线程做文件写入
                val file = withContext(Dispatchers.IO) {
                    // 优先用 app 专属的外部图片目录（不会撞系统权限）
                    val dir = getExternalFilesDir("exports") ?: filesDir
                    val historyDir = File(dir, "history")
                    if (!historyDir.exists()) historyDir.mkdirs()

                    val outFile = File(historyDir, "EDIT_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(outFile).use { out ->
                        // 防止有些 Bitmap 配置奇怪，统一压成 ARGB_8888 再写
                        val safeBitmap = if (bmp.config != Bitmap.Config.ARGB_8888) {
                            bmp.copy(Bitmap.Config.ARGB_8888, false)
                        } else {
                            bmp
                        }
                        safeBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    outFile
                }

                if (!file.exists()) {
                    Toast.makeText(this@EditorActivity, "保存临时图片失败（文件未生成）", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val intent = Intent(this@EditorActivity, ExportActivity::class.java)
                intent.putExtra("image_path", file.absolutePath)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@EditorActivity,
                    "保存临时图片失败: ${e.message ?: "未知错误"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    // ------------------ 撤销 / 重做 ------------------

    private fun undo() {
        if (inCropMode) return
        if (undoStack.isEmpty()) return

        val curr = currentBitmap
        val prev = undoStack.removeLast()
        if (curr != null) {
            redoStack.addLast(curr)
        }
        currentBitmap = prev
        currentBitmap?.let {
            glRenderer.setBitmap(it)
            glSurfaceView.requestRender()
        }
        updateUndoRedoState()
    }

    private fun redo() {
        if (inCropMode) return
        if (redoStack.isEmpty()) return

        val curr = currentBitmap
        val next = redoStack.removeLast()
        if (curr != null) {
            undoStack.addLast(curr)
        }
        currentBitmap = next
        currentBitmap?.let {
            glRenderer.setBitmap(it)
            glSurfaceView.requestRender()
        }
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        val canUndo = undoStack.isNotEmpty() && !inCropMode
        val canRedo = redoStack.isNotEmpty() && !inCropMode

        btnUndo.isEnabled = canUndo
        btnRedo.isEnabled = canRedo

        btnUndo.setColorFilter(
            ContextCompat.getColor(
                this,
                if (canUndo) android.R.color.black else android.R.color.darker_gray
            )
        )
        btnRedo.setColorFilter(
            ContextCompat.getColor(
                this,
                if (canRedo) android.R.color.black else android.R.color.darker_gray
            )
        )
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        glSurfaceView.onPause()
        super.onPause()
    }
}
