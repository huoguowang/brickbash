package com.dsh.brickbash

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View

/**
 * 只负责三件事：画出来、收触摸、跑循环。
 * 游戏规则全在 GameWorld 里。
 */
class GameView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val world = GameWorld(d)

    private val prefs = context.getSharedPreferences("brickbash", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best", 0)

    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // ---- 画笔
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brickHi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 255, 255, 255) }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4CC9F0.toInt() }
    private val paddleGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 76, 201, 240) }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8FAFC.toInt() }
    private val ballGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 248, 250, 252) }
    private val overlayPaint = Paint().apply { color = Color.argb(220, 6, 10, 20) }

    private val hudLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textSize = 12f * d
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val hudValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f * d
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val hudDim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF64748B.toInt()
        textSize = 13f * d
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val bigText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF4D6D.toInt()
        textSize = 38f * d
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val midText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCBD5E1.toInt()
        textSize = 16f * d
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()

    // ---- 球的拖尾
    private val trailX = FloatArray(8)
    private val trailY = FloatArray(8)

    // ---- 主循环
    private var running = false
    private var lastFrame = 0L
    private val ticker = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastFrame == 0L) {
                lastFrame = frameTimeNanos
            } else {
                var dt = (frameTimeNanos - lastFrame) / 1_000_000_000f
                lastFrame = frameTimeNanos
                if (dt > 0.05f) dt = 0.05f
                if (dt > 0f) {
                    world.update(dt)
                    if (world.phase == Phase.PLAYING) pushTrail()
                }
            }
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        isFocusable = true
        setBackgroundColor(Color.BLACK)
        world.onEvent = { event -> onGameEvent(event) }
    }

    // ------------------------------------------------------------ 生命周期

    fun resume() {
        if (running) return
        running = true
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(ticker)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(ticker)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF0A0F1E.toInt(), 0xFF151E38.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        world.resize(w.toFloat(), h.toFloat())
        resetTrail()
    }

    // ------------------------------------------------------------ 输入

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (world.phase == Phase.GAME_OVER) {
                    world.newGame()
                    resetTrail()
                } else {
                    world.movePaddle(event.x)
                    if (world.phase == Phase.READY) world.launch()
                }
                pushTrail()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                world.movePaddle(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    // ------------------------------------------------------------ 绘制

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 砖块
        val corner = 4f * d
        for (b in world.bricks) {
            brickPaint.color = b.color
            rect.set(b.left, b.top, b.right, b.bottom)
            canvas.drawRoundRect(rect, corner, corner, brickPaint)
            rect.set(b.left + d, b.top + d, b.right - d, b.top + 5f * d)
            canvas.drawRoundRect(rect, 2f * d, 2f * d, brickHi)
        }

        // 破碎粒子
        for (p in world.particles) {
            val fade = (p.life / p.maxLife).coerceIn(0f, 1f)
            particlePaint.color = p.color
            particlePaint.alpha = (255 * fade).toInt()
            canvas.drawCircle(p.x, p.y, 3.2f * d * (0.5f + fade * 0.5f), particlePaint)
        }

        // 挡板
        rect.set(
            world.paddleLeft - 4f * d, world.paddleY - 3f * d,
            world.paddleRight + 4f * d, world.paddleY + world.paddleH + 5f * d
        )
        canvas.drawRoundRect(rect, 12f * d, 12f * d, paddleGlow)
        rect.set(world.paddleLeft, world.paddleY, world.paddleRight, world.paddleY + world.paddleH)
        canvas.drawRoundRect(rect, 7f * d, 7f * d, paddlePaint)

        // 拖尾
        for (i in trailX.indices) {
            val a = 120 - i * 15
            if (a <= 0) continue
            ballGlow.alpha = a
            canvas.drawCircle(trailX[i], trailY[i], world.ballR * (1f - i * 0.08f), ballGlow)
        }
        ballGlow.alpha = 70

        // 球
        canvas.drawCircle(world.ballX, world.ballY, world.ballR * 2.1f, ballGlow)
        canvas.drawCircle(world.ballX, world.ballY, world.ballR, ballPaint)

        drawHud(canvas, w)
        if (world.phase == Phase.READY) drawReadyHint(canvas, w, h)
        if (world.phase == Phase.GAME_OVER) drawGameOver(canvas, w, h)
    }

    private fun drawHud(canvas: Canvas, w: Float) {
        hudLabel.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE", 18f * d, 26f * d, hudLabel)
        hudLabel.textAlign = Paint.Align.RIGHT
        canvas.drawText("LV ${world.level}", w - 18f * d, 26f * d, hudLabel)

        hudValue.textAlign = Paint.Align.LEFT
        canvas.drawText(world.score.toString(), 18f * d, 50f * d, hudValue)

        hudDim.textAlign = Paint.Align.RIGHT
        canvas.drawText("BEST $best", w - 18f * d, 50f * d, hudDim)

        // 剩余小球
        val gap = 14f * d
        val startX = w * 0.5f - gap * (world.lives - 1) / 2f
        for (i in 0 until world.lives) {
            canvas.drawCircle(startX + i * gap, 44f * d, 4f * d, ballPaint)
        }
    }

    private fun drawReadyHint(canvas: Canvas, w: Float, h: Float) {
        midText.color = 0xFF94A3B8.toInt()
        canvas.drawText("拖动挡板 · 点击发射", w * 0.5f, h - 44f * d, midText)
    }

    private fun drawGameOver(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, overlayPaint)
        val cy = h * 0.42f
        bigText.color = 0xFFFF4D6D.toInt()
        canvas.drawText("GAME OVER", w * 0.5f, cy, bigText)

        midText.color = Color.WHITE
        canvas.drawText("得分 ${world.score}", w * 0.5f, cy + 46f * d, midText)

        midText.color = 0xFF94A3B8.toInt()
        canvas.drawText("最高 $best", w * 0.5f, cy + 74f * d, midText)
        canvas.drawText("点击屏幕重来", w * 0.5f, cy + 124f * d, midText)
    }

    // ------------------------------------------------------------ 杂项

    private fun pushTrail() {
        for (i in trailX.size - 1 downTo 1) {
            trailX[i] = trailX[i - 1]
            trailY[i] = trailY[i - 1]
        }
        trailX[0] = world.ballX
        trailY[0] = world.ballY
    }

    private fun resetTrail() {
        for (i in trailX.indices) {
            trailX[i] = world.ballX
            trailY[i] = world.ballY
        }
    }

    private fun onGameEvent(event: GameEvent) {
        when (event) {
            GameEvent.BRICK -> buzz(10L)
            GameEvent.PADDLE -> buzz(6L)
            GameEvent.LIFE_LOST -> buzz(35L)
            GameEvent.LEVEL_UP -> buzz(45L)
            GameEvent.GAME_OVER -> {
                saveBest()
                buzz(80L)
            }
        }
    }

    private fun saveBest() {
        if (world.score > best) {
            best = world.score
            prefs.edit().putInt("best", best).apply()
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz(ms: Long) {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(ms)
            }
        } catch (e: Exception) {
            // 没有震动马达就算了，不该因为震动崩游戏
        }
    }
}
