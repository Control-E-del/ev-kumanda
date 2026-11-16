package com.evkumanda.app.tv

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

data class AppInfo(val pkg: String, val label: String)

fun installedApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val seen = HashSet<String>()
    val out = ArrayList<AppInfo>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(i, 0)) {
            val p = ri.activityInfo.packageName
            if (p == ctx.packageName) continue
            if (seen.add(p)) out.add(AppInfo(p, ri.loadLabel(pm).toString()))
        }
    }
    return out.sortedBy { it.label.lowercase() }
}

/** TV üzerinde çalışan mini web sunucusu: http://TV_IP:8765 */
object WebPanel {
    const val PORT = 8765
    private var started = false

    fun start(ctx: Context) {
        synchronized(this) {
            if (started) return
            started = true
        }
        registerNsd(ctx)
        thread(isDaemon = true, name = "evtv-web") {
            try {
                val ss = ServerSocket(PORT)
                while (true) {
                    val c = ss.accept()
                    thread(isDaemon = true) {
                        try {
                            handle(ctx, c)
                        } catch (_: Exception) {
                        } finally {
                            try { c.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {
                synchronized(this) { started = false }
            }
        }
    }

    /** Tablet uygulaması TV'yi bulabilsin diye ağda "_evtv._tcp" olarak duyurulur */
    private fun registerNsd(ctx: Context) {
        try {
            val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo()
            info.serviceName = "EvTV"
            info.serviceType = "_evtv._tcp."
            info.port = PORT
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo?) {}
                override fun onRegistrationFailed(i: NsdServiceInfo?, e: Int) {}
                override fun onServiceUnregistered(i: NsdServiceInfo?) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo?, e: Int) {}
            })
        } catch (_: Exception) {
        }
    }

    /** Okunabiliyorsa TV'nin MAC adresi (Android 10+ çoğu cihazda engelli, bu durumda boş döner) */
    private fun readMac(): String {
        for (n in listOf("eth0", "wlan0")) {
            try {
                val t = File("/sys/class/net/$n/address").readText().trim()
                if (t.length == 17 && t != "00:00:00:00:00:00" && t != "02:00:00:00:00:00") return t
            } catch (_: Exception) {
            }
        }
        return ""
    }

    private fun readLine(inp: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (buf.size() < 16384) {
            val b = inp.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
        }
        return buf.toString("UTF-8")
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n" +
            "Content-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun safeName(n: String): String {
        val s = n.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        return if (s.isBlank() || s.startsWith(".")) "file" else s
    }

    private fun parseQuery(q: String): Map<String, String> {
        val m = HashMap<String, String>()
        if (q.isBlank()) return m
        for (kv in q.split("&")) {
            val i = kv.indexOf('=')
            if (i > 0) m[kv.substring(0, i)] = URLDecoder.decode(kv.substring(i + 1), "UTF-8")
        }
        return m
    }

    private fun contentType(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".mp4") -> "video/mp4"
            n.endsWith(".webm") -> "video/webm"
            else -> "application/octet-stream"
        }
    }

    private fun readBody(inp: InputStream, len: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var left = len
        while (left > 0) {
            val r = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) break
            out.write(buf, 0, r)
            left -= r
        }
        return out.toByteArray()
    }

    private fun handle(ctx: Context, sock: Socket) {
        sock.soTimeout = 30_000
        val inp = BufferedInputStream(sock.getInputStream(), 65536)
        val out = BufferedOutputStream(sock.getOutputStream(), 65536)

        val reqLine = readLine(inp) ?: return
        val parts = reqLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]
        var len = 0L
        while (true) {
            val h = readLine(inp) ?: break
            if (h.isEmpty()) break
            val i = h.indexOf(':')
            if (i > 0 && h.substring(0, i).equals("content-length", ignoreCase = true)) {
                len = h.substring(i + 1).trim().toLongOrNull() ?: 0L
            }
        }
        val path = target.substringBefore('?')
        val query = parseQuery(target.substringAfter('?', ""))
        val dir = TvStore.mediaDir(ctx)

        when {
            method == "GET" && path == "/" ->
                respond(out, 200, "text/html; charset=utf-8", PANEL_HTML.toByteArray(Charsets.UTF_8))

            method == "GET" && path == "/api/state" -> {
                val o = JSONObject()
                o.put("cfg", TvStore.cfg.toJson())
                val media = JSONArray()
                (dir.listFiles() ?: emptyArray()).filter { it.isFile }.sortedBy { it.name }.forEach {
                    media.put(JSONObject().put("name", it.name).put("size", it.length()))
                }
                o.put("media", media)
                o.put("free", dir.usableSpace)
                val apps = JSONArray()
                installedApps(ctx).forEach { apps.put(JSONObject().put("pkg", it.pkg).put("label", it.label)) }
                o.put("apps", apps)
                respond(out, 200, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8))
            }

            method == "GET" && path == "/api/info" -> {
                val o = JSONObject()
                o.put("name", android.os.Build.MODEL ?: "TV")
                o.put("mac", readMac())
                respond(out, 200, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8))
            }

            method == "PUT" && path == "/api/upload" && len > dir.usableSpace - 300_000_000L ->
                respond(out, 507, "text/plain", "TV'de yeterli boş alan yok".toByteArray())

            method == "PUT" && path == "/api/upload" -> {
                val f = File(dir, safeName(query["name"] ?: "file"))
                val tmp = File(dir, f.name + ".part")
                FileOutputStream(tmp).use { fo ->
                    val buf = ByteArray(65536)
                    var left = len
                    while (left > 0) {
                        val r = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (r < 0) break
                        fo.write(buf, 0, r)
                        left -= r
                    }
                    if (left > 0) {
                        tmp.delete()
                        respond(out, 400, "text/plain", "eksik veri".toByteArray())
                        return
                    }
                }
                f.delete()
                tmp.renameTo(f)
                respond(out, 200, "text/plain", "ok".toByteArray())
            }

            method == "POST" && path == "/api/delete" -> {
                File(dir, safeName(query["name"] ?: "")).delete()
                respond(out, 200, "text/plain", "ok".toByteArray())
            }

            method == "POST" && path == "/api/config" -> {
                val body = String(readBody(inp, len), Charsets.UTF_8)
                try {
                    TvStore.save(ctx, TvConfig.fromJson(JSONObject(body)))
                    respond(out, 200, "text/plain", "ok".toByteArray())
                } catch (e: Exception) {
                    respond(out, 400, "text/plain", ("hata: " + e.message).toByteArray())
                }
            }

            method == "GET" && path.startsWith("/media/") -> {
                val f = File(dir, safeName(path.removePrefix("/media/")))
                if (!f.exists()) {
                    respond(out, 404, "text/plain", "yok".toByteArray())
                } else {
                    val head = "HTTP/1.1 200 OK\r\nContent-Type: ${contentType(f.name)}\r\n" +
                        "Content-Length: ${f.length()}\r\nConnection: close\r\n\r\n"
                    out.write(head.toByteArray())
                    f.inputStream().use { it.copyTo(out, 65536) }
                    out.flush()
                }
            }

            else -> respond(out, 404, "text/plain", "bulunamadı".toByteArray())
        }
    }
}
