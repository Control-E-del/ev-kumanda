package com.evkumanda.app.tv

import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/** Videonun formatını okuyup TV dekoderinin destekleyip desteklemediğini açıklar */
object VideoProbe {
    private fun codecName(mime: String) = when (mime) {
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265 (HEVC)"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        else -> mime.removePrefix("video/")
    }

    fun explain(f: File): String {
        return try {
            val ex = MediaExtractor()
            ex.setDataSource(f.path)
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val t = ex.getTrackFormat(i)
                if (t.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { fmt = t; break }
            }
            ex.release()
            val t = fmt ?: return "Dosyada video izi bulunamadı."
            val mime = t.getString(MediaFormat.KEY_MIME) ?: ""
            val w = t.getInteger(MediaFormat.KEY_WIDTH)
            val h = t.getInteger(MediaFormat.KEY_HEIGHT)
            val fps = if (t.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                try { t.getInteger(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { t.getFloat(MediaFormat.KEY_FRAME_RATE).toInt() }
            } else 30
            val desc = "${codecName(mime)} ${w}x$h ${fps}fps"
            // TV'nin bu codec için desteklediği en yüksek çözünürlük/fps
            var ok = false
            var best = ""
            var bestPx = 0
            for (ci in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
                if (ci.isEncoder || !ci.supportedTypes.any { it.equals(mime, true) }) continue
                val vc = try { ci.getCapabilitiesForType(mime).videoCapabilities } catch (_: Exception) { null } ?: continue
                if (vc.areSizeAndRateSupported(w, h, fps.toDouble())) ok = true
                val mw = vc.supportedWidths.upper
                val mh = vc.supportedHeights.upper
                val mf = try { vc.getSupportedFrameRatesFor(minOf(w, mw), minOf(h, mh)).upper.toInt() } catch (_: Exception) { 0 }
                val s = "${mw}x$mh${if (mf > 0) " ${mf}fps" else ""}"
                if (mw * mh > bestPx) { bestPx = mw * mh; best = s }
            }
            when {
                best.isEmpty() -> "Video: $desc. Bu TV ${codecName(mime)} çözemiyor. H.265 (HEVC) ya da H.264'e dönüştür."
                !ok -> "Video: $desc. TV ${codecName(mime)} için en fazla $best destekliyor. 30 fps ve/veya H.265'e dönüştür."
                else -> "Video: $desc. TV destekliyor görünüyor; dekoder meşgul olabilir, tekrar deneniyor."
            }
        } catch (e: Exception) {
            "Video okunamadı: ${e.message}"
        }
    }
}
