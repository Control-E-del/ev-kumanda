package com.evkumanda.app

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

private fun b(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }

private fun varint(n: Int): ByteArray {
    val o = ByteArrayOutputStream()
    var v = n
    while (v > 0x7F) {
        o.write((v and 0x7F) or 0x80)
        v = v ushr 7
    }
    o.write(v)
    return o.toByteArray()
}

private fun readVarint(m: ByteArray, start: Int): Int {
    var r = 0
    var shift = 0
    var i = start
    while (i < m.size) {
        val x = m[i].toInt() and 0xFF
        r = r or ((x and 0x7F) shl shift)
        if ((x and 0x80) == 0) break
        shift += 7
        i++
    }
    return r
}

/**
 * Google TV / Android TV "Remote Protocol v2" istemcisi
 * (Google TV mobil uygulamasının kullandığı protokol).
 * Port 6467: eşleştirme, port 6466: komutlar.
 */
class TvRemote(private val ctx: Context) {

    companion object {
        const val KEY_POWER = 26
        const val KEY_HOME = 3
        const val KEY_BACK = 4
        const val KEY_UP = 19
        const val KEY_DOWN = 20
        const val KEY_LEFT = 21
        const val KEY_RIGHT = 22
        const val KEY_OK = 23
        const val KEY_VOL_UP = 24
        const val KEY_VOL_DOWN = 25
        const val KEY_MUTE = 164
        const val KEY_PLAY_PAUSE = 85
    }

    private val ksFile = File(ctx.filesDir, "tv_client.p12")
    private val pw = "evkumanda".toCharArray()
    private val header = b(8, 2, 16, 200, 1)

    @Volatile private var sock: SSLSocket? = null
    @Volatile private var connectedHost: String = ""
    @Volatile private var ready = false
    @Volatile var poweredOn: Boolean? = null
        private set

    /** TV'de şu an açık olan uygulamanın paket adı (bağlıyken güncellenir) */
    @Volatile var currentApp: String? = null
        private set

    private var pairSock: SSLSocket? = null
    private var serverKey: RSAPublicKey? = null

    // ---------- Sertifika / TLS ----------

