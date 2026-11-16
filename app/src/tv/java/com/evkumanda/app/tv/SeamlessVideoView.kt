package com.evkumanda.app.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Kesintisiz döngü: iki oynatıcı kullanır. Videonun bitmesine [fadeMs] kala ikinci oynatıcı
 * baştan başlar ve ilkinin üstüne yumuşakça belirir. Böylece döngü noktasında donma veya
 * siyah kare olmaz. Ekranı kırpıp doldurur (center-crop), sessizdir.
 */
class SeamlessVideoView(ctx: Context, private val file: File, private val fadeMs: Long = 1200L) : FrameLayout(ctx) {

    private class Slot(val tv: TextureView, val player: ExoPlayer) {
        var size: VideoSize? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val slots: Array<Slot>
    private var cur = 0
    private var running = false
    private var switching = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val a = slots[cur]
            val b = slots[1 - cur]
            val d = a.player.duration
            if (!switching && d != C.TIME_UNSET && d > fadeMs * 3) {
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
                    cur = 1 - cur
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

    init {
        setBackgroundColor(Color.BLACK)
        slots = Array(2) { i ->
            val tv = TextureView(ctx)
            val p = ExoPlayer.Builder(ctx).build()
            p.volume = 0f
            p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            p.setVideoTextureView(tv)
            val slot = Slot(tv, p)
            p.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    slot.size = videoSize
                    fit(tv, videoSize)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    // Çok kısa videolarda ikinci oynatıcı devreye girmez, düz döngü yap
                    if (state == Player.STATE_READY && i == 0) {
                        val d = p.duration
                        if (d != C.TIME_UNSET && d <= fadeMs * 3) p.repeatMode = Player.REPEAT_MODE_ONE
                    }
                }
            })
            tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fit(tv, slot.size) }
            p.prepare()
            addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            slot
        }
        slots[1].tv.alpha = 0f
    }

    /** Başlatır veya duraklatılmışsa devam ettirir */
    fun start() {
        if (running) return
        running = true
        slots[cur].player.play()
        handler.post(tick)
    }

    fun pause() {
        running = false
        handler.removeCallbacksAndMessages(null)
        slots.forEach { it.player.pause() }
    }

    fun release() {
        running = false
        handler.removeCallbacksAndMessages(null)
        slots.forEach { it.player.release() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}
