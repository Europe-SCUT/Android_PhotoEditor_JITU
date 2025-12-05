package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // 图片在 View 中实际显示的区域（fitCenter 后的结果）
    private var imageBounds: RectF? = null

    // 当前裁剪框（View 坐标系）
    private var cropRect: RectF? = null

    // 当前比例（宽/高），null = 自由
    private var aspectRatio: Float? = null

    // 拖动 / 缩放
    private var isDragging = false
    private var isResizing = false
    private var lastX = 0f
    private var lastY = 0f

    // 右下角触发缩放的区域大小
    private val resizeTouchSize = 60f
    private val minCropSize = 120f

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#66000000") // 外部半透明黑
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 主边框
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // 九宫格线
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // 四角线（更粗）
    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    fun setImageBounds(bounds: RectF) {
        imageBounds = RectF(bounds)
        if (cropRect == null) {
            cropRect = RectF(bounds)
        }
        invalidate()
    }

    fun setCropRect(rect: RectF) {
        cropRect = RectF(rect)
        invalidate()
    }

    /**
     * 设置裁剪比例 ratio = 宽/高，null = 自由（占满 imageBounds）
     */
    fun setAspectRatio(ratio: Float?) {
        aspectRatio = ratio
        val bounds = imageBounds ?: return

        cropRect = if (ratio == null) {
            RectF(bounds)
        } else {
            val bw = bounds.width()
            val bh = bounds.height()
            val boundsRatio = bw / bh

            if (boundsRatio > ratio) {
                // 更宽，裁左右
                val targetWidth = bh * ratio
                val left = bounds.centerX() - targetWidth / 2f
                RectF(left, bounds.top, left + targetWidth, bounds.bottom)
            } else {
                // 更高，裁上下
                val targetHeight = bw / ratio
                val top = bounds.centerY() - targetHeight / 2f
                RectF(bounds.left, top, bounds.right, top + targetHeight)
            }
        }

        invalidate()
    }

    /**
     * 获取裁剪框在图片区域中的相对坐标（0~1）
     */
    fun getRelativeCropRect(): RectF? {
        val bounds = imageBounds ?: return null
        val rect = cropRect ?: return null

        val left = (rect.left - bounds.left) / bounds.width()
        val top = (rect.top - bounds.top) / bounds.height()
        val right = (rect.right - bounds.left) / bounds.width()
        val bottom = (rect.bottom - bounds.top) / bounds.height()

        return RectF(
            left.coerceIn(0f, 1f),
            top.coerceIn(0f, 1f),
            right.coerceIn(0f, 1f),
            bottom.coerceIn(0f, 1f)
        )
    }

    /**
     * 获取当前裁剪框在 View 坐标系的绝对位置（用于做动画）
     */
    fun getAbsoluteCropRect(): RectF? {
        val rect = cropRect ?: return null
        return RectF(rect)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rect = cropRect ?: return

        // 画四周遮罩
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, dimPaint)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, dimPaint)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, dimPaint)

        // 主边框
        canvas.drawRect(rect, borderPaint)

        // 九宫格：2 竖 + 2 横
        val oneThirdW = rect.width() / 3f
        val oneThirdH = rect.height() / 3f

        // 竖线
        canvas.drawLine(rect.left + oneThirdW, rect.top, rect.left + oneThirdW, rect.bottom, gridPaint)
        canvas.drawLine(rect.left + 2 * oneThirdW, rect.top, rect.left + 2 * oneThirdW, rect.bottom, gridPaint)

        // 横线
        canvas.drawLine(rect.left, rect.top + oneThirdH, rect.right, rect.top + oneThirdH, gridPaint)
        canvas.drawLine(rect.left, rect.top + 2 * oneThirdH, rect.right, rect.top + 2 * oneThirdH, gridPaint)

        // 四角粗线
        val cornerLen = min(rect.width(), rect.height()) * 0.12f

        // 左上
        canvas.drawLine(rect.left, rect.top, rect.left + cornerLen, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLen, cornerPaint)

        // 右上
        canvas.drawLine(rect.right - cornerLen, rect.top, rect.right, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLen, cornerPaint)

        // 左下
        canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLen, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom - cornerLen, rect.left, rect.bottom, cornerPaint)

        // 右下
        canvas.drawLine(rect.right - cornerLen, rect.bottom, rect.right, rect.bottom, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom - cornerLen, rect.right, rect.bottom, cornerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rect = cropRect ?: return false
        val bounds = imageBounds ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isInResizeHandle(event.x, event.y, rect)) {
                    isResizing = true
                    lastX = event.x
                    lastY = event.y
                    return true
                } else if (rect.contains(event.x, event.y)) {
                    isDragging = true
                    lastX = event.x
                    lastY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    moveRect(rect, bounds, dx, dy)
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                    return true
                } else if (isResizing) {
                    resizeRect(rect, bounds, event.x, event.y)
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isResizing = false
            }
        }

        return super.onTouchEvent(event)
    }

    private fun isInResizeHandle(x: Float, y: Float, rect: RectF): Boolean {
        return (x >= rect.right - resizeTouchSize && x <= rect.right + resizeTouchSize &&
                y >= rect.bottom - resizeTouchSize && y <= rect.bottom + resizeTouchSize)
    }

    private fun moveRect(rect: RectF, bounds: RectF, dx: Float, dy: Float) {
        var newLeft = rect.left + dx
        var newTop = rect.top + dy
        var newRight = rect.right + dx
        var newBottom = rect.bottom + dy

        val w = rect.width()
        val h = rect.height()

        if (newLeft < bounds.left) {
            newLeft = bounds.left
            newRight = newLeft + w
        }
        if (newRight > bounds.right) {
            newRight = bounds.right
            newLeft = newRight - w
        }
        if (newTop < bounds.top) {
            newTop = bounds.top
            newBottom = newTop + h
        }
        if (newBottom > bounds.bottom) {
            newBottom = bounds.bottom
            newTop = newBottom - h
        }

        rect.set(newLeft, newTop, newRight, newBottom)
    }

    private fun resizeRect(rect: RectF, bounds: RectF, touchX: Float, touchY: Float) {
        val ar = aspectRatio
        val left = rect.left
        val top = rect.top

        var newRight = touchX.coerceIn(left + minCropSize, bounds.right)
        var newBottom = touchY.coerceIn(top + minCropSize, bounds.bottom)

        if (ar != null) {
            // 保持比例：宽/高 = ar
            var width = newRight - left
            var height = width / ar

            if (top + height > bounds.bottom) {
                height = bounds.bottom - top
                width = height * ar
            }
            if (left + width > bounds.right) {
                width = bounds.right - left
                height = width / ar
            }

            if (width < minCropSize || height < minCropSize) return

            newRight = left + width
            newBottom = top + height
        } else {
            // 自由比例
            if (newRight - left < minCropSize || newBottom - top < minCropSize) return
        }

        rect.set(left, top, newRight, newBottom)
    }
}
