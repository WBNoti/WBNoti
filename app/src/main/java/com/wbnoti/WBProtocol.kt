package com.wbnoti

import java.util.UUID
import java.util.zip.CRC32

/**
 * BLE protocol constants and packet builders for WHOOP 4.0 and 5.0 bands.
 *
 * Protocol reverse-engineered by the Noop project (MIT licence):
 * https://github.com/suyashkumar/whoop
 *
 * WHOOP 4.0 packet layout (little-endian):
 *   [0xAA][len_lo][len_hi][crc8(len)][type=35][seq][cmd][...payload][crc32le(inner)]
 *
 * WHOOP 5.0 "puffin" frame layout:
 *   [0xAA][0x01][declLen_lo][declLen_hi][0x00][0x01][crc16modbus(head)][inner (4-byte aligned)][crc32le(inner)]
 *
 * Haptic commands:
 *   4.0 — RUN_HAPTICS_PATTERN (79): patternId=2 (graduated alarm), loopCount=number of pulses
 *   5.0 — RUN_HAPTIC_MAVERICK (0x13): payload bytes [0x01, 47, 152, 0...] trigger one haptic pulse
 */
object WBProtocol {
    // WHOOP 4.0 BLE UUIDs
    val SERVICE_UUID: UUID = UUID.fromString("61080001-8d6d-82b8-614a-1c8cb0f8dcc6")
    val CMD_TO_STRAP: UUID = UUID.fromString("61080002-8d6d-82b8-614a-1c8cb0f8dcc6")
    val CMD_FROM_STRAP: UUID = UUID.fromString("61080003-8d6d-82b8-614a-1c8cb0f8dcc6")

    // WHOOP 5.0 BLE UUIDs
    val SERVICE_UUID_5: UUID = UUID.fromString("fd4b0001-cce1-4033-93ce-002d5875f58a")
    val CMD_TO_STRAP_5: UUID = UUID.fromString("fd4b0002-cce1-4033-93ce-002d5875f58a")

    // Standard BLE Battery Service (exposed by both WHOOP 4.0 and 5.0)
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private const val FRAME_MAGIC: Byte = 0xAA.toByte()
    private const val PACKET_TYPE_COMMAND: Byte = 35

    private const val CMD_RUN_HAPTICS_PATTERN: Byte = 79      // WHOOP 4.0
    private const val CMD_RUN_HAPTIC_MAVERICK: Byte = 0x13    // WHOOP 5.0

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

    private fun crc32Le(data: ByteArray): ByteArray {
        val crc = CRC32().also { it.update(data) }.value
        return ByteArray(4) { i -> ((crc shr (i * 8)) and 0xFF).toByte() }
    }

    // WHOOP 4.0: send RUN_HAPTICS_PATTERN (79) with patternId=2, one pulse per call.
    // Multiple pulses are handled by buzzPattern() which calls buzz() in a loop.
    fun buildHapticPacket4(seq: Byte): ByteArray {
        val inner = byteArrayOf(
            PACKET_TYPE_COMMAND,
            seq,
            CMD_RUN_HAPTICS_PATTERN,
            2,                       // patternId — graduated alarm pattern
            1,                       // loopCount — always 1; pacing is done in buzzPattern()
            0, 0, 0
        )
        val lenBytes = byteArrayOf(
            (inner.size and 0xFF).toByte(),
            ((inner.size shr 8) and 0xFF).toByte()
        )
        return byteArrayOf(FRAME_MAGIC) + lenBytes + crc8(lenBytes) + inner + crc32Le(inner)
    }

    // WHOOP 5.0/MG: puffin frame with RUN_HAPTIC_MAVERICK (0x13)
    // Matches puffinCommandFrame() from NoopApp/noop exactly.
    fun buildHapticPacket5(seq: Byte): ByteArray {
        val hapticPayload = byteArrayOf(
            0x01,
            47, 152.toByte(),
            0, 0, 0, 0, 0, 0, 0, 0, 0
        )
        val inner0 = byteArrayOf(PACKET_TYPE_COMMAND, seq, CMD_RUN_HAPTIC_MAVERICK) +
            hapticPayload
        val pad = (4 - inner0.size % 4) % 4
        val inner = if (pad == 0) inner0 else inner0 + ByteArray(pad)

        val declLen = inner.size + 4
        // 6-byte head: [0xAA][0x01][declLen_lo][declLen_hi][0x00][0x01]
        val head = byteArrayOf(
            FRAME_MAGIC,
            0x01,
            (declLen and 0xFF).toByte(),
            ((declLen ushr 8) and 0xFF).toByte(),
            0x00,
            0x01
        )
        val c16 = crc16Modbus(head)
        val c32 = crc32Le(inner)

        return head +
            byteArrayOf((c16 and 0xFF).toByte(), ((c16 ushr 8) and 0xFF).toByte()) +
            inner +
            c32
    }

    private fun crc16Modbus(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x0001 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }
}
