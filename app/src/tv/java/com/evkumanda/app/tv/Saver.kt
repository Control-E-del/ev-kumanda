package com.evkumanda.app.tv

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)

fun parseAccent(s: String): Int = try {
    Color.parseColor(s)
} catch (_: Exception) {
    0xFF8B6CFF.toInt()
}

fun decodeScaled(f: File, maxW: Int): Bitmap? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / (s * 2) >= maxW) s *= 2
    val o2 = BitmapFactory.Options().apply { inSampleSize = s }
    BitmapFactory.decodeFile(f.path, o2)
} catch (_: Exception) {
    null
}

/** Akışkan, renkli aurora arka planı */
class AuroraView(ctx: Context, accent: Int) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t0 = System.nanoTime()
    private val colors = intArrayOf(accent, 0xFF2BD9C5.toInt(), 0xFF3A7BFF.toInt(), 0xFFFF5FA2.toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = (System.nanoTime() - t0) / 1e9f
        c.drawColor(0xFF05070D.toInt())
        for (i in colors.indices) {
            val cx = w * (0.5f + 0.38f * sin(t * 0.13f * (i + 1) + i * 1.7f))
            val cy = h * (0.5f + 0.32f * cos(t * 0.11f * (i + 2) + i))
            val r = max(w, h) * (0.42f + 0.08f * sin(t * 0.2f + i))
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(withAlpha(colors[i], 150), withAlpha(colors[i], 0)),
                null, Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cy, r, paint)
        }
        postInvalidateOnAnimation()
    }
}

/** Yıldızlı gökyüzü veya kar yağışı */
class ParticleView(ctx: Context, private val mode: String) : View(ctx) {
    private class P(var x: Float, var y: Float, var r: Float, var s: Float, var ph: Float)

    private val ps = ArrayList<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private var last = System.nanoTime()
    private val t0 = last

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        ps.clear()
        val n = if (mode == "snow") 160 else 240
        repeat(n) {
            ps.add(
                P(
                    Random.nextFloat() * w, Random.nextFloat() * h,
                    if (mode == "snow") 2f + Random.nextFloat() * 5f else 0.8f + Random.nextFloat() * 2.2f,
                    if (mode == "snow") 30f + Random.nextFloat() * 70f else 2f + Random.nextFloat() * 8f,
                    Random.nextFloat() * 6.28f,
                ),
            )
        }
        val top = if (mode == "snow") 0xFF0E1B33.toInt() else 0xFF02040B.toInt()
        val bottom = if (mode == "snow") 0xFF1D3557.toInt() else 0xFF0B1030.toInt()
        bgPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = (now - last) / 1e9f
        last = now
        val t = (now - t0) / 1e9f
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        for (p in ps) {
            if (mode == "snow") {
                p.y += p.s * dt
                p.x += sin(t * 0.8f + p.ph) * 18f * dt
                if (p.y > height + 10) {
                    p.y = -10f
                    p.x = Random.nextFloat() * width
                }
                paint.color = withAlpha(Color.WHITE, 190)
            } else {
                p.x += p.s * dt
                if (p.x > width + 5) p.x = -5f
                val a = (60 + 195 * abs(sin(t * (0.4f + p.r * 0.3f) + p.ph))).toInt()
                paint.color = withAlpha(Color.WHITE, a.coerceIn(0, 255))
            }
            c.drawCircle(p.x, p.y, p.r, paint)
        }
        postInvalidateOnAnimation()
    }
}

