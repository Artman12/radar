package com.onemanarmy.radar.data.local

import com.onemanarmy.radar.domain.model.AdminLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UkraineBoundaryParserTest {

    @Test
    fun parseBinary_decodesUab1PayloadCorrectly() {
        val bytes = buildSampleUab1Binary()
        val repo = UkraineBoundaryRepository()

        val result = repo.parseBinary(bytes)

        assertEquals(1, result.countryRings.size)
        assertEquals(6, result.countryRings[0].size)
        assertEquals(48.0f, result.countryRings[0][0], 0.0001f)
        assertEquals(30.0f, result.countryRings[0][1], 0.0001f)

        assertEquals(1, result.oblasts.size)
        assertEquals("Київська область", result.oblasts[0].name)
        assertEquals(AdminLevel.OBLAST, result.oblasts[0].level)

        assertEquals(1, result.raions.size)
        assertEquals("Броварський район", result.raions[0].name)
        assertEquals("Київська область", result.raions[0].oblastName)
        assertEquals(AdminLevel.RAION, result.raions[0].level)

        assertEquals(1, result.hromadas.size)
        assertEquals("Броварська", result.hromadas[0].name)
        assertEquals("Броварський район", result.hromadas[0].raionName)
        assertEquals("Київська область", result.hromadas[0].oblastName)
        assertEquals(AdminLevel.HROMADA, result.hromadas[0].level)
        assertEquals(50.51f, result.hromadas[0].centerLat, 0.001f)
        assertEquals(30.79f, result.hromadas[0].centerLon, 0.001f)
    }

    @Test
    fun parseBinary_rejectsInvalidMagicHeader() {
        val repo = UkraineBoundaryRepository()
        assertFailsWith<IllegalArgumentException> {
            repo.parseBinary(ByteArray(32) { 0 })
        }
    }

    private fun buildSampleUab1Binary(): ByteArray {
        val out = mutableListOf<Byte>()
        fun writeAscii(s: String) = s.encodeToByteArray().forEach { out.add(it) }
        fun writeShort(v: Int) {
            out.add(((v ushr 8) and 0xFF).toByte())
            out.add((v and 0xFF).toByte())
        }
        fun writeInt(v: Int) {
            out.add(((v ushr 24) and 0xFF).toByte())
            out.add(((v ushr 16) and 0xFF).toByte())
            out.add(((v ushr 8) and 0xFF).toByte())
            out.add((v and 0xFF).toByte())
        }
        fun writeFloat(f: Float) = writeInt(f.toBits())
        fun writeString(s: String) {
            val utf8 = s.encodeToByteArray()
            writeShort(utf8.size)
            utf8.forEach { out.add(it) }
        }
        fun writeRing(coords: FloatArray) {
            writeInt(coords.size / 2)
            coords.forEach { writeFloat(it) }
        }
        fun writeRegion(
            name: String,
            raion: String,
            oblast: String,
            centerLat: Float,
            centerLon: Float,
            coords: FloatArray
        ) {
            writeString(name)
            writeString(raion)
            writeString(oblast)
            writeFloat(centerLat - 0.1f)
            writeFloat(centerLat + 0.1f)
            writeFloat(centerLon - 0.1f)
            writeFloat(centerLon + 0.1f)
            writeFloat(centerLat)
            writeFloat(centerLon)
            writeShort(1)
            writeRing(coords)
        }

        writeAscii("UAB1")
        // 1 country ring
        writeInt(1)
        writeRing(floatArrayOf(48.0f, 30.0f, 49.0f, 30.0f, 49.0f, 31.0f))

        // Counts: 1 oblast, 1 raion, 1 hromada
        writeInt(1)
        writeInt(1)
        writeInt(1)

        val poly = floatArrayOf(50.4f, 30.6f, 50.6f, 30.6f, 50.6f, 30.9f)
        writeRegion("Київська область", "", "Київська область", 50.2f, 30.6f, poly)
        writeRegion("Броварський район", "Броварський район", "Київська область", 50.5f, 30.8f, poly)
        writeRegion("Броварська", "Броварський район", "Київська область", 50.51f, 30.79f, poly)

        return out.toByteArray()
    }
}
