package io.privacydroid.data.source

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

data class ProcNetEntry(
    val uid: Int,
    val remoteIp: String,
    val remotePort: Int,
    val state: String,
    val protocol: String  // "tcp" veya "udp"
)

/**
 * /proc/net/tcp ve /proc/net/udp dosyalarını okur.
 *
 * Android 10+ (API 29): Bu dosyalara erişim kısıtlı olabilir.
 * Okuma başarısız olursa boş liste döner — uygulama çökmez.
 *
 * TCP state kodları: 01=ESTABLISHED, 02=SYN_SENT, 0A=LISTEN, vs.
 * Yalnızca ESTABLISHED (01) bağlantılar raporlanır.
 */
@Singleton
class ProcNetReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val pm = context.packageManager

    fun readActiveConnections(): List<ProcNetEntry> {
        val results = mutableListOf<ProcNetEntry>()
        results += readProcFile("/proc/net/tcp", "tcp")
        results += readProcFile("/proc/net/tcp6", "tcp6")
        return results.distinctBy { "${it.uid}_${it.remoteIp}_${it.remotePort}" }
    }

    private fun readProcFile(path: String, protocol: String): List<ProcNetEntry> {
        return try {
            val lines = File(path).readLines()
            lines.drop(1)  // başlık satırını atla
                .mapNotNull { parseLine(it, protocol) }
                .filter { it.state == "01" }  // yalnızca ESTABLISHED
        } catch (e: Exception) {
            // Android 10+ erişim engeli veya dosya yok — beklenen durum
            Timber.d("/proc/net/$protocol okunamadı: ${e.message}")
            emptyList()
        }
    }

    private fun parseLine(line: String, protocol: String): ProcNetEntry? {
        return try {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 8) return null

            val remoteHex = parts[2]  // hex IP:Port
            val state = parts[3]
            val uid = parts[7].toIntOrNull() ?: return null

            val (remoteIp, remotePort) = parseAddress(remoteHex) ?: return null

            ProcNetEntry(uid = uid, remoteIp = remoteIp, remotePort = remotePort,
                state = state, protocol = protocol)
        } catch (_: Exception) { null }
    }

    private fun parseAddress(hexAddress: String): Pair<String, Int>? {
        return try {
            val (hexIp, hexPort) = hexAddress.split(":").let {
                if (it.size != 2) return null
                it[0] to it[1]
            }
            val port = hexPort.toInt(16)

            // IPv4: little-endian hex string (8 chars)
            val ip = if (hexIp.length == 8) {
                val bytes = ByteArray(4) { i ->
                    hexIp.substring((3 - i) * 2, (3 - i) * 2 + 2).toInt(16).toByte()
                }
                InetAddress.getByAddress(bytes).hostAddress ?: return null
            } else {
                // IPv6 — simplified: skip non-private addresses
                return null
            }

            Pair(ip, port)
        } catch (_: Exception) { null }
    }

    /**
     * UID'den paket adlarına çevirir.
     * Birden fazla uygulama aynı UID'yi paylaşabilir (shared UID).
     */
    fun packagesForUid(uid: Int): List<String> {
        return try {
            pm.getPackagesForUid(uid)?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}