    private fun keyStore(): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        if (ksFile.exists()) {
            FileInputStream(ksFile).use { ks.load(it, pw) }
            return ks
        }
        ks.load(null, null)
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val name = X500Name("CN=atvremote")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(now),
            Date(now - 86_400_000L),
            Date(now + 3650L * 86_400_000L),
            name,
            kp.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        ks.setKeyEntry("client", kp.private, pw, arrayOf(cert))
        FileOutputStream(ksFile).use { ks.store(it, pw) }
        return ks
    }

    private fun sslContext(): SSLContext {
        val ks = keyStore()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, pw)
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val c = SSLContext.getInstance("TLS")
        c.init(kmf.keyManagers, arrayOf<TrustManager>(trustAll), null)
        return c
    }

    private fun clientPublicKey(): RSAPublicKey {
        val cert = keyStore().getCertificate("client") as X509Certificate
        return cert.publicKey as RSAPublicKey
    }

    // ---------- Çerçeveleme ----------

    private fun send(o: OutputStream, payload: ByteArray) {
        synchronized(o) {
            o.write(varint(payload.size))
            o.write(payload)
            o.flush()
        }
    }

    private fun readMsg(i: InputStream): ByteArray {
        var len = 0
        var shift = 0
        while (true) {
            val x = i.read()
            if (x < 0) throw EOFException()
            len = len or ((x and 0x7F) shl shift)
            if ((x and 0x80) == 0) break
            shift += 7
        }
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val r = i.read(buf, off, len - off)
            if (r < 0) throw EOFException()
            off += r
        }
        return buf
    }

    private fun stripped(v: BigInteger): ByteArray {
        val a = v.toByteArray()
        return if (a.size > 1 && a[0] == 0.toByte()) a.copyOfRange(1, a.size) else a
    }

    // ---------- Eşleştirme (port 6467) ----------

    /** TV ekranında 6 haneli kod çıkar. Ardından [finishPairing] çağrılır. */
    fun startPairing(host: String) {
        closePairing()
        val s = sslContext().socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, 6467), 5000)
        s.soTimeout = 10_000
        s.startHandshake()
        pairSock = s
        serverKey = s.session.peerCertificates[0].publicKey as RSAPublicKey
        val o = s.outputStream
        val i = s.inputStream

        val service = "evkumanda".toByteArray()
        val client = "Ev Kumanda".toByteArray()
        val req = b(10, service.size) + service + b(18, client.size) + client
        send(o, header + b(82) + varint(req.size) + req)
        readMsg(i) // pairing request ack

        send(o, header + b(162, 1, 8, 10, 4, 8, 3, 16, 6, 24, 1)) // options
        readMsg(i)

        send(o, header + b(242, 1, 8, 10, 4, 8, 3, 16, 6, 16, 1)) // configuration
        readMsg(i) // configuration ack, TV kodu gösterir
        s.soTimeout = 0
    }

    fun finishPairing(codeRaw: String): Boolean {
        val s = pairSock ?: throw IOException("Önce eşleştirmeyi başlat")
        val code = codeRaw.trim().uppercase()
        require(code.length == 6) { "Kod 6 haneli olmalı" }
        val cp = clientPublicKey()
        val sp = serverKey ?: throw IOException("Sunucu anahtarı yok")

        val md = MessageDigest.getInstance("SHA-256")
        md.update(stripped(cp.modulus))
        md.update(stripped(cp.publicExponent))
        md.update(stripped(sp.modulus))
        md.update(stripped(sp.publicExponent))
        val tail = code.substring(2)
        md.update(ByteArray(2) { tail.substring(it * 2, it * 2 + 2).toInt(16).toByte() })
        val hash = md.digest()

        s.soTimeout = 8000
        send(s.outputStream, header + b(194, 2, 34, 10, 32) + hash)
        val resp = readMsg(s.inputStream)
        closePairing()
        val ok = resp.size >= 4 && resp[3] == 200.toByte()
        return ok
    }

    private fun closePairing() {
        try { pairSock?.close() } catch (_: Exception) {}
        pairSock = null
    }

    // ---------- Komut oturumu (port 6466) ----------

    @Synchronized
    private fun ensureConnected(host: String) {
        val cur = sock
        if (cur != null && !cur.isClosed && ready && connectedHost == host) return
        closeRemote()

        val s = sslContext().socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, 6466), 4000)
        s.startHandshake()
        sock = s
        connectedHost = host
        ready = false
        poweredOn = null

        val latch = CountDownLatch(1)
        val o = s.outputStream

        thread(isDaemon = true) {
            var cfgSent = false
            var activeSent = false
            try {
                val inp = s.inputStream
                while (!s.isClosed) {
                    val m = readMsg(inp)
                    if (m.isEmpty()) continue
                    val tag = m[0].toInt() and 0xFF
                    when {
                        tag == 10 && !cfgSent -> {
                            cfgSent = true
                            send(o, clientConfig())
                        }
                        tag == 18 && !activeSent -> {
                            activeSent = true
                            send(o, b(18, 3, 8, 238, 4))
                            ready = true
                            latch.countDown()
                        }
                        tag == 66 -> {
                            val v = if (m.size > 3) readVarint(m, 3) else 0
                            val inner = b(8) + varint(v)
                            send(o, b(74) + varint(inner.size) + inner)
                        }
                        tag == 162 && m.size > 5 && (m[1].toInt() and 0xFF) == 1 && m[3].toInt() == 10 -> {
                            val plen = readVarint(m, 4)
                            if (5 + plen <= m.size) currentApp = String(m, 5, plen, Charsets.UTF_8)
                        }
                        tag == 194 && m.size >= 5 && (m[1].toInt() and 0xFF) == 2 -> {
                            poweredOn = m[m.size - 1].toInt() != 0
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                ready = false
                try { s.close() } catch (_: Exception) {}
            }
        }

        if (!latch.await(6, TimeUnit.SECONDS)) {
            closeRemote()
            throw IOException("TV yanıt vermedi (eşleştirdin mi? TV açık mı?)")
        }
    }

    private fun clientConfig(): ByteArray {
        val s1 = "androidtv-remote".toByteArray()
        val s2 = "1.0.0".toByteArray()
        val device = b(24, 1) + b(34, 1, 49) + b(42, s1.size) + s1 + b(50, s2.size) + s2
        val inner = b(8, 238, 4) + b(18, device.size) + device
        return b(10) + varint(inner.size) + inner
    }

    private fun closeRemote() {
        try { sock?.close() } catch (_: Exception) {}
        sock = null
        ready = false
    }

    /** Bağlantıyı açık tutar, durum ve açık uygulama bilgisini günceller */
    fun connect(host: String) {
        ensureConnected(host)
    }

    fun key(host: String, code: Int) {
        ensureConnected(host)
        val o = sock?.outputStream ?: throw IOException("Bağlantı yok")
        for (dir in intArrayOf(1, 2)) {
            val inner = b(8) + varint(code) + b(16, dir)
            send(o, b(82) + varint(inner.size) + inner)
            Thread.sleep(40)
        }
    }

    /** Bir uygulama/bağlantı açar (ör. https://www.youtube.com) */
    fun openLink(host: String, url: String) {
        ensureConnected(host)
        val o = sock?.outputStream ?: throw IOException("Bağlantı yok")
        val u = url.toByteArray()
        val inner = b(10) + varint(u.size) + u
        send(o, b(210, 5) + varint(inner.size) + inner)
    }

    fun powerOn(host: String, mac: String) {
        var wolSent = false
        if (mac.isNotBlank()) {
            Wol.send(mac, host)
            wolSent = true
        }
        try {
            ensureConnected(host)
            Thread.sleep(700)
            if (poweredOn == false) key(host, KEY_POWER)
        } catch (e: Exception) {
            if (!wolSent) throw e
        }
    }

    fun powerOff(host: String) {
        ensureConnected(host)
        Thread.sleep(700)
        if (poweredOn != false) key(host, KEY_POWER)
    }
}
