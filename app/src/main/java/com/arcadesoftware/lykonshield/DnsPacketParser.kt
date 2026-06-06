package com.arcadesoftware.lykonshield

import java.nio.ByteBuffer

object DnsPacketParser {

    const val DNS_TYPE_A = 1
    const val DNS_TYPE_AAAA = 28

    private const val DNS_HEADER_LEN = 12
    private const val IPV4_HEADER_MIN_LEN = 20
    private const val UDP_HEADER_LEN = 8
    private const val BLOCKED_TTL = 300

    // Parses the domain name from a raw UDP DNS packet.
    // Returns null if the packet is not a valid DNS query.
    fun parseQueryDomain(packet: ByteBuffer, ipHeaderLen: Int, udpHeaderLen: Int): String? {
        val dnsStart = ipHeaderLen + udpHeaderLen
        if (ipHeaderLen < IPV4_HEADER_MIN_LEN || udpHeaderLen < UDP_HEADER_LEN) return null
        if (packet.limit() < dnsStart + DNS_HEADER_LEN) return null

        packet.position(dnsStart)
        val transactionId = packet.short
        val flags = packet.short.toInt() and 0xFFFF
        val isQuery = (flags and 0x8000) == 0
        if (!isQuery) return null

        val qdCount = packet.short.toInt() and 0xFFFF // Questions count
        if (qdCount <= 0) return null

        // Skip ANCount (2), NSCount (2), ARCount (2)
        val skipTarget = packet.position() + 6
        if (skipTarget > packet.limit()) return null
        packet.position(skipTarget)

        return parseDomainName(packet)
    }

    // Parses the QTYPE field after the QNAME in the DNS question section.
    // Returns DNS_TYPE_A (1), DNS_TYPE_AAAA (28), or 0 for unknown/unsupported types.
    fun getQueryType(packet: ByteBuffer, ipHeaderLen: Int, udpHeaderLen: Int): Int {
        val dnsStart = ipHeaderLen + udpHeaderLen
        if (ipHeaderLen < IPV4_HEADER_MIN_LEN || udpHeaderLen < UDP_HEADER_LEN) return 0
        if (packet.limit() < dnsStart + DNS_HEADER_LEN) return 0

        packet.position(dnsStart)
        val transactionId = packet.short
        val flags = packet.short.toInt() and 0xFFFF
        val isQuery = (flags and 0x8000) == 0
        if (!isQuery) return 0

        val qdCount = packet.short.toInt() and 0xFFFF
        if (qdCount <= 0) return 0

        // Skip ANCount (2), NSCount (2), ARCount (2)
        val skipTarget = packet.position() + 6
        if (skipTarget > packet.limit()) return 0
        packet.position(skipTarget)

        // Skip past QNAME to reach QTYPE
        if (!skipDomainName(packet)) return 0

        // Read QTYPE (2 bytes) and QCLASS (2 bytes)
        if (packet.remaining() < 4) return 0
        val qtype = packet.short.toInt() and 0xFFFF

        return qtype
    }

    // Builds a DNS A record response returning 0.0.0.0 for the queried domain.
    // Silently sinks connections instead of causing NXDOMAIN retries.
    fun buildBlockedAResponse(
        requestPacket: ByteArray,
        requestLen: Int,
        ipHeaderLen: Int,
        udpHeaderLen: Int
    ): ByteArray? {
        return buildBlockedResponse(requestPacket, requestLen, ipHeaderLen, udpHeaderLen, DNS_TYPE_A)
    }

    // Builds a DNS AAAA record response returning :: (all zeros) for the queried domain.
    // Silently sinks connections instead of causing NXDOMAIN retries.
    fun buildBlockedAAAAResponse(
        requestPacket: ByteArray,
        requestLen: Int,
        ipHeaderLen: Int,
        udpHeaderLen: Int
    ): ByteArray? {
        return buildBlockedResponse(requestPacket, requestLen, ipHeaderLen, udpHeaderLen, DNS_TYPE_AAAA)
    }

