package com.dsh.brickbash

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 一局的阶段。 */
enum class Phase { READY, PLAYING, GAME_OVER }

/** 抛给 View 层的事件：震动、存档都在那一层做。 */
enum class GameEvent { PADDLE, BRICK, LIFE_LOST, LEVEL_UP, GAME_OVER }

class Brick(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val color: Int
)

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Int
)

/**
 * 纯游戏逻辑：不碰 Canvas，不碰 Android API。
 * 尺寸全部用像素，由 View 层的 density 换算好传进来。
 */
class GameWorld(private val d: Float) {

    companion object {
        private const val COLS = 7
        private val ROW_COLORS = intArrayOf(
            0xFFFF4D6D.toInt(),
            0xFFFF9F1C.toInt(),
            0xFFFFD166.toInt(),
            0xFF06D6A0.toInt(),
            0xFF4CC9F0.toInt(),
            0xFFB388FF.toInt(),
            0xFFF15BB5.toInt()
        )
    }

    var width = 0f
        private set
    var height = 0f
        private set

    var phase = Phase.READY
        private set

    var score = 0
        private set

    var lives = 3
        private set

    var level = 1
        private set

    val bricks = ArrayList<Brick>(COLS * 7)
    val particles = ArrayList<Particle>(256)

    var paddleW = 0f
        private set
    var paddleH = 0f
        private set
    var paddleX = 0f
        private set
    var paddleY = 0f
        private set

    var ballX = 0f
        private set
    var ballY = 0f
        private set
    var ballVX = 0f
        private set
    var ballVY = 0f
        private set
    var ballR = 0f
        private set

    var onEvent: ((GameEvent) -> Unit)? = null

    val paddleLeft: Float get() = paddleX - paddleW * 0.5f
    val paddleRight: Float get() = paddleX + paddleW * 0.5f

    private val random = Random(System.nanoTime())
    private var started = false

    /** 屏幕尺寸确定后调用；第一次调用会直接开一局。 */
    fun resize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        width = w
        height = h
        paddleW = min(w * 0.30f, 150f * d)
        paddleH = 14f * d
        paddleY = h - 92f * d
        ballR = 9f * d
        paddleX = paddleX.coerceIn(paddleW * 0.5f, max(paddleW * 0.5f, w - paddleW * 0.5f))

