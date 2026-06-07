package io.privacydroid.util

/**
 * IPv4/UDP DNS paketlerini ayrıştırır ve yeniden inşa eder.
 *
 * Desteklenen format: IPv4 + UDP + DNS (RFC 1035).
 * IPv6 desteği Faz 3+'a bırakıldı.
 * Root gerektirmez.
 */
object DnsPacketParser {

    private const val IP_PROTOCOL_UDP = 17
    const val DNS_PORT = 53

    /**
     * IPv4/UDP paketinden DNS sorgu domain adını ayrıştırır.
     *
     * @return domain adı (örn. "graph.facebook.com") veya geçersiz/yanıt paketiyse null
     */
    fun parseDnsQuery(packet: ByteArray, length: Int = packet.size): String? {
        if (length < 28) return null
        if (packet[0].toInt().and(0xF0).ushr(4) != 4) return null  // Sadece IPv4
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (length < ihl + 8) return null
        if (packet[9].toInt() and 0xFF != IP_PROTOCOL_UDP) return null
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != DNS_PORT) return null
        val dnsOffset = ihl + 8
        if (length < dnsOffset + 13) return null
        // QR bit = 0 → sorgu
        val qrBit = (packet[dnsOffset + 2].toInt() and 0xFF) ushr 7
        if (qrBit != 0) return null
        return parseDnsName(packet, dnsOffset + 12, length)
    }

    /**
     * DNS mesajındaki questions bölümünden domain adını ayrıştırır.
     * "graph" + "facebook" + "com" → "graph.facebook.com"
     */
    fun parseDnsName(packet: ByteArray, startOffset: Int, length: Int = packet.size): String? {
        val labels = mutableListOf<String>()
        var pos = startOffset
        var iterations = 0
        while (pos < length && iterations < 128) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) break
            if (labelLen and 0xC0 == 0xC0) {
                // Pointer compression — sorgularda nadiren görülür, mevcut label'ları döndür
                break
            }
            pos++
            if (pos + labelLen > length) return null
            labels.add(String(packet, pos, labelLen, Charsets.US_ASCII))
            pos += labelLen
            iterations++
        }
        return if (labels.isEmpty()) null else labels.joinToString(".")
    }

    /** IPv4 paketinden kaynak IP'yi 4-byte dizi olarak döner. */
    fun getSourceIp(packet: ByteArray): ByteArray =
        ByteArray(4).also { System.arraycopy(packet, 12, it, 0, 4) }

    /** IPv4 paketinden hedef IP'yi 4-byte dizi olarak döner. */
    fun getDestIp(packet: ByteArray): ByteArray =
        ByteArray(4).also { System.arraycopy(packet, 16, it, 0, 4) }

    /** UDP kaynak portunu döner. */
    fun getSourcePort(packet: ByteArray): Int {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        return ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
    }

    /**
     * Bir DNS sorgusundan NXDOMAIN (alan adı yok) yanıtı üretir.
     *
     * Sorgu mesajını kopyalar ve yalnızca header flag baytlarını değiştirir:
     *   byte[2] = 0x81 → QR=1 (yanıt), OPCODE=0, AA=0, TC=0, RD=1
     *   byte[3] = 0x83 → RA=1, Z=0, RCODE=3 (NXDOMAIN)
     * QDCOUNT korunur (soru echo'lanır); AN/NS/AR sayıları sorgudakiyle aynı kalır.
     * Reklam/tracker domainlerini "yok" göstererek engellemek için kullanılır.
     */
    fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size >= 4) {
            response[2] = 0x81.toByte()
            response[3] = 0x83.toByte()
        }
        return response
    }

    /** DNS yanıtını TUN arayüzüne geri yazmak için IPv4/UDP paketi inşa eder. */
    fun buildUdpResponse(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipLen = 20
        val udpLen = 8
        val total = ipLen + udpLen + payload.size
        val buf = ByteArray(total)

        buf[0] = 0x45.toByte()
        buf[2] = (total ushr 8).toByte(); buf[3] = total.toByte()
        buf[5] = 1.toByte()
        buf[6] = 0x40.toByte()
        buf[8] = 64
        buf[9] = IP_PROTOCOL_UDP.toByte()
        System.arraycopy(srcIp, 0, buf, 12, 4)
        System.arraycopy(dstIp, 0, buf, 16, 4)
        val checksum = ipChecksum(buf, 0, ipLen)
        buf[10] = (checksum ushr 8).toByte(); buf[11] = checksum.toByte()

        buf[ipLen + 0] = (srcPort ushr 8).toByte(); buf[ipLen + 1] = srcPort.toByte()
        buf[ipLen + 2] = (dstPort ushr 8).toByte(); buf[ipLen + 3] = dstPort.toByte()
        val uLen = udpLen + payload.size
        buf[ipLen + 4] = (uLen ushr 8).toByte(); buf[ipLen + 5] = uLen.toByte()

        System.arraycopy(payload, 0, buf, ipLen + udpLen, payload.size)
        return buf
    }

    private fun ipChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 != 0) sum += (buf[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }
}
