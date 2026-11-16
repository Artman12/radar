package com.onemanarmy.airalerts.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.onemanarmy.airalerts.data.remote.ResilientOkHttpProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.INetworkAvailablityCheck
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.TileDownloader
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream

object DarkMapTileSources {
    const val ESRI_DARK_GRAY_BASE_NAME = "EsriDarkGrayBase"
    const val ESRI_DARK_GRAY_REF_NAME = "EsriDarkGrayReference"

    val EsriDarkGrayBase: OnlineTileSourceBase = object : OnlineTileSourceBase(
        ESRI_DARK_GRAY_BASE_NAME,
        0,
        16,
        256,
        ".png",
        arrayOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/"
        ),
        "© Esri, HERE, Garmin, OpenStreetMap contributors"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$y/$x"
        }
    }

    val EsriDarkGrayReference: OnlineTileSourceBase = object : OnlineTileSourceBase(
        ESRI_DARK_GRAY_REF_NAME,
        0,
        16,
        256,
        ".png",
        arrayOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/"
        ),
        "© Esri, HERE, Garmin, OpenStreetMap contributors"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$y/$x"
        }
    }
}

/**
 * Custom osmdroid TileDownloader powered by OkHttp (with DoH DNS fallback).
 * Downloads base geography and transparent reference label tiles separately so labels
 * can be rendered above colored alert overlays.
 */
class OkHttpTileDownloader(
    private val okHttpClient: OkHttpClient
) : TileDownloader() {

    companion object {
        private const val TAG = "OkHttpTileDownloader"
    }

    override fun downloadTile(
        pMapTileIndex: Long,
        redirectCount: Int,
        targetUrl: String?,
        pFilesystemCache: IFilesystemCache?,
        pTileSource: OnlineTileSourceBase?
    ): Drawable? {
        if (pTileSource == null || targetUrl.isNullOrBlank()) return null

        val userAgent = Configuration.getInstance().userAgentValue
            ?.takeIf { it.isNotBlank() }
            ?: "Radar/1.0 (Android)"

        val bytes = fetchBytes(targetUrl, userAgent) ?: return null
        return cacheAndDecode(
            bytes = bytes,
            mapTileIndex = pMapTileIndex,
            filesystemCache = pFilesystemCache,
            tileSource = pTileSource
        )
    }

    private fun fetchBytes(url: String, userAgent: String): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "image/png,image/jpeg,image/*,*/*;q=0.8")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code != 404) {
                        Log.w(TAG, "Tile HTTP ${response.code} for $url")
                    }
                    return null
                }
                val bytes = response.body?.bytes() ?: return null
                bytes.takeIf { it.isNotEmpty() }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Tile download failed for $url: ${t.message}")
            null
        }
    }

    private fun cacheAndDecode(
        bytes: ByteArray,
        mapTileIndex: Long,
        filesystemCache: IFilesystemCache?,
        tileSource: OnlineTileSourceBase
    ): Drawable? {
        return try {
            val expirationTime = System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L
            if (filesystemCache != null) {
                runCatching {
                    ByteArrayInputStream(bytes).use { cacheStream ->
                        filesystemCache.saveFile(tileSource, mapTileIndex, cacheStream, expirationTime)
                    }
                }
            }
            ByteArrayInputStream(bytes).use { drawStream ->
                tileSource.getDrawable(drawStream)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Tile decode failed: ${t.message}")
            null
        }
    }
}

class ModernNetworkAvailabilityCheck(context: Context) : INetworkAvailablityCheck {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    @SuppressLint("MissingPermission")
    override fun getNetworkAvailable(): Boolean {
        val manager = cm ?: return true
        val active = manager.activeNetwork ?: return true
        val caps = manager.getNetworkCapabilities(active) ?: return true
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun getWiFiNetworkAvailable(): Boolean = getNetworkAvailable()
    override fun getCellularDataNetworkAvailable(): Boolean = getNetworkAvailable()

    @Deprecated("Deprecated in osmdroid")
    override fun getRouteToPathExists(hostAddress: Int): Boolean = true
}

class ResilientMapTileProvider(
    context: Context,
    tileSource: ITileSource
) : MapTileProviderBasic(
    org.osmdroid.tileprovider.util.SimpleRegisterReceiver(context.applicationContext),
    ModernNetworkAvailabilityCheck(context.applicationContext),
    tileSource,
    context.applicationContext,
    null
) {
    override fun createDownloaderProvider(
        aNetworkAvailablityCheck: INetworkAvailablityCheck?,
        pTileSource: ITileSource?
    ): MapTileDownloader {
        return MapTileDownloader(pTileSource, tileWriter, aNetworkAvailablityCheck).apply {
            setTileDownloader(OkHttpTileDownloader(ResilientOkHttpProvider.client))
        }
    }
}
