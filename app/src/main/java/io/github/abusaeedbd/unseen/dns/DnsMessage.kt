package io.github.abusaeedbd.unseen.dns

/**
 * Just enough DNS (RFC 1035) to read the question of a query and to write a
 * "blocked" answer for it. Pure Kotlin, no Android classes, so it is unit-tested.
 */
object DnsMessage {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    private const val HEADER = 12
    private const val BLOCKED_TTL = 60

    /** The first question of a DNS query. [questionEnd] is the byte index just after it. */
    data class Query(val id: Int, val name: String, val type: Int, val questionEnd: Int)

    /** Returns the query's first question, or null if this is not a well-formed standard query. */
    fun parseQuery(msg: ByteArray, length: Int = msg.size): Query? {
        if (length < HEADER || length > msg.size) return null
        val flags = u16(msg, 2)
        if (flags and 0x8000 != 0) return null          // QR=1: a response, not a query
        if ((flags shr 11) and 0xF != 0) return null    // only standard queries (opcode 0)
        if (u16(msg, 4) < 1) return null                // no question

        val name = StringBuilder()
        var p = HEADER
        while (true) {
            if (p >= length) return null
            val labelLength = msg[p].toInt() and 0xFF
            p++
            if (labelLength == 0) break
            if (labelLength and 0xC0 != 0) return null   // compression is not used in questions
            if (p + labelLength > length) return null
            if (name.isNotEmpty()) name.append('.')
            for (i in p until p + labelLength) name.append(Char(msg[i].toInt() and 0xFF))
            p += labelLength
            if (name.length > 253) return null
        }
        if (p + 4 > length) return null
        val type = u16(msg, p)
        return Query(id = u16(msg, 0), name = name.toString().lowercase(), type = type, questionEnd = p + 4)
    }

    /**
     * A response that answers [query] with 0.0.0.0 (A) or :: (AAAA), or with no
     * records for other types, so the app cannot reach the tracker.
     */
    fun blockedResponse(msg: ByteArray, query: Query): ByteArray {
        val questionLength = query.questionEnd - HEADER
        val rdLength = when (query.type) {
            TYPE_A -> 4
            TYPE_AAAA -> 16
            else -> 0
        }
        val answerLength = if (rdLength > 0) 12 + rdLength else 0
        val out = ByteArray(HEADER + questionLength + answerLength)

        put16(out, 0, query.id)
        // QR=1, keep opcode and RD from the query; RA=1, RCODE=0 (no error)
        out[2] = (0x80 or (msg[2].toInt() and 0x79)).toByte()
        out[3] = 0x80.toByte()
        put16(out, 4, 1)                                 // QDCOUNT
        put16(out, 6, if (rdLength > 0) 1 else 0)        // ANCOUNT
        // NSCOUNT and ARCOUNT stay 0

        System.arraycopy(msg, HEADER, out, HEADER, questionLength)

        if (rdLength > 0) {
            var p = HEADER + questionLength
            put16(out, p, 0xC00C); p += 2                // name: pointer to the question
            put16(out, p, query.type); p += 2
            put16(out, p, 1); p += 2                     // class IN
            put16(out, p, 0); p += 2                     // TTL, high 16 bits
            put16(out, p, BLOCKED_TTL); p += 2           // TTL, low 16 bits
            put16(out, p, rdLength)                      // address bytes stay 0
        }
        return out
    }

    internal fun u16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    internal fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v shr 8).toByte()
        b[i + 1] = v.toByte()
    }
}