    // Builds a DNS NXDOMAIN (Name Error) response packet
    fun buildNxDomainResponse(
        requestPacket: ByteArray,
        requestLen: Int,
        ipHeaderLen: Int,
        udpHeaderLen: Int
    ): ByteArray {
        val dnsStart = ipHeaderLen + udpHeaderLen

        // The response will be the same size as the request, we just swap headers and set flags.
        val response = requestPacket.copyOf(requestLen)
        val responseBuf = ByteBuffer.wrap(response)

        // 1. Swap Source and Destination IPs in IPv4 Header
        val srcIp = ByteArray(4)
        val destIp = ByteArray(4)
        System.arraycopy(requestPacket, 12, destIp, 0, 4) // Request source IP
        System.arraycopy(requestPacket, 16, srcIp, 0, 4)  // Request dest IP

        responseBuf.position(12)
        responseBuf.put(srcIp)   // Response source = original dest
        responseBuf.put(destIp)  // Response dest = original source

        // Recalculate IP Checksum
        responseBuf.putShort(10, 0) // Clear checksum
        val ipChecksum = calculateChecksum(response, 0, ipHeaderLen)
        responseBuf.putShort(10, ipChecksum)

        // 2. Swap Source and Destination Ports in UDP Header
        val srcPort = responseBuf.getShort(ipHeaderLen)
        val destPort = responseBuf.getShort(ipHeaderLen + 2)
        responseBuf.putShort(ipHeaderLen, destPort)   // Response source port = 53
        responseBuf.putShort(ipHeaderLen + 2, srcPort) // Response dest port = original source

        // Clear UDP Checksum (setting to 0 disables checksum verification in UDP IPv4)
        responseBuf.putShort(ipHeaderLen + 6, 0)

        // 3. Modify DNS Flags in Response:
        // Set QR=1 (response), AA=1, RA=1, RCODE=3 (NXDOMAIN). Flags = 0x8583
        responseBuf.position(dnsStart + 2)
        responseBuf.putShort(0x8583.toShort())

        return response
    }

    // ─── Private helpers ────────────────────────────────────────────────

    private fun buildBlockedResponse(
        requestPacket: ByteArray,
        requestLen: Int,
        ipHeaderLen: Int,
        udpHeaderLen: Int,
        queryType: Int
    ): ByteArray? {
        val dnsStart = ipHeaderLen + udpHeaderLen

        // Validate minimum packet length: IP + UDP + DNS header
        if (ipHeaderLen < IPV4_HEADER_MIN_LEN || udpHeaderLen < UDP_HEADER_LEN) return null
        if (requestLen < dnsStart + DNS_HEADER_LEN) return null

        // Determine the RDATA length based on the query type
        val rdataLen = when (queryType) {
            DNS_TYPE_A -> 4    // IPv4 address = 4 bytes
            DNS_TYPE_AAAA -> 16 // IPv6 address = 16 bytes
            else -> return null
        }

        // Calculate the end of the question section (QNAME + QTYPE + QCLASS).
        val questionStart = dnsStart + DNS_HEADER_LEN
        val questionEnd = findQuestionEnd(requestPacket, questionStart, requestLen)
        if (questionEnd < 0) return null

        // Answer section layout:
        //   Name pointer (2) + TYPE (2) + CLASS (2) + TTL (4) + RDLENGTH (2) + RDATA (rdataLen)
        val answerSectionLen = 2 + 2 + 2 + 4 + 2 + rdataLen
        val responseLen = questionEnd + answerSectionLen

        // Build the response buffer
        val response = ByteArray(responseLen)
        // Copy the original packet up through the question section
        System.arraycopy(requestPacket, 0, response, 0, questionEnd)
        val responseBuf = ByteBuffer.wrap(response)

        // 1. Update IP Total Length field (offset 2 in IPv4 header)
        responseBuf.putShort(2, responseLen.toShort())

        // 2. Swap Source and Destination IPs in IPv4 Header
        val srcIp = ByteArray(4)
        val destIp = ByteArray(4)
        System.arraycopy(requestPacket, 12, destIp, 0, 4) // Request source IP
        System.arraycopy(requestPacket, 16, srcIp, 0, 4)  // Request dest IP

        responseBuf.position(12)
        responseBuf.put(srcIp)   // Response source = original dest
        responseBuf.put(destIp)  // Response dest = original source

        // 3. Recalculate IP Checksum
        responseBuf.putShort(10, 0) // Clear checksum
        val ipChecksum = calculateChecksum(response, 0, ipHeaderLen)
        responseBuf.putShort(10, ipChecksum)

        // 4. Swap Source and Destination Ports in UDP Header
        val srcPort = responseBuf.getShort(ipHeaderLen)
        val destPort = responseBuf.getShort(ipHeaderLen + 2)
        responseBuf.putShort(ipHeaderLen, destPort)    // Response source port = 53
        responseBuf.putShort(ipHeaderLen + 2, srcPort) // Response dest port = original source

        // 5. Update UDP Length field
        val udpLen = responseLen - ipHeaderLen
        responseBuf.putShort(ipHeaderLen + 4, udpLen.toShort())

        // 6. Clear UDP Checksum (setting to 0 disables checksum verification in UDP IPv4)
        responseBuf.putShort(ipHeaderLen + 6, 0)

        // 7. Set DNS Flags: QR=1, AA=1, RA=1, RCODE=0 (no error) → 0x8580
        responseBuf.putShort(dnsStart + 2, 0x8580.toShort())

        // 8. Set ANCOUNT = 1
        responseBuf.putShort(dnsStart + 6, 1.toShort())

        // 9. Write the answer section
        responseBuf.position(questionEnd)

        // Name: compression pointer to QNAME at the start of the question section (offset 12)
        val qnameOffset = DNS_HEADER_LEN
        responseBuf.putShort((0xC000 or qnameOffset).toShort())

        // TYPE
        responseBuf.putShort(queryType.toShort())

        // CLASS = IN (1)
        responseBuf.putShort(1.toShort())

        // TTL = 300 seconds
        responseBuf.putInt(BLOCKED_TTL)

        // RDLENGTH
        responseBuf.putShort(rdataLen.toShort())

        // RDATA: all zeros (0.0.0.0 for A, :: for AAAA)
        for (i in 0 until rdataLen) {
            responseBuf.put(0)
        }

        return response
    }

