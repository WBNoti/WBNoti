package com.wbnoti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class WBProtocolTest {

    // ── WHOOP 4.0 packet ──────────────────────────────────────────────────────

    @Test
    fun `4_0 packet starts with frame magic 0xAA`() {
        val packet = WBProtocol.buildHapticPacket4(seq = 0)
        assertEquals(0xAA.toByte(), packet[0])
    }

    @Test
    fun `4_0 packet length bytes encode inner size`() {
        val packet = WBProtocol.buildHapticPacket4(seq = 0)
        // inner = [type, seq, cmd, patternId, loopCount, 0, 0, 0] = 8 bytes
        val expectedInnerLen = 8
        val lenLo = packet[1].toInt() and 0xFF
        val lenHi = packet[2].toInt() and 0xFF
        assertEquals(expectedInnerLen, lenLo or (lenHi shl 8))
    }

    @Test
    fun `4_0 packet length CRC8 is correct`() {
        val packet = WBProtocol.buildHapticPacket4(seq = 0)
        val lenBytes = byteArrayOf(packet[1], packet[2])
        val expected = crc8(lenBytes)
        assertEquals(expected, packet[3])
    }

    @Test
    fun `4_0 packet contains command opcode 79`() {
        val packet = WBProtocol.buildHapticPacket4(seq = 0)
        // inner starts at offset 4: [type=35, seq, cmd=79, ...]
        assertEquals(79.toByte(), packet[6])
    }

    @Test
    fun `4_0 packet patternId is 2`() {
        val packet = WBProtocol.buildHapticPacket4(seq = 0)
        assertEquals(2.toByte(), packet[7])
    }

    @Test
    fun `4_0 packet pulse byte is always 1`() {
        val packet = WBProtocol.buildHapticPacket4(seq = 0)
        assertEquals(1.toByte(), packet[8])
    }

    @Test
    fun `4_0 packet sequence number is written correctly`() {
        val packet = WBProtocol.buildHapticPacket4(seq = 42)
        assertEquals(42.toByte(), packet[5])
    }

    @Test
    fun `4_0 packet CRC32 covers inner bytes`() {
        val packet = WBProtocol.buildHapticPacket4(seq = 0)
        // total size: 1 (magic) + 2 (len) + 1 (crc8) + 8 (inner) + 4 (crc32) = 16
        assertEquals(16, packet.size)
        val inner = packet.slice(4..11).toByteArray()
        val expectedCrc = CRC32().also { it.update(inner) }.value
        val actualCrc = (0..3).fold(0L) { acc, i ->
            acc or ((packet[12 + i].toLong() and 0xFF) shl (i * 8))
        }
        assertEquals(expectedCrc, actualCrc)
    }

    @Test
    fun `4_0 packets with different seq numbers produce different bytes`() {
        val p1 = WBProtocol.buildHapticPacket4(seq = 1)
        val p2 = WBProtocol.buildHapticPacket4(seq = 2)
        assertTrue(!p1.contentEquals(p2))
    }

    // ── WHOOP 5.0 packet ──────────────────────────────────────────────────────

    @Test
    fun `5_0 packet starts with frame magic 0xAA`() {
        val packet = WBProtocol.buildHapticPacket5(seq = 0)
        assertEquals(0xAA.toByte(), packet[0])
    }

    @Test
    fun `5_0 packet second byte is 0x01`() {
        val packet = WBProtocol.buildHapticPacket5(seq = 0)
        assertEquals(0x01.toByte(), packet[1])
    }

    @Test
    fun `5_0 packet header bytes 4 and 5 are 0x00 and 0x01`() {
        val packet = WBProtocol.buildHapticPacket5(seq = 0)
        assertEquals(0x00.toByte(), packet[4])
        assertEquals(0x01.toByte(), packet[5])
    }

    @Test
    fun `5_0 packet contains haptic maverick opcode 0x13`() {
        val packet = WBProtocol.buildHapticPacket5(seq = 0)
        // head=6 bytes, crc16=2 bytes → inner starts at offset 8
        // inner = [type=35, seq, cmd=0x13, ...payload]
        assertEquals(0x13.toByte(), packet[10])
    }

    @Test
    fun `5_0 packet total length is a multiple of 4 plus fixed overhead`() {
        val packet = WBProtocol.buildHapticPacket5(seq = 0)
        // head(6) + crc16(2) + inner(4-byte aligned) + crc32(4)
        // inner content is 14 bytes → padded to 16 → total = 6+2+16+4 = 28
        assertEquals(28, packet.size)
    }

    @Test
    fun `5_0 packets with different seq numbers produce different bytes`() {
        val p1 = WBProtocol.buildHapticPacket5(seq = 1)
        val p2 = WBProtocol.buildHapticPacket5(seq = 2)
        assertTrue(!p1.contentEquals(p2))
    }

    // ── Helpers mirroring WBProtocol's private implementations ───────────────

    private fun crc8(data: ByteArray): Byte {
        var crc = 0
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) (crc shl 1) xor 0x07 else crc shl 1
                crc = crc and 0xFF
            }
        }
        return crc.toByte()
    }
}
