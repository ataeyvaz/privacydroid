package io.privacydroid.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.privacydroid.MainActivity
import io.privacydroid.R
import io.privacydroid.data.local.dao.AllowedDomainDao
import io.privacydroid.data.local.dao.AppBlockingExceptionDao
import io.privacydroid.data.local.dao.BlockedDomainDao
import io.privacydroid.data.local.dao.TrackerConnectionDao
import io.privacydroid.data.local.entity.AllowedDomainEntity
import io.privacydroid.data.local.entity.BlockedDomainEntity
import io.privacydroid.data.local.entity.TrackerConnectionEntity
import io.privacydroid.data.model.BlockingMode
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.data.source.BlockingStats
import io.privacydroid.domain.model.classifyTrackerDomain
import io.privacydroid.util.DnsPacketParser
import io.privacydroid.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Yerel VPN servisi — DNS sorgularını yakalayarak bilinen tracker domain'lere
 * bağlantıları tespit eder ve loglar.
 *
 * Çalışma prensibi:
 *   1. Yalnızca 8.8.8.8/32 ve 8.8.4.4/32 trafiği TUN'dan geçirilir.
 *      Diğer tüm trafik doğrudan cihazın ağ yığınına gider — gecikme yok.
 *   2. Port 53 UDP paketleri yakalanır, domain ayrıştırılır.
 *   3. tracker_domains.txt listesiyle karşılaştırılır.
 *   4. Eşleşme varsa Room'a kaydedilir, eşleşmese de DNS iletilir.
 *   5. Gerçek DNS yanıtı 8.8.8.8'den alınır, TUN'a yazılarak app'e iletilir.
 *
 * Trafik dışarıya gönderilmez — her şey cihazda kalır.
 * Root gerektirmez — Android VpnService API (API 14+).
 */
@AndroidEntryPoint
class PrivacyVpnService : VpnService() {

    companion object {
        const val ACTION_START = "io.privacydroid.action.START_PRIVACY_VPN"
        const val ACTION_STOP  = "io.privacydroid.action.STOP_PRIVACY_VPN"

        private const val NOTIFICATION_ID = 9100
        private const val CHANNEL_ID = "vpn_service"
        private const val DNS_REAL_SERVER = "8.8.8.8"
        private const val DNS_FALLBACK_SERVER = "8.8.4.4"
        private const val DNS_PORT = 53
        private const val IP_PROTOCOL_UDP = 17
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5_000L
        private const val PACKET_BUFFER_SIZE = 65535

        fun startVpn(context: Context) {
            context.startForegroundService(
                Intent(context, PrivacyVpnService::class.java)
                    .apply { action = ACTION_START }
            )
        }

        fun stopVpn(context: Context) {
            context.startService(
                Intent(context, PrivacyVpnService::class.java)
                    .apply { action = ACTION_STOP }
            )
        }
    }

    @Inject lateinit var trackerConnectionDao: TrackerConnectionDao
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var blockedDomainDao: BlockedDomainDao
    @Inject lateinit var appBlockingExceptionDao: AppBlockingExceptionDao
    @Inject lateinit var allowedDomainDao: AllowedDomainDao
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var blockingStats: BlockingStats

    // İzin verilen domainleri saniyede yüzlerce kez yazmamak için: her benzersiz
    // domaini süreç ömrü boyunca YALNIZCA BİR KEZ allowed_domains'e yazarız.
    // Bu set, hangi domainlerin zaten yazıldığını bellekte tutar (UID çözümleme
    // gibi pahalı işlemleri de bu sayede sınırlarız).
    private val loggedAllowedDomains =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var retryCount = 0
    private var trackerDomains: Set<String> = emptySet()
    private var adDomains: Set<String> = emptySet()
    private var adWhitelist: Set<String> = emptySet()

    // Engelleme kararı paket başına çok sık verildiği için bu değerler bellekte
    // tutulur ve ayar/istisna değişince Flow ile güncellenir (DB sorgusu yok).
    @Volatile private var blockingMode: BlockingMode = BlockingMode.OFF
    @Volatile private var exceptionPackages: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        trackerDomains = loadDomainList("tracker_domains.txt")
        adDomains      = loadDomainList("ad_domains.txt")
        adWhitelist    = loadDomainList("ad_whitelist.txt")
        Timber.d(
            "PrivacyVpnService: ${trackerDomains.size} tracker, ${adDomains.size} reklam, " +
            "${adWhitelist.size} beyaz liste domain yüklendi"
        )

