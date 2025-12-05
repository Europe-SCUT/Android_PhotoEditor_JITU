package com.example.myapplication

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLImageRenderer : GLSurfaceView.Renderer {

    // 全屏两个三角形条带
    private val vertexData = floatArrayOf(
        -1f, -1f,   // 左下
        1f, -1f,   // 右下
        -1f,  1f,   // 左上
        1f,  1f    // 右上
    )

    // 纹理坐标（左上 (0,0) – 右下 (1,1)）
    private val texCoordData = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private val vertexBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexData)
                position(0)
            }

    private val texBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(texCoordData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoordData)
                position(0)
            }

    private var program = 0
    private var aPositionLocation = 0
    private var aTexCoordLocation = 0
    private var uMatrixLocation = 0
    private var uTextureLocation = 0

    private var textureId = 0

    private val projectionMatrix = FloatArray(16)

    @Volatile
    private var pendingBitmap: Bitmap? = null

    @Volatile
    private var textureNeedsUpdate = false

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0

    fun setBitmap(bitmap: Bitmap) {
        synchronized(this) {
            // 不在这里 recycle，交给上层控制生命周期更安全
            pendingBitmap = bitmap
            textureNeedsUpdate = true
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        val vertexShaderCode = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMatrix * aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        aPositionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMatrixLocation = GLES20.glGetUniformLocation(program, "uMatrix")
        uTextureLocation = GLES20.glGetUniformLocation(program, "uTexture")

        textureId = createTexture()
        Matrix.setIdentityM(projectionMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        surfaceWidth = width
        surfaceHeight = height
        updateMatrix()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (textureNeedsUpdate) {
            synchronized(this) {
                val bmp = pendingBitmap
                if (bmp != null && !bmp.isRecycled) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                    imageWidth = bmp.width
                    imageHeight = bmp.height
                    updateMatrix()
                }
                textureNeedsUpdate = false
            }
        }

        if (textureId == 0 || imageWidth == 0 || imageHeight == 0) return

        GLES20.glUseProgram(program)

        GLES20.glUniformMatrix4fv(uMatrixLocation, 1, false, projectionMatrix, 0)

        GLES20.glEnableVertexAttribArray(aPositionLocation)
        GLES20.glVertexAttribPointer(
            aPositionLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(aTexCoordLocation)
        GLES20.glVertexAttribPointer(
            aTexCoordLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            texBuffer
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureLocation, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLocation)
        GLES20.glDisableVertexAttribArray(aTexCoordLocation)
    }

    private fun updateMatrix() {
        if (surfaceWidth == 0 || surfaceHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            Matrix.setIdentityM(projectionMatrix, 0)
            return
        }

        val viewRatio = surfaceWidth.toFloat() / surfaceHeight
        val imageRatio = imageWidth.toFloat() / imageHeight

        Matrix.setIdentityM(projectionMatrix, 0)
        if (imageRatio > viewRatio) {
            // 图片更宽：宽铺满，高缩放
            val scaleY = viewRatio / imageRatio
            Matrix.scaleM(projectionMatrix, 0, 1f, scaleY, 1f)
        } else {
            // 图片更高：高铺满，宽缩放
            val scaleX = imageRatio / viewRatio
            Matrix.scaleM(projectionMatrix, 0, scaleX, 1f, 1f)
        }
    }

    private fun createTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        return tex[0]
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e("GLImageRenderer", "Shader compile error: $log")
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed")
        }
        return shader
    }
}