/** Ekran koruyucu içeriği. Hem DreamService hem önizleme Activity'si bunu kullanır. */
class SaverView(ctx: Context, private val cfg: TvConfig) : FrameLayout(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val timeTv = TextView(ctx)
    private val dateTv = TextView(ctx)
    private val clockBox = LinearLayout(ctx)
    private val imgA = ImageView(ctx)
    private val imgB = ImageView(ctx)
    private var video: SeamlessVideoView? = null
    private var files: List<File> = emptyList()
    private var idx = 0
    private var front = true
    private val timeFmt = SimpleDateFormat("HH:mm", Locale("tr"))
    private val dateFmt = SimpleDateFormat("d MMMM EEEE", Locale("tr"))

    private val clockTick = object : Runnable {
        override fun run() {
            val d = Date()
            timeTv.text = timeFmt.format(d)
            dateTv.text = dateFmt.format(d) + (if (Weather.text.isNotEmpty()) "   ·   " + Weather.text else "")
            // OLED yanmasını önlemek için saati yavaşça kaydır
            val m = (System.currentTimeMillis() / 60000L).toInt()
            clockBox.translationX = ((m * 37) % 120 - 60).toFloat()
            clockBox.translationY = ((m * 53) % 80 - 40).toFloat()
            handler.postDelayed(this, 10_000)
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics,
    ).toInt()

    private fun buildImages(): Boolean {
        files = TvStore.mediaFiles(context, "wall-").filter { it.name.matches(Regex(".*\\.(jpe?g|png|webp)", RegexOption.IGNORE_CASE)) }
        if (files.isEmpty()) return false
        for (v in listOf(imgA, imgB)) {
            v.scaleType = ImageView.ScaleType.CENTER_CROP
            v.alpha = 0f
            addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        return true
    }

    private fun buildVideo(): Boolean {
        val f = File(TvStore.mediaDir(context), cfg.saverVideo)
        if (cfg.saverVideo.isBlank() || !f.exists()) return false
        val v = SeamlessVideoView(context, f)
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        video = v
        return true
    }

    private fun buildClock() {
        timeTv.setTextColor(withAlpha(Color.WHITE, 235))
        timeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 110f)
        timeTv.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        timeTv.setShadowLayer(24f, 0f, 4f, 0x99000000.toInt())
        dateTv.setTextColor(withAlpha(Color.WHITE, 200))
        dateTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        dateTv.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        dateTv.setShadowLayer(16f, 0f, 2f, 0x99000000.toInt())
        clockBox.orientation = LinearLayout.VERTICAL
        clockBox.addView(timeTv)
        clockBox.addView(dateTv)
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
        lp.setMargins(dp(72), 0, 0, dp(64))
        addView(clockBox, lp)
    }

    private fun showNext() {
        if (!running || files.isEmpty()) return
        val f = files[idx % files.size]
        idx++
        Thread {
            val bmp = decodeScaled(f, 1920)
            handler.post {
                if (!running) return@post
                if (bmp == null) {
                    handler.postDelayed({ showNext() }, 1000)
                    return@post
                }
                val show = if (front) imgA else imgB
                val hide = if (front) imgB else imgA
                front = !front
                show.setImageBitmap(bmp)
                show.scaleX = 1f
                show.scaleY = 1f
                show.translationX = 0f
                val dx = (if (idx % 2 == 0) -1 else 1) * 40f
                val ms = cfg.saverInterval * 1000L + 3000L
                val set = AnimatorSet()
                set.playTogether(
                    ObjectAnimator.ofFloat(show, View.SCALE_X, 1f, 1.15f),
                    ObjectAnimator.ofFloat(show, View.SCALE_Y, 1f, 1.15f),
                    ObjectAnimator.ofFloat(show, View.TRANSLATION_X, 0f, dx),
                )
                set.duration = ms
                set.start()
                show.animate().alpha(1f).setDuration(1500).start()
                hide.animate().alpha(0f).setDuration(1500).start()
                handler.postDelayed({ showNext() }, cfg.saverInterval * 1000L)
            }
        }.start()
    }

    fun start() {
        if (running) return
        running = true
        video?.start()
        if (files.isNotEmpty()) showNext()
        if (cfg.saverClock) handler.post(clockTick)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        video?.pause()
    }

    init {
        setBackgroundColor(Color.BLACK)
        val ok = when (cfg.saverMode) {
            "images" -> buildImages()
            "video" -> buildVideo()
            "stars", "snow" -> {
                addView(ParticleView(context, cfg.saverMode), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                true
            }
            else -> false
        }
        if (!ok) {
            addView(AuroraView(context, parseAccent(cfg.accent)), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        if (cfg.saverClock) buildClock()
    }
}

/** Android "Ekran koruyucu" olarak seçilebilir */
class SaverDreamService : DreamService() {
    private var view: SaverView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val v = SaverView(this, TvStore.load(applicationContext))
        view = v
        setContentView(v)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.start()
    }

    override fun onDreamingStopped() {
        view?.stop()
        super.onDreamingStopped()
    }
}

/** Boşta kalınca launcher tarafından başlatılır, önizleme için de kullanılır. Herhangi bir tuş kapatır. */
class SaverActivity : Activity() {
    private var view: SaverView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val v = SaverView(this, TvStore.load(applicationContext))
        view = v
        setContentView(v)
    }

    override fun onResume() {
        super.onResume()
        view?.start()
    }

    override fun onPause() {
        view?.stop()
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) finish()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        finish()
        return true
    }
}
