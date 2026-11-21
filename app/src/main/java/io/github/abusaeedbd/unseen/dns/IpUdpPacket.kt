package io.github.abusaeedbd.unseen.dns

/** Reads and writes IPv4 + UDP packets as they come out of and go back into the VPN interface. */
object IpUdpPacket {

    private const val PROTOCOL_UDP = 17

    class Udp(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payloadOffset: Int,
        val payloadLength: Int,
    )

    /** Returns the UDP packet in [buf], or null for anything else (TCP, IPv6, fragments, junk). */
    fun parse(buf: ByteArray, length: Int): Udp? {
        if (length < 28) return null
        val version = (buf[0].toInt() shr 4) and 0xF
        if (version != 4) return null
        val headerLength = (buf[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || length < headerLength + 8) return null
        if (buf[9].toInt() and 0xFF != PROTOCOL_UDP) return null
        if (DnsMessage.u16(buf, 6) and 0x3FFF != 0) return null   // fragmented
        val totalLength = minOf(DnsMessage.u16(buf, 2), length)
        val udpLength = DnsMessage.u16(buf, headerLength + 4)
        val payloadLength = minOf(udpLength, totalLength - headerLength) - 8
        if (payloadLength < 0) return null
        return Udp(
            srcIp = buf.copyOfRange(12, 16),
            dstIp = buf.copyOfRange(16, 20),
            srcPort = DnsMessage.u16(buf, headerLength),
            dstPort = DnsMessage.u16(buf, headerLength + 2),
            payloadOffset = headerLength + 8,
            payloadLength = payloadLength,
        )
    }

    /** Builds an IPv4 + UDP packet carrying [payload]. */
    fun build(
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        payload: ByteArray, payloadLength: Int = payload.size,
    ): ByteArray {
        val total = 20 + 8 + payloadLength
        val p = ByteArray(total)
        p[0] = 0x45                                      // IPv4, header 20 bytes
        DnsMessage.put16(p, 2, total)
        DnsMessage.put16(p, 6, 0x4000)                   // don't fragment
        p[8] = 64                                        // TTL
        p[9] = PROTOCOL_UDP.toByte()
        System.arraycopy(srcIp, 0, p, 12, 4)
        System.arraycopy(dstIp, 0, p, 16, 4)
        DnsMessage.put16(p, 10, checksum(p, 0, 20))

        DnsMessage.put16(p, 20, srcPort)
        DnsMessage.put16(p, 22, dstPort)
        DnsMessage.put16(p, 24, 8 + payloadLength)
        // UDP checksum 0 = "not computed", allowed for IPv4
        System.arraycopy(payload, 0, p, 28, payloadLength)
        return p
    }

    internal fun checksum(b: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i + 1 < offset + length) {
            sum += DnsMessage.u16(b, i)
            i += 2
        }
        if (i < offset + length) sum += (b[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }
}
