package com.evkumanda.app

import android.content.Context
import android.net.ConnectivityManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Aynı Wi-Fi ağındaki TV, PS5 ve bilgisayarı kendi bulur */
object Discovery {

    class Found(
        var tvIp: String = "",
        var tvMac: String = "",
        var ps5Ip: String = "",
        var ps5Mac: String = "",
        var pcIp: String = "",
    )

    /** Telefonun/tabletin kendi IPv4 adresi ve alt ağ öneki (çoğunlukla 24) */
    fun localNet(ctx: Context): Pair<String, Int>? {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
            val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
            Pair(la.address.hostAddress ?: return null, la.prefixLength)
        } catch (_: Exception) {
            null
        }
    }

    private fun open(ip: String, port: Int, timeout: Int = 350): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeout); true }
    } catch (_: Exception) {
        false
    }

    /** /24 (veya daha küçük) ağdaki tüm adreslerde verilen portlardan birini dener */
    private fun scan(base: String, self: String, ports: IntArray, skip: Set<String>): List<String> {
        val prefix = base.substringBeforeLast('.') + "."
        val pool = Executors.newFixedThreadPool(64)
        try {
            val jobs = (1..254).map { i ->
                val ip = prefix + i
                pool.submit(Callable<String?> {
                    if (ip == self || ip in skip) null
                    else if (ports.any { open(ip, it) }) ip else null
                })
            }
            return jobs.mapNotNull { try { it.get() } catch (_: Exception) { null } }
        } finally {
            pool.shutdownNow()
        }
    }

    /** PS5'e özel keşif paketi: UDP 987 yayın. Yanıt "host-id" olarak MAC adresini de verir. */
    private fun findPs5(broadcast: String): Pair<String, String>? {
        return try {
            DatagramSocket().use { s ->
                s.broadcast = true
                s.soTimeout = 1200
                val msg = "SRCH * HTTP/1.1\ndevice-discovery-protocol-version:00030010\n".toByteArray()
                for (target in listOf(broadcast, "255.255.255.255")) {
                    try {
                        s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(target), 987))
                    } catch (_: Exception) {
                    }
                }
                val buf = ByteArray(1024)
                val end = System.currentTimeMillis() + 2500
                while (System.currentTimeMillis() < end) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(p)
                    } catch (_: Exception) {
                        break
                    }
                    val text = String(p.data, 0, p.length)
                    if (text.contains("PS5") || text.startsWith("HTTP/1.1 200") || text.startsWith("HTTP/1.1 620")) {
                        val id = Regex("host-id:([0-9A-Fa-f]{12})").find(text)?.groupValues?.get(1) ?: ""
                        val mac = if (id.length == 12) id.chunked(2).joinToString(":").uppercase() else ""
                        return Pair(p.address.hostAddress ?: continue, mac)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun broadcastOf(ip: String, prefix: Int): String {
        val b = InetAddress.getByName(ip).address
        val mask = if (prefix <= 0) 0 else (-1 shl (32 - prefix))
        var v = 0
        for (x in b) v = (v shl 8) or (x.toInt() and 0xff)
        val bc = v or mask.inv()
        return listOf(24, 16, 8, 0).joinToString(".") { ((bc ushr it) and 0xff).toString() }
    }

    /** TV: Android TV Remote portu (6466) ve bizim panel (8765) açık olan cihaz */
    fun findTv(ctx: Context): String? {
        val (self, _) = localNet(ctx) ?: return null
        return scan(self, self, intArrayOf(8765, 6466), emptySet()).firstOrNull()
    }

    /** Hepsini bir kerede bul. Bulunamayan alan boş kalır. */
    fun all(ctx: Context): Found {
        val f = Found()
        val (self, prefix) = localNet(ctx) ?: return f
        val ps5 = findPs5(broadcastOf(self, prefix))
        if (ps5 != null) {
            f.ps5Ip = ps5.first
            f.ps5Mac = ps5.second
        }
        val tvs = scan(self, self, intArrayOf(8765, 6466), emptySet())
        // panel (8765) olanı TV'dir; yoksa 6466 açık ilk cihaz
        f.tvIp = tvs.firstOrNull { open(it, 8765) } ?: tvs.firstOrNull() ?: ""
        if (f.tvIp.isNotEmpty()) f.tvMac = tvMac(f.tvIp)
        val skip = setOf(f.tvIp, f.ps5Ip) + tvs.toSet()
        f.pcIp = scan(self, self, intArrayOf(3389, 22, 445, 135), skip).firstOrNull() ?: ""
        return f
    }

    /** TV paneli /api/info ile MAC verebiliyorsa alır (Android sürümüne bağlı, boş olabilir) */
    fun tvMac(ip: String): String = try {
        val c = java.net.URL("http://$ip:8765/api/info").openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 1500
        c.readTimeout = 1500
        val t = c.inputStream.bufferedReader().readText()
        org.json.JSONObject(t).optString("mac", "")
    } catch (_: Exception) {
        ""
    }

    /** PS5 açık mı (rest modda 620 yanıtı, açıkken 200) */
    fun ps5Alive(ip: String): Boolean = try {
        DatagramSocket().use { s ->
            s.soTimeout = 900
            val msg = "SRCH * HTTP/1.1\ndevice-discovery-protocol-version:00030010\n".toByteArray()
            s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(ip), 987))
            val p = DatagramPacket(ByteArray(512), 512)
            s.receive(p)
            true
        }
    } catch (_: Exception) {
        false
    }
}
