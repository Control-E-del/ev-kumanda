package com.evkumanda.app

import android.content.Context
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Ayarlar (SharedPreferences) */
object K {
    const val PC_IP = "pc_ip"
    const val PC_MAC = "pc_mac"
    const val PC_USER = "pc_user"
    const val PC_PASS = "pc_pass"
    const val PC_CMD = "pc_cmd"
    const val TV_IP = "tv_ip"
    const val TV_MAC = "tv_mac"
    const val PS5_IP = "ps5_ip"
    const val PS5_CRED = "ps5_cred"
    const val TV_PAIRED = "tv_paired"
    const val THEME = "theme"

    val all = listOf(PC_IP, PC_MAC, PC_USER, PC_PASS, PC_CMD, TV_IP, TV_MAC, PS5_IP, PS5_CRED, THEME)
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("evkumanda", Context.MODE_PRIVATE)

    fun get(key: String): String {
        val def = when (key) {
            K.PC_CMD -> "shutdown /s /t 0"
            K.THEME -> "kirmizi"
            else -> ""
        }
        return sp.getString(key, def) ?: def
    }

    fun put(key: String, value: String) {
        sp.edit().putString(key, value.trim()).apply()
    }
}

/** Wake-on-LAN sihirli paketi */
object Wol {
    fun send(mac: String, ip: String?) {
        val clean = mac.replace(":", "").replace("-", "").trim()
        require(clean.length == 12) { "MAC adresi geçersiz (12 hane olmalı)" }
        val m = ByteArray(6) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val pkt = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) pkt[i] = 0xFF.toByte()
        for (i in 0 until 16) System.arraycopy(m, 0, pkt, 6 + i * 6, 6)

        val targets = mutableListOf("255.255.255.255")
        if (!ip.isNullOrBlank()) {
            val p = ip.split(".")
            if (p.size == 4) targets.add("${p[0]}.${p[1]}.${p[2]}.255")
        }
        DatagramSocket().use { s ->
            s.broadcast = true
            for (t in targets) {
                for (port in intArrayOf(9, 7)) {
                    s.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName(t), port))
                }
            }
        }
    }
}

/** Basit erişilebilirlik kontrolü */
object Net {
    fun up(host: String, ports: IntArray): Boolean {
        for (p in ports) {
            try {
                Socket().use { it.connect(InetSocketAddress(host, p), 500) }
                return true
            } catch (e: ConnectException) {
                val msg = e.message ?: ""
                if (msg.contains("refused", ignoreCase = true) || msg.contains("ECONNREFUSED")) return true
            } catch (e: Exception) {
                // bu port için yanıt yok, sıradakini dene
            }
        }
        return false
    }
}

/** PC: SSH ile kapatma */
object Pc {
    fun shutdown(host: String, user: String, pass: String, cmd: String) {
        require(host.isNotBlank()) { "PC IP adresi boş" }
        require(user.isNotBlank()) { "SSH kullanıcı adı boş" }
        val session = JSch().getSession(user, host, 22)
        session.setPassword(pass)
        session.setConfig("StrictHostKeyChecking", "no")
        session.connect(8000)
        try {
            val ch = session.openChannel("exec") as ChannelExec
            ch.setCommand(cmd)
            ch.connect(5000)
            Thread.sleep(1200)
            ch.disconnect()
        } finally {
            session.disconnect()
        }
    }
}

/**
 * PS5: Dinlenme modundan uyandırma (UDP 9302).
 * Kimlik bilgisi (user-credential) bir kez Remote Play eşleştirmesiyle alınmalı.
 * Dinlenme moduna alma bu protokolle yapılamaz; HDMI-CEC (HDMI Cihaz Bağlantısı) kullanılır.
 */
object Ps5 {
    fun wake(ip: String, credential: String) {
        require(ip.isNotBlank()) { "PS5 IP adresi boş" }
        require(credential.isNotBlank()) { "PS5 user-credential boş" }
        val text = "WAKEUP * HTTP/1.1\n" +
            "client-type:a\n" +
            "auth-type:C\n" +
            "model:m\n" +
            "app-type:r\n" +
            "user-credential:$credential\n" +
            "device-discovery-protocol-version:00030010\n"
        val data = text.toByteArray(Charsets.US_ASCII)
        DatagramSocket().use { s ->
            s.broadcast = true
            val parts = ip.split(".")
            val targets = mutableListOf(ip)
            if (parts.size == 4) targets.add("${parts[0]}.${parts[1]}.${parts[2]}.255")
            for (t in targets) {
                // Konsol bazen ilk paketi kaçırır, üç kez gönder
                repeat(3) {
                    s.send(DatagramPacket(data, data.size, InetAddress.getByName(t), 9302))
                    Thread.sleep(150)
                }
            }
        }
    }
}