        if (!started) {
            started = true
            newGame()
        } else {
            if (bricks.isEmpty()) buildLevel()
            parkBall()
        }
    }

    fun newGame() {
        score = 0
        lives = 3
        level = 1
        phase = Phase.READY
        particles.clear()
        buildLevel()
        parkBall()
    }

    /** 手指拖动挡板。 */
    fun movePaddle(x: Float) {
        if (width <= 0f) return
        paddleX = x.coerceIn(paddleW * 0.5f, width - paddleW * 0.5f)
        if (phase == Phase.READY) parkBall()
    }

    /** 点击发射。 */
    fun launch() {
        if (phase != Phase.READY) return
        val sp = levelBallSpeed()
        val t = (random.nextFloat() * 0.7f - 0.35f).toDouble()
        ballVX = (sp * sin(t)).toFloat()
        ballVY = (-sp * cos(t)).toFloat()
        phase = Phase.PLAYING
    }

    fun update(dt: Float) {
        if (dt <= 0f) return
        updateParticles(dt)
        when (phase) {
            Phase.READY -> parkBall()
            Phase.PLAYING -> simulate(dt)
            Phase.GAME_OVER -> Unit
        }
    }

    // ---------------------------------------------------------------- 内部

    private fun buildLevel() {
        bricks.clear()
        if (width <= 0f) return
        val rows = min(4 + level, 7)
        val gap = 5f * d
        val marginX = 14f * d
        val topY = 82f * d
        val usable = width - marginX * 2f
        if (usable <= 0f) return
        val bw = (usable - gap * (COLS - 1)) / COLS
        val bh = 18f * d
        for (r in 0 until rows) {
            for (c in 0 until COLS) {
                val l = marginX + c * (bw + gap)
                val t = topY + r * (bh + gap)
                bricks.add(Brick(l, t, l + bw, t + bh, ROW_COLORS[r % ROW_COLORS.size]))
            }
        }
    }

    private fun levelBallSpeed(): Float = min(330f + (level - 1) * 40f, 700f) * d

    private fun parkBall() {
        ballX = paddleX
        ballY = paddleY - ballR - 2f * d
        ballVX = 0f
        ballVY = 0f
    }

    /** 固定小步长推进，避免球速快时穿模。 */
    private fun simulate(dt: Float) {
        var remaining = dt
        val step = 1f / 240f
        while (remaining > 0.0001f && phase == Phase.PLAYING) {
            val s = min(remaining, step)
            stepBall(s)
            remaining -= s
        }
    }

    private fun stepBall(s: Float) {
        ballX += ballVX * s
        ballY += ballVY * s

        if (ballX - ballR < 0f) {
            ballX = ballR
            ballVX = abs(ballVX)
        } else if (ballX + ballR > width) {
            ballX = width - ballR
            ballVX = -abs(ballVX)
        }
        if (ballY - ballR < 0f) {
            ballY = ballR
            ballVY = abs(ballVY)
        }

        if (ballVY > 0f && touchesPaddle()) {
            bounceOffPaddle()
        }

        val brick = brickAt()
        if (brick != null) hitBrick(brick)

        if (ballY - ballR > height + 30f * d) loseLife()
    }

    private fun touchesPaddle(): Boolean =
        ballY + ballR >= paddleY &&
            ballY - ballR <= paddleY + paddleH &&
            ballX + ballR >= paddleLeft &&
            ballX - ballR <= paddleRight

    private fun bounceOffPaddle() {
        val rel = ((ballX - paddleX) / (paddleW * 0.5f)).coerceIn(-1f, 1f)
        val angle = rel * (Math.PI / 3.0)
        val sp = max(hypot(ballVX, ballVY), levelBallSpeed())
        ballVX = (sp * sin(angle)).toFloat()
        ballVY = (-sp * cos(angle)).toFloat()
        ballY = paddleY - ballR - 0.5f
        onEvent?.invoke(GameEvent.PADDLE)
    }

    private fun brickAt(): Brick? {
        for (i in bricks.indices) {
            val b = bricks[i]
            if (ballX + ballR > b.left && ballX - ballR < b.right &&
                ballY + ballR > b.top && ballY - ballR < b.bottom
            ) {
                return b
            }
        }
        return null
    }

    private fun hitBrick(b: Brick) {
        bricks.remove(b)

        // 按穿透深度判断从哪一面撞上来的
        val overlapX = min(ballX + ballR, b.right) - max(ballX - ballR, b.left)
        val overlapY = min(ballY + ballR, b.bottom) - max(ballY - ballR, b.top)
        if (overlapY < overlapX) {
            ballVY = -ballVY
            ballY += if (ballVY > 0f) overlapY else -overlapY
        } else {
            ballVX = -ballVX
            ballX += if (ballVX > 0f) overlapX else -overlapX
        }

        score += 10 + (level - 1) * 5
        burst(b)
        onEvent?.invoke(GameEvent.BRICK)

        if (bricks.isEmpty()) nextLevel()
    }

    private fun burst(b: Brick) {
        repeat(12) {
            val a = random.nextFloat() * 6.2831855f
            val sp = (60f + random.nextFloat() * 200f) * d
            val life = 0.28f + random.nextFloat() * 0.30f
            particles.add(
                Particle(
                    x = b.left + (b.right - b.left) * random.nextFloat(),
                    y = b.top + (b.bottom - b.top) * random.nextFloat(),
                    vx = cos(a) * sp,
                    vy = sin(a) * sp,
                    life = life,
                    maxLife = life,
                    color = b.color
                )
            )
        }
    }

    private fun updateParticles(dt: Float) {
        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) {
                particles.removeAt(i)
                continue
            }
            p.vy += 420f * d * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.99f
            i++
        }
    }

    private fun nextLevel() {
        level += 1
        buildLevel()
        parkBall()
        phase = Phase.READY
        onEvent?.invoke(GameEvent.LEVEL_UP)
    }

    private fun loseLife() {
        lives -= 1
        if (lives <= 0) {
            lives = 0
            phase = Phase.GAME_OVER
            onEvent?.invoke(GameEvent.GAME_OVER)
        } else {
            parkBall()
            phase = Phase.READY
            onEvent?.invoke(GameEvent.LIFE_LOST)
        }
    }
}
