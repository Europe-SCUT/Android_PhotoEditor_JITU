package com.example.myapplication

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class ShimmerTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var gradient: LinearGradient? = null
    private val gradientMatrix = Matrix()
    private var gradientOffset = 0f
    private var animator: ValueAnimator? = null
    private var viewWidth = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0) return
        viewWidth = w

        // 从左到右的线性渐变，中间一条亮带，两边稍暗
        gradient = LinearGradient(
            -viewWidth.toFloat(), 0f,
            0f, 0f,
            intArrayOf(
                currentTextColor,         // 左：正常色
                0xFFFFFFFF.toInt(),       // 中间：高亮白
                currentTextColor          // 右：正常色
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        paint.shader = gradient
        startAnim()
    }

    private fun startAnim() {
        if (animator != null) return
        if (viewWidth == 0) return

        animator = ValueAnimator.ofFloat(0f, 2f * viewWidth).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { valueAnimator ->
                gradientOffset = valueAnimator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnim() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        val g = gradient
        if (g != null) {
            gradientMatrix.setTranslate(gradientOffset, 0f)
            g.setLocalMatrix(gradientMatrix)
            paint.shader = g
        }
        super.onDraw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewWidth > 0 && animator == null) {
            startAnim()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnim()
        super.onDetachedFromWindow()
    }
}
