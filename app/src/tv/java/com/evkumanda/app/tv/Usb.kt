package com.evkumanda.app.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlin.concurrent.thread

/**
 * USB bellek takılınca içindeki videoyu bulur. Video takılı olduğu sürece ana ekranda ve ekran
 * koruyucuda oynar. TV'de yer varsa arka planda TV'ye kopyalanır ve ana ekran videosu yapılır,
 * böylece USB çıkarılınca da oynamaya devam eder.
 */
object UsbMedia {
    var video by mutableStateOf<File?>(null)
        private set
    var status by mutableStateOf("")
        private set

    private val videoExt = Regex(".*\\.(mp4|mkv|mov|m4v|webm|ts)$", RegexOption.IGNORE_CASE)
    private var receiver: BroadcastReceiver? = null
    @Volatile private var copying = false

    /** Takılı, çıkarılabilir depolama kök klasörleri */
    fun roots(ctx: Context): List<File> {
        val out = ArrayList<File>()
        try {
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            for (v in sm.storageVolumes) {
                if (!v.isRemovable || v.state != Environment.MEDIA_MOUNTED) continue
                val dir: File? = if (Build.VERSION.SDK_INT >= 30) {
                    v.directory
                } else {
                    try {
                        (v.javaClass.getMethod("getPath").invoke(v) as? String)?.let { File(it) }
                    } catch (_: Exception) {
                        null
                    }
                }
                if (dir != null) out.add(dir)
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun findVideo(root: File): File? {
        // Önce kök, sonra bir alt klasör (ör. /Video). Gizli/sistem klasörleri atlanır.
        val top = root.listFiles() ?: return null
        top.filter { it.isFile && videoExt.matches(it.name) && it.length() > 0 }.minByOrNull { it.name.lowercase() }?.let { return it }
        for (d in top.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "LOST.DIR" && it.name != "System Volume Information" }.sortedBy { it.name.lowercase() }) {
            (d.listFiles() ?: continue).filter { it.isFile && videoExt.matches(it.name) && it.length() > 0 }
                .minByOrNull { it.name.lowercase() }?.let { return it }
        }
        return null
    }

    /** USB'yi yeniden tarar. Ana iş parçacığı dışında çağır. */
    fun rescan(ctx: Context) {
        val rs = roots(ctx)
        var found: File? = null
        var unreadable = false
        for (r in rs) {
            if (r.listFiles() == null) {
                unreadable = true
                continue
            }
            found = findVideo(r)
            if (found != null) break
        }
        val changed = found?.path != video?.path
        video = found
        status = when {
            found != null -> "USB: ${found.name}"
            unreadable -> "USB okunamadı: depolama iznini ver"
            else -> ""
        }
        if (changed && found != null && TvStore.cfg.usbAuto) copyToTv(ctx, found)
    }

    /** Videoyu TV'ye kopyalayıp ana ekran ve ekran koruyucu videosu yapar (yer varsa) */
    private fun copyToTv(ctx: Context, src: File) {
        if (copying) return
        val dir = TvStore.mediaDir(ctx)
        val name = "vid-usb-" + src.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dst = File(dir, name)
        fun apply() {
            val c = TvStore.cfg
            TvStore.save(ctx, c.copy(homeVideo = name, saverVideo = name, saverMode = "video"))
        }
        if (dst.exists() && dst.length() == src.length()) {
            apply()
            return
        }
        if (dir.usableSpace - src.length() < 800_000_000L) {
            status = "USB: ${src.name} (TV'de kopyalamaya yer yok, USB'den oynuyor)"
            return
        }
        copying = true
        thread(name = "usb-copy", isDaemon = true) {
            val part = File(dir, "$name.part")
            try {
                src.inputStream().use { i -> part.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
                if (part.length() == src.length() && part.renameTo(dst)) {
                    apply()
                    status = "USB videosu TV'ye kopyalandı, USB'yi çıkarabilirsin"
                } else {
                    part.delete()
                }
            } catch (_: Exception) {
                part.delete()
            } finally {
                copying = false
            }
        }
    }

    /** Takma/çıkarma olaylarını dinler */
    fun register(ctx: Context) {
        if (receiver != null) return
        val app = ctx.applicationContext
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                thread(isDaemon = true) {
                    // Bağlama birkaç saniye sürebilir
                    Thread.sleep(if (i.action == Intent.ACTION_MEDIA_MOUNTED) 1500 else 0)
                    rescan(app)
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
            else app.registerReceiver(r, f)
            receiver = r
        } catch (_: Exception) {
        }
        thread(isDaemon = true) { rescan(app) }
    }
}
