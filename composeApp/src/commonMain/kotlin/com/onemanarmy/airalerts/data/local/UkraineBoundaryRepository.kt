package com.onemanarmy.airalerts.data.local

import com.onemanarmy.airalerts.domain.model.AdminLevel
import com.onemanarmy.airalerts.domain.model.RegionPolygon
import com.onemanarmy.airalerts.domain.model.UkraineMapBoundaries
import com.onemanarmy.airalerts.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Loads and caches Ukraine's detailed country border and 3-tier administrative boundaries
 * (27 oblasts/special-status cities, 137 raions, 1,472 hromadas) from the bundled UAB1 binary asset.
 */
class UkraineBoundaryRepository {

    companion object {
        private const val BINARY_ASSET_PATH = "files/ukraine_admin.bin"
        private const val MAGIC_HEADER = "UAB1"
    }

    private val loadMutex = Mutex()
    private val _boundariesFlow = MutableStateFlow<UkraineMapBoundaries?>(null)
    val boundariesFlow: StateFlow<UkraineMapBoundaries?> = _boundariesFlow.asStateFlow()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadIfNeeded(): UkraineMapBoundaries? {
        _boundariesFlow.value?.let { return it }
        return loadMutex.withLock {
            _boundariesFlow.value?.let { return@withLock it }
            val parsed = withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = Res.readBytes(BINARY_ASSET_PATH)
                    parseBinary(bytes)
                }.getOrNull()
            }
            if (parsed != null) {
                _boundariesFlow.value = parsed
            }
            parsed
        }
    }

    /**
     * Pure-Kotlin big-endian parser for the UAB1 binary boundary format.
     */
    internal fun parseBinary(bytes: ByteArray): UkraineMapBoundaries {
        require(bytes.size >= 20) { "UAB1 binary payload is too small (${bytes.size} bytes)" }
        val magic = bytes.decodeToString(0, 4)
        require(magic == MAGIC_HEADER) { "Invalid UAB1 header: $magic" }

        val cursor = ByteCursor(bytes, startPos = 4)

        val countryRingCount = cursor.readInt()
        val countryRings = ArrayList<FloatArray>(countryRingCount)
        repeat(countryRingCount) {
            countryRings.add(cursor.readRing())
        }

        val oblastCount = cursor.readInt()
        val raionCount = cursor.readInt()
        val hromadaCount = cursor.readInt()

        val oblasts = ArrayList<RegionPolygon>(oblastCount)
        repeat(oblastCount) { index ->
            oblasts.add(cursor.readRegion(AdminLevel.OBLAST, index))
        }

        val raions = ArrayList<RegionPolygon>(raionCount)
        repeat(raionCount) { index ->
            raions.add(cursor.readRegion(AdminLevel.RAION, index))
        }

        val hromadas = ArrayList<RegionPolygon>(hromadaCount)
        repeat(hromadaCount) { index ->
            hromadas.add(cursor.readRegion(AdminLevel.HROMADA, index))
        }

        return UkraineMapBoundaries(
            countryRings = countryRings,
            oblasts = oblasts,
            raions = raions,
            hromadas = hromadas
        )
    }

    private class ByteCursor(
        private val bytes: ByteArray,
        startPos: Int = 0
    ) {
        private var pos = startPos

        fun readShort(): Int {
            val hi = bytes[pos++].toInt() and 0xFF
            val lo = bytes[pos++].toInt() and 0xFF
            return (hi shl 8) or lo
        }

        fun readInt(): Int {
            val b0 = bytes[pos++].toInt() and 0xFF
            val b1 = bytes[pos++].toInt() and 0xFF
            val b2 = bytes[pos++].toInt() and 0xFF
            val b3 = bytes[pos++].toInt() and 0xFF
            return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }

        fun readFloat(): Float = Float.fromBits(readInt())

        fun readString(): String {
            val len = readShort()
            if (len == 0) return ""
            val value = bytes.decodeToString(pos, pos + len)
            pos += len
            return value
        }

        fun readRing(): FloatArray {
            val vertexCount = readInt()
            val coords = FloatArray(vertexCount * 2)
            for (i in coords.indices) {
                coords[i] = readFloat()
            }
            return coords
        }

        fun readRegion(level: AdminLevel, index: Int): RegionPolygon {
            val name = readString()
            val raionName = readString()
            val oblastName = readString()
            val minLat = readFloat()
            val maxLat = readFloat()
            val minLon = readFloat()
            val maxLon = readFloat()
            val centerLat = readFloat()
            val centerLon = readFloat()
            val ringCount = readShort()
            val rings = ArrayList<FloatArray>(ringCount)
            repeat(ringCount) {
                rings.add(readRing())
            }
            return RegionPolygon(
                id = "${level.name.lowercase()}_$index",
                name = name,
                raionName = raionName,
                oblastName = oblastName,
                level = level,
                minLat = minLat,
                maxLat = maxLat,
                minLon = minLon,
                maxLon = maxLon,
                centerLat = centerLat,
                centerLon = centerLon,
                rings = rings
            )
        }
    }
}
