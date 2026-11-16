package com.evkumanda.app.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Döngüde oynayan, ekranı kırpıp dolduran (center-crop), sessiz video.
 *
 * - Normal çözünürlükte (4K altı) iki oynatıcı kullanır: bitişe [fadeMs] kala ikincisi baştan
 *   başlar ve yumuşakça belirir, döngü noktasında donma olmaz.
 * - 4K ve üstünde (çoğu TV yongası tek 4K dekoder taşır) tek oynatıcı ile döngü yapar. Döngü
 *   noktasında son kare ekranda kalır, siyah ekran olmaz. En temiz sonuç için başı ve sonu
 *   birbirine benzeyen (döngüye uygun) bir video kullan.
 * - Dekoder hata verirse [onError] ile bildirir.
 */
class SeamlessVideoView(ctx: Context, private val file: File, private val fadeMs: Long = 1200L) : FrameLayout(ctx) {

    private class Slot(val tv: TextureView, val player: ExoPlayer) {
        var size: VideoSize? = null
    }

    var onError: ((String) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var front: Slot
    private var back: Slot? = null
    private var running = false
    private var switching = false
    private var single = false
    private var errorReported = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val b = back
            if (b != null && !single && !switching) {
                val a = front
                val d = a.player.duration
                if (d != C.TIME_UNSET && d > fadeMs * 3) {
                    val left = d - a.player.currentPosition
                    if (left <= fadeMs || a.player.playbackState == Player.STATE_ENDED) {
                        switching = true
                        b.player.seekTo(0)
                        b.player.play()
                        b.tv.animate().alpha(1f).setDuration(fadeMs).start()
                        a.tv.animate().alpha(0f).setDuration(fadeMs).withEndAction {
                            a.player.pause()
                            a.player.seekTo(0)
                            switching = false
                        }.start()
                        front = b
                        back = a
                    }
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    private fun fit(tv: TextureView, v: VideoSize?) {
        if (v == null || v.width == 0 || v.height == 0) return
        val bw = tv.width.toFloat()
        val bh = tv.height.toFloat()
        if (bw == 0f || bh == 0f) return
        val vr = v.width.toFloat() / v.height
        val br = bw / bh
        val sx = if (vr > br) vr / br else 1f
        val sy = if (vr > br) 1f else br / vr
        val m = Matrix()
        m.setScale(sx, sy, bw / 2f, bh / 2f)
        tv.setTransform(m)
    }

    private fun createSlot(): Slot {
        val tv = TextureView(context)
        val factory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(context, factory).build()
        p.volume = 0f
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        p.setVideoTextureView(tv)
        val slot = Slot(tv, p)
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                slot.size = videoSize
                fit(tv, videoSize)
                if (slot === front) onFrontSize(videoSize)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && slot === front) {
                    val d = p.duration
                    if (d != C.TIME_UNSET && d <= fadeMs * 3) enterSingle()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (slot === front) {
                    if (!errorReported) {
                        errorReported = true
                        onError?.invoke(
                            "Video oynatılamadı. TV bu çözünürlüğü/codec'i desteklemiyor olabilir. " +
                                "H.265 (HEVC) 4K 30 fps dene.",
                        )
                    }
                } else {
                    // İkinci dekoder açılamadı (donanım sınırı): tek oynatıcı kipine geç
                    enterSingle()
                }
            }
        })
        tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fit(tv, slot.size) }
        addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        p.prepare()
        return slot
    }

    private fun onFrontSize(v: VideoSize) {
        if (single || back != null) return
        if (v.height >= 2000 || v.width >= 3000) {
            enterSingle()
        } else {
            val s = createSlot()
            s.tv.alpha = 0f
            back = s
        }
    }

    private fun enterSingle() {
        if (single) return
        single = true
        front.player.repeatMode = Player.REPEAT_MODE_ONE
        front.tv.animate().cancel()
        front.tv.alpha = 1f
        back?.let {
            try {
                it.player.release()
            } catch (_: Exception) {
            }
            removeView(it.tv)
        }
        back = null
    }

    init {
        setBackgroundColor(Color.BLACK)
        front = createSlot()
    }

    /** Başlatır veya duraklatılmışsa devam ettirir */
    fun start() {
        if (running) return
        running = true
        front.player.play()
        handler.post(tick)
    }

    fun pause() {
        running = false
        handler.removeCallbacksAndMessages(null)
        front.player.pause()
        back?.player?.pause()
    }

    fun release() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try { front.player.release() } catch (_: Exception) {}
        try { back?.player?.release() } catch (_: Exception) {}
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}
