package io.github.abusaeedbd.unseen.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsTest {

    /** A standard query for [name], like the one Android's resolver sends. */
    private fun query(name: String, type: Int, id: Int = 0x1234): ByteArray {
        val out = ArrayList<Byte>()
        fun u16(v: Int) { out.add((v shr 8).toByte()); out.add(v.toByte()) }
        u16(id); u16(0x0100); u16(1); u16(0); u16(0); u16(0)
        for (label in name.split('.')) {
            out.add(label.length.toByte())
            label.forEach { out.add(it.code.toByte()) }
        }
        out.add(0)
        u16(type); u16(1)
        return out.toByteArray()
    }

    @Test
    fun parsesQuestion() {
        val q = DnsMessage.parseQuery(query("App-Measurement.com", DnsMessage.TYPE_A))
        assertNotNull(q)
        assertEquals(0x1234, q!!.id)
        assertEquals("app-measurement.com", q.name)
        assertEquals(DnsMessage.TYPE_A, q.type)
    }

    @Test
    fun rejectsResponsesAndJunk() {
        val response = query("example.com", DnsMessage.TYPE_A).also { it[2] = 0x81.toByte() }
        assertNull(DnsMessage.parseQuery(response))
        assertNull(DnsMessage.parseQuery(ByteArray(5)))
        val truncated = query("example.com", DnsMessage.TYPE_A)
        assertNull(DnsMessage.parseQuery(truncated, truncated.size - 6))
    }

    @Test
    fun blockedAnswerForA() {
        val msg = query("ads.example.com", DnsMessage.TYPE_A)
        val q = DnsMessage.parseQuery(msg)!!
        val r = DnsMessage.blockedResponse(msg, q)
        assertEquals(0x12, r[0].toInt() and 0xFF)
        assertEquals(0x34, r[1].toInt() and 0xFF)
        assertTrue(r[2].toInt() and 0x80 != 0)            // it is a response
        assertEquals(1, r[2].toInt() and 0x01)             // RD copied
        assertEquals(0, r[3].toInt() and 0x0F)             // no error
        assertEquals(1, DnsMessage.u16(r, 6))              // one answer
        assertEquals(msg.size + 16, r.size)
        val rdLength = DnsMessage.u16(r, r.size - 6)
        assertEquals(4, rdLength)
        assertArrayEquals(ByteArray(4), r.copyOfRange(r.size - 4, r.size))   // 0.0.0.0
        // the question is echoed unchanged
        assertArrayEquals(msg.copyOfRange(12, msg.size), r.copyOfRange(12, msg.size))
    }

    @Test
    fun blockedAnswerForAaaaAndOthers() {
        val aaaa = query("ads.example.com", DnsMessage.TYPE_AAAA)
        val r6 = DnsMessage.blockedResponse(aaaa, DnsMessage.parseQuery(aaaa)!!)
        assertEquals(aaaa.size + 28, r6.size)
        assertEquals(16, DnsMessage.u16(r6, r6.size - 18))

        val https = query("ads.example.com", 65)
        val r = DnsMessage.blockedResponse(https, DnsMessage.parseQuery(https)!!)
        assertEquals(0, DnsMessage.u16(r, 6))              // no answers
        assertEquals(https.size, r.size)
    }

    @Test
    fun ipUdpRoundTrip() {
        val payload = query("example.com", DnsMessage.TYPE_A)
        val src = byteArrayOf(10, 111, 222.toByte(), 2)
        val dst = byteArrayOf(10, 111, 222.toByte(), 1)
        val packet = IpUdpPacket.build(src, dst, 40000, 53, payload)
        assertEquals(0, IpUdpPacket.checksum(packet, 0, 20))  // header checksum is valid
        val parsed = IpUdpPacket.parse(packet, packet.size)!!
        assertArrayEquals(src, parsed.srcIp)
        assertArrayEquals(dst, parsed.dstIp)
        assertEquals(40000, parsed.srcPort)
        assertEquals(53, parsed.dstPort)
        assertArrayEquals(
            payload,
            packet.copyOfRange(parsed.payloadOffset, parsed.payloadOffset + parsed.payloadLength),
        )
    }

    @Test
    fun ignoresNonUdp() {
        val packet = IpUdpPacket.build(ByteArray(4), ByteArray(4), 1, 2, ByteArray(10))
        packet[9] = 6 // TCP
        assertNull(IpUdpPacket.parse(packet, packet.size))
    }

    @Test
    fun matchesDomainsAndSubdomains() {
        val m = DomainMatcher.parse(
            sequenceOf("# comment", "", "doubleclick.net", "0.0.0.0 appsflyer.com", "api.segment.io  # inline")
        )
        assertEquals(3, m.size)
        assertTrue(m.matches("doubleclick.net"))
        assertTrue(m.matches("stats.g.DoubleClick.net."))
        assertTrue(m.matches("t.appsflyer.com"))
        assertTrue(m.matches("api.segment.io"))
        assertFalse(m.matches("segment.io"))
        assertFalse(m.matches("notdoubleclick.net"))
        assertFalse(m.matches("google.com"))
    }
}

class LookalikeTest {
    @Test
    fun spotsFakeBkashAndNagad() {
        assertTrue(Lookalike.isScam("bkash-bonus-offer.xyz"))
        assertTrue(Lookalike.isScam("login.bkash.com.verify-account.top"))
        assertTrue(Lookalike.isScam("nagad-gift.top"))
        assertFalse(Lookalike.isScam("www.bkash.com"))
        assertFalse(Lookalike.isScam("bkash.com"))
        assertFalse(Lookalike.isScam("nagad.com.bd"))
        assertFalse(Lookalike.isScam("nagadhat.com"))     // "nagad" alone is a normal word
        assertFalse(Lookalike.isScam("google.com"))
    }

    @Test
    fun skipsHostsFileNoise() {
        val m = DomainMatcher.parse(
            sequenceOf("127.0.0.1 localhost", "0.0.0.0 0.0.0.0", "::1 ip6-localhost", "0.0.0.0 ads.example.com")
        )
        assertEquals(1, m.size)
        assertTrue(m.matches("x.ads.example.com"))
    }
}