    // Finds the byte offset immediately after the first question entry (QNAME + QTYPE + QCLASS).
    // Returns -1 if the question section is malformed.
    private fun findQuestionEnd(packet: ByteArray, questionStart: Int, packetLen: Int): Int {
        var pos = questionStart
        // Walk the QNAME labels
        while (pos < packetLen) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) {
                pos++ // skip the zero-length terminator
                break
            }
            // Compression pointers should not appear in query QNAME
            if ((labelLen and 0xC0) == 0xC0) return -1
            // Guard against oversized or malformed labels
            if (labelLen > 63) return -1
            pos += 1 + labelLen
        }
        // QTYPE (2) + QCLASS (2)
        pos += 4
        if (pos > packetLen) return -1
        return pos
    }

    // Advances the buffer position past a domain name (label sequence or compression pointer).
    // Returns false if the name is malformed or the buffer runs out of data.
    private fun skipDomainName(packet: ByteBuffer): Boolean {
        try {
            while (packet.hasRemaining()) {
                val labelLen = packet.get().toInt() and 0xFF
                if (labelLen == 0) {
                    return true
                }
                // Compression pointer – 2 bytes total; we already consumed the first.
                if ((labelLen and 0xC0) == 0xC0) {
                    if (!packet.hasRemaining()) return false
                    packet.get() // consume the second byte of the pointer
                    return true
                }
                if (labelLen > 63) return false
                if (packet.remaining() < labelLen) return false
                packet.position(packet.position() + labelLen)
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    private fun parseDomainName(packet: ByteBuffer): String? {
        val domain = StringBuilder()
        try {
            while (packet.hasRemaining()) {
                val labelLen = packet.get().toInt() and 0xFF
                if (labelLen == 0) {
                    break
                }
                // Handle basic compression pointers (0xC0) - shouldn't happen in query QNAME
                if ((labelLen and 0xC0) == 0xC0) {
                    return null
                }
                if (labelLen > 63) return null
                if (domain.isNotEmpty()) {
                    domain.append('.')
                }
                if (packet.remaining() < labelLen) return null
                val labelBytes = ByteArray(labelLen)
                packet.get(labelBytes)
                domain.append(String(labelBytes, Charsets.US_ASCII))
            }
            return domain.toString()
        } catch (e: Exception) {
            return null
        }
    }

    private fun calculateChecksum(buf: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        val end = offset + length - 1
        while (i < end) {
            val high = buf[i].toInt() and 0xFF
            val low = buf[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i == end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()).toShort()
    }
}