        // Engelleme modu ve uygulama istisnalarını canlı izle
        serviceScope.launch {
            settingsRepository.observeBlockingMode().collectLatest { mode ->
                blockingMode = mode
                Timber.d("PrivacyVpnService: engelleme modu = $mode")
            }
        }
        serviceScope.launch {
            appBlockingExceptionDao.observePackageNames().collectLatest { pkgs ->
                exceptionPackages = pkgs.toHashSet()
                Timber.d("PrivacyVpnService: ${pkgs.size} uygulama engellemeden hariç")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopVpnTunnel()
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                // Android 14+ (API 34+) foreground service tip doğrulaması başarısız
                // olursa (ör. eksik FGS izni) startForeground SecurityException fırlatır.
                // Bunu yakalamazsak tüm uygulama çöker. Servis tipi manifest'te
                // "specialUse" olarak tanımlı; yine de savunmacı davran.
                try {
                    startForeground(NOTIFICATION_ID, buildForegroundNotification())
                } catch (e: Exception) {
                    Timber.e(e, "PrivacyVpnService: foreground başlatılamadı — servis durduruluyor")
                    try { notificationHelper.sendVpnFailedNotification() } catch (_: Exception) {}
                    stopSelf()
                    return START_NOT_STICKY
                }
                retryCount = 0
                startVpnWithRetry()
                START_STICKY
            }
        }
    }

    // ── VPN tünel kurulumu ────────────────────────────────────────────────────

    private fun startVpnWithRetry() {
        serviceScope.launch {
            while (retryCount <= MAX_RETRIES && isActive) {
                try {
                    if (retryCount > 0) {
                        Timber.w("PrivacyVpnService: yeniden bağlanıyor (deneme $retryCount/$MAX_RETRIES)")
                        delay(RETRY_DELAY_MS)
                    }
                    startVpnTunnel()
                    return@launch  // Başarılı — döngüden çık
                } catch (e: Exception) {
                    Timber.e(e, "PrivacyVpnService: tünel kurulamadı (deneme $retryCount)")
                    retryCount++
                }
            }
            // MAX_RETRIES aşıldı — kullanıcıya bildir
            Timber.e("PrivacyVpnService: maksimum yeniden bağlanma denemesi aşıldı")
            try { notificationHelper.sendVpnFailedNotification() } catch (e: Exception) { }
            stopSelf()
        }
    }

    private fun startVpnTunnel() {
        val builder = Builder()
            .setSession("PrivacyDroid")
            .addAddress("10.0.0.1", 32)
            // DNS sunucularını VPN üzerinden kullan — böylece uygulamaların DNS
            // sorguları bu IP'lere gider ve TUN tarafından yakalanır.
            .addDnsServer(DNS_REAL_SERVER)
            .addDnsServer(DNS_FALLBACK_SERVER)
            .addDnsServer("1.1.1.1")
            // KRİTİK: YALNIZCA DNS sunucularına giden trafik TUN'dan geçer.
            // 0.0.0.0/0 (tam tünel) YOK — diğer tüm trafik (HTTP/HTTPS) doğrudan
            // cihazın ağına gider, internet kesilmez.
            .addRoute(DNS_REAL_SERVER, 32)
            .addRoute(DNS_FALLBACK_SERVER, 32)
            .addRoute("1.1.1.1", 32)
            // Blocking IO — okuma döngüsü paket gelene kadar bekler (busy-loop yok).
            .setBlocking(true)
            .setMtu(1500)

        // NOT: addDisallowedApplication(packageName) DENENDİ ve bu cihazda DNS iletme
        // soketinin oluşturulmasını "socket failed: EPERM" ile engelledi (kendi
        // uygulamamız VPN'den hariç tutulunca ağ erişimini kaybediyor). Bunun yerine
        // geri besleme döngüsü, iletme soketini protect() ile tünelden muaf tutarak
        // engelleniyor (forwardDns içinde) — bu yöntem güvenilir çalışıyor.

        val fd = builder.establish()
            ?: throw IllegalStateException("VPN izni alınamadı veya başka bir VPN aktif")

        stopVpnTunnel()  // Önceki varsa kapat
        vpnInterface = fd
        retryCount = 0

        Timber.i("PrivacyVpnService: VPN tüneli kuruldu, DNS yakalama başladı")
        processPackets(fd)
    }

    private fun stopVpnTunnel() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Timber.e(e, "PrivacyVpnService: tünel kapatılamadı")
        }
    }

    // ── Paket okuma döngüsü ───────────────────────────────────────────────────

    private fun processPackets(fd: ParcelFileDescriptor) {
        serviceScope.launch {
            val input = FileInputStream(fd.fileDescriptor)
            val output = FileOutputStream(fd.fileDescriptor)
            val buffer = ByteArray(PACKET_BUFFER_SIZE)

            try {
                while (isActive && vpnInterface != null) {
                    val length = try {
                        input.read(buffer)
                    } catch (e: Exception) {
                        Timber.w("PrivacyVpnService: paket okunamadı: ${e.message}")
                        break
                    }
                    if (length <= 0) continue
                    handlePacket(buffer, length, output)
                }
            } finally {
                try { input.close() } catch (_: Exception) {}
                try { output.close() } catch (_: Exception) {}
            }

            // Döngü dışına çıkıldıysa (tünel kapandı) yeniden dene
            if (isActive && vpnInterface != null) {
                retryCount++
                startVpnWithRetry()
            }
        }
    }

    // ── Tek paket işleme ─────────────────────────────────────────────────────

    private fun handlePacket(buffer: ByteArray, length: Int, output: FileOutputStream) {
        try {
            // Yalnızca IPv4 + UDP + hedef port 53 (DNS) paketlerini ele alıyoruz.
            if (length < 28) return
            if (((buffer[0].toInt() and 0xF0) ushr 4) != 4) return         // IPv4 değil
            val ihl = (buffer[0].toInt() and 0x0F) * 4
            if ((buffer[9].toInt() and 0xFF) != IP_PROTOCOL_UDP) return     // UDP değil
            if (length < ihl + 8) return
            val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or
                    (buffer[ihl + 3].toInt() and 0xFF)
            if (dstPort != DNS_PORT) return                                 // DNS değil

            val srcIp   = DnsPacketParser.getSourceIp(buffer)
            val dstIp   = DnsPacketParser.getDestIp(buffer)
            val srcPort = DnsPacketParser.getSourcePort(buffer)

            // DNS payload: IP başlığı + 8 byte UDP başlığından sonra
            val dnsOffset = ihl + 8
            val dnsLength = length - dnsOffset
            if (dnsLength <= 0) return
            val dnsPayload = buffer.copyOfRange(dnsOffset, dnsOffset + dnsLength)

            // Domain parse SADECE sınıflandırma/loglama içindir; parse edilemese
            // bile paket iletilir (DNS çözümlemesi asla bozulmaz).
            val domain = runCatching { DnsPacketParser.parseDnsQuery(buffer, length) }.getOrNull()

            // Karar verme + iletme ayrı coroutine'de — okuma döngüsü bloklanmaz.
            serviceScope.launch {
                decideAndHandle(domain, dnsPayload, srcIp, srcPort, dstIp, output)
            }
        } catch (e: Exception) {
            Timber.v("PrivacyVpnService: paket işlenemedi: ${e.message}")
        }
    }

    /**
     * Bir DNS sorgusu için engelleme/iletme kararını verir.
     *
     * Öncelik sırası (DEĞİŞTİRİLEMEZ):
     *   1. Beyaz liste (ad_whitelist.txt)  → HER ZAMAN ilet
     *   2. Uygulama istisnası (kullanıcı)  → HER ZAMAN ilet
     *   3. Reklam (BALANCED+) / tracker (AGGRESSIVE) → NXDOMAIN + logla
     *   4. Tracker (engellenmedi)          → ilet + tracker_connections'a logla
     *   5. Diğer / parse edilemeyen        → normal ilet
     */
    private suspend fun decideAndHandle(
        domain: String?,
        dnsPayload: ByteArray,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        output: FileOutputStream
    ) {
        val d = domain?.lowercase()
        val mode = blockingMode

        // 1. Beyaz liste her şeyden önce gelir — asla engellenmez.
        val whitelisted = d != null && matchesList(d, adWhitelist)
        val isAd      = d != null && !whitelisted && matchesList(d, adDomains)
        val isTracker = d != null && !whitelisted && matchesList(d, trackerDomains)

        val wantBlock = when (mode) {
            BlockingMode.OFF        -> false
            BlockingMode.BALANCED   -> isAd
            BlockingMode.AGGRESSIVE -> isAd || isTracker
        }

        // İzin verilen sorgunun nedeni — allowed_domains kaydı için.
        var allowReason = if (whitelisted) "WHITELIST" else "NORMAL"

        if (wantBlock) {
            // 2. Uygulama istisnası — bu uygulama için engelleme kapalıysa ilet.
            val uid = resolveUid(srcIp, srcPort, dstIp, DNS_PORT)
            val pkg = if (uid >= 0) getPackageNameForUid(uid) else null
            val isException = pkg != null && pkg in exceptionPackages

            if (!isException) {
                // 3. ENGELLE: NXDOMAIN döndür + kaydet.
                blockDomain(d!!, isAd, pkg, uid, dnsPayload, srcIp, srcPort, dstIp, output)
                return
            }
            allowReason = "USER_EXCEPTION"
            Timber.v("Engelleme istisnası ($pkg) — iletiliyor: $d")
        }

        // 4. Engellenmedi: tracker ise monitoring için tracker_connections'a logla.
        if (d != null && isTracker) {
            logTrackerConnection(domain!!, srcIp, srcPort, dstIp, DNS_PORT)
        }

        // 5. Normal ilet.
        forwardDns(dnsPayload, dstIp, srcIp, srcPort, output)
        blockingStats.recordAllowed()

        // 6. İzin verilen domaini (benzersizse) kaydet — "İzin Verilenler" sekmesi için.
        if (d != null) {
            logAllowedConnection(d, allowReason, srcIp, srcPort, dstIp)
        }
    }

    /**
     * İzin verilen bir DNS sorgusunu allowed_domains'e kaydeder.
     *
     * Saniyede yüzlerce izinli sorgu olabileceğinden, her benzersiz domain
     * süreç ömrü boyunca YALNIZCA BİR KEZ yazılır (bellekte [loggedAllowedDomains]
     * dedup). Böylece pahalı UID çözümlemesi ve DB yazımı sınırlı kalır.
     */
    private suspend fun logAllowedConnection(
        domain: String,
        allowReason: String,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray
    ) {
        // Bellekte zaten varsa hiçbir şey yapma — pahalı işlemleri atla.
        if (!loggedAllowedDomains.add(domain)) return
        try {
            // Süreç yeniden başladıysa DB'de zaten olabilir — tekrar yazma.
            if (allowedDomainDao.exists(domain)) return

            val uid = resolveUid(srcIp, srcPort, dstIp, DNS_PORT)
            val pkg = if (uid >= 0) getPackageNameForUid(uid) else null
            val appName = when {
                pkg != null -> appLabel(pkg)
                uid >= 0    -> "UID: $uid"
                else        -> "Bilinmeyen Uygulama"
            }
            allowedDomainDao.insert(
                AllowedDomainEntity(
                    domain      = domain,
                    packageName = pkg ?: if (uid >= 0) "uid:$uid" else "unknown",
                    appName     = appName,
                    allowReason = allowReason
                )
            )
        } catch (e: Exception) {
            Timber.v("allowed_domains kaydedilemedi: ${e.message}")
        }
    }

    /** NXDOMAIN yanıtını TUN'a yazar ve engelleme kaydını oluşturur. */
    private suspend fun blockDomain(
        domain: String,
        isAd: Boolean,
        pkg: String?,
        uid: Int,
        dnsPayload: ByteArray,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        output: FileOutputStream
    ) {
        // Uygulamaya NXDOMAIN yanıtı gönder — zaman aşımı yerine hızlı "yok" cevabı.
        try {
            val nxPayload = DnsPacketParser.buildNxDomainResponse(dnsPayload)
            val responsePacket = DnsPacketParser.buildUdpResponse(
                srcIp   = dstIp,      // DNS sunucusu kaynak
                dstIp   = srcIp,      // app hedef
                srcPort = DNS_PORT,
                dstPort = srcPort,
                payload = nxPayload
            )
            synchronized(output) { output.write(responsePacket) }
        } catch (e: Exception) {
            Timber.v("PrivacyVpnService: NXDOMAIN yazılamadı: ${e.message}")
        }

        // Engelleme kaydı — "neden açılmıyor?" sorusu için.
        val appName = when {
            pkg != null -> appLabel(pkg)
            uid >= 0    -> "UID: $uid"
            else        -> "Bilinmeyen Uygulama"
        }
        val type = if (isAd) "AD" else "TRACKER"
        try {
            blockedDomainDao.insert(
                BlockedDomainEntity(
                    domain      = domain,
                    packageName = pkg ?: if (uid >= 0) "uid:$uid" else "unknown",
                    appName     = appName,
                    blockType   = type
                )
            )
        } catch (e: Exception) {
            Timber.w("blocked_domains kaydedilemedi: ${e.message}")
        }
        Timber.i("DNS engellendi: $appName → $domain [$type]")
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    // ── DNS iletme ────────────────────────────────────────────────────────────

    private fun forwardDns(
        dnsPayload: ByteArray,
        originalDstIp: ByteArray,
        originalSrcIp: ByteArray,
        originalSrcPort: Int,
        output: FileOutputStream
    ) {
        try {
            val socket = DatagramSocket()
            protect(socket)  // VPN tünelinden muaf tut, gerçek ağa git

            val serverIp = InetAddress.getByAddress(originalDstIp)
            val sendPacket = DatagramPacket(dnsPayload, dnsPayload.size, serverIp, DNS_PORT)
            socket.soTimeout = 3000
            socket.send(sendPacket)

            val responseBuffer = ByteArray(PACKET_BUFFER_SIZE)
            val receivePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(receivePacket)
            socket.close()

            val responsePayload = receivePacket.data.copyOfRange(0, receivePacket.length)
            val responsePacket = DnsPacketParser.buildUdpResponse(
                srcIp   = originalDstIp,    // DNS sunucusu kaynak
                dstIp   = originalSrcIp,    // App hedef
                srcPort = DNS_PORT,
                dstPort = originalSrcPort,
                payload = responsePayload
            )
            synchronized(output) {
                output.write(responsePacket)
            }
        } catch (e: Exception) {
            Timber.v("PrivacyVpnService: DNS iletme başarısız: ${e.message}")
        }
    }

    // ── Tracker tespiti ───────────────────────────────────────────────────────

    private suspend fun logTrackerConnection(
        domain: String,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int
    ) {
        try {
            val uid = resolveUid(srcIp, srcPort, dstIp, dstPort)

            // UID → paket adı çözümleme (çok aşamalı):
            //   1. PackageManager.getPackagesForUid (en güvenilir)
            //   2. ActivityManager.getRunningAppProcesses (proc/net erişilemezse)
            //   3. Her ikisi de başarısızsa ham UID numarasını göster
            val packageName: String
            val appName: String
            val resolvedPackage = if (uid >= 0) getPackageNameForUid(uid) else null
            when {
                resolvedPackage != null -> {
                    packageName = resolvedPackage
                    appName = runCatching {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(resolvedPackage, 0)
                        ).toString()
                    }.getOrDefault(resolvedPackage)
                }
                uid >= 0 -> {
                    // Paket adı bulunamadı — ActivityManager ile process adını dene
                    val processName = getProcessNameForUid(uid)
                    if (processName != null) {
                        packageName = processName
                        appName = processName
                    } else {
                        // Son çare: ham UID numarasını göster, "Bilinmeyen Uygulama" yerine
                        packageName = "uid:$uid"
                        appName = "UID: $uid"
                    }
                }
                else -> {
                    packageName = "unknown"
                    appName = "Bilinmeyen Uygulama"
                }
            }

            val category = classifyTrackerDomain(domain)
            trackerConnectionDao.insert(
                TrackerConnectionEntity(
                    packageName = packageName,
                    appName     = appName,
                    domain      = domain,
                    category    = category.name,
                    bytesSent   = 0L
                )
            )
            Timber.i("VPN DNS tracker tespit edildi: $appName → $domain [$category]")
        } catch (e: Exception) {
            Timber.e(e, "Tracker bağlantısı kaydedilemedi: $domain")
        }
    }

    /**
     * Bir bağlantı tuple'ından bu bağlantıyı açan uygulamanın UID'sini çözer.
     *
     * Android 10+ (API 29+): ConnectivityManager.getConnectionOwnerUid() kullanılır —
     * VpnService'lerin trafiği uygulamaya eşlemesi için tasarlanmış RESMİ API'dir.
     * Root gerektirmez ve /proc/net kısıtlamasından etkilenmez.
     *
     * Android 9 ve altı (API < 29): /proc/net/udp fallback'i kullanılır.
     *
     * Çözülemezse -1 döner.
     */
    private fun resolveUid(srcIp: ByteArray, srcPort: Int, dstIp: ByteArray, dstPort: Int): Int {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE)
                        as android.net.ConnectivityManager
                // local = uygulamanın yerel ucu (kaynak), remote = DNS sunucusu (hedef)
                val local = java.net.InetSocketAddress(InetAddress.getByAddress(srcIp), srcPort)
                val remote = java.net.InetSocketAddress(InetAddress.getByAddress(dstIp), dstPort)
                val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
                if (uid >= 0 && uid != android.os.Process.INVALID_UID) return uid
                Timber.v("getConnectionOwnerUid çözemedi (uid=$uid), proc/net deneniyor")
            } catch (e: Exception) {
                Timber.w("getConnectionOwnerUid başarısız: ${e.message}")
            }
        }
        // Eski cihazlar veya getConnectionOwnerUid başarısız → /proc/net/udp
        return readUidForPort(srcPort)
    }

    /**
     * /proc/net/udp dosyasından kaynak porta göre UID okur.
     * Root gerektirmez; bazı OEM cihazlarda kısıtlı olabilir.
     * Okuma başarısız olursa -1 döner.
     */
    private fun readUidForPort(srcPort: Int): Int {
        val hexPort = "%04X".format(srcPort).uppercase()
        try {
            for (path in listOf("/proc/net/udp", "/proc/net/udp6")) {
                val file = File(path)
                if (!file.canRead()) continue
                val uid = file.readLines()
                    .map { it.trim().split("\\s+".toRegex()) }
                    .firstOrNull { parts ->
                        parts.size >= 8 &&
                        parts.getOrNull(1)?.substringAfter(":")?.uppercase()?.trim() == hexPort
                    }
                    ?.getOrNull(7)?.toIntOrNull()
                if (uid != null) return uid
            }
        } catch (e: Exception) { /* root yok veya dosya okunamıyor */ }
        return -1
    }

    /**
     * UID'den paket adını PackageManager üzerinden çözer.
     * Birden fazla uygulama aynı UID'yi paylaşabilir (shared UID) — ilki döner.
     * Bulunamazsa veya hata olursa null döner.
     */
    fun getPackageNameForUid(uid: Int): String? {
        return try {
            packageManager
                .getPackagesForUid(uid)
                ?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * PackageManager UID'yi çözemediğinde ActivityManager ile çalışan process'in
     * adını bulmayı dener.
     *
     * Not: Android 5.1+ (API 22+) güvenlik kısıtı nedeniyle getRunningAppProcesses
     * yalnızca çağıran uygulamanın kendi process'lerini döner. Bu yüzden bu yöntem
     * çoğu cihazda sınırlıdır — yine de hiç bilgi vermemekten iyidir.
     */
    private fun getProcessNameForUid(uid: Int): String? {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses
                ?.firstOrNull { it.uid == uid }
                ?.processName
        } catch (e: Exception) {
            null
        }
    }

    // ── Tracker domain listesi ────────────────────────────────────────────────

    private fun loadDomainList(fileName: String): Set<String> {
        return try {
            assets.open(fileName).bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toHashSet()
            }
        } catch (e: Exception) {
            Timber.e(e, "$fileName yüklenemedi")
            emptySet()
        }
    }

    /** Domain, listedeki bir kayıtla tam eşleşiyor mu ya da onun alt domaini mi? */
    private fun matchesList(domainLower: String, list: Set<String>): Boolean =
        list.any { domainLower == it || domainLower.endsWith(".$it") }

    // ── Foreground bildirimi ──────────────────────────────────────────────────

    private fun buildForegroundNotification(): Notification {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                manager.createNotificationChannel(
                    android.app.NotificationChannel(
                        CHANNEL_ID,
                        "VPN Servisi",
                        android.app.NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "PrivacyDroid VPN aktifken gösterilir"
                        setShowBadge(false)
                    }
                )
            }
        } catch (e: Exception) { }

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, PrivacyVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("🔬 PrivacyDroid VPN aktif")
            .setContentText("DNS sorguları izleniyor — trafik dışarıya gönderilmiyor")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Durdur",
                stopIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnTunnel()
        serviceScope.cancel()
    }
}
