package com.onemanarmy.airalerts.data.remote

import android.util.Log
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttpClient with fast coalesced resilient DNS resolution:
 * 1. Returns cached DNS entries immediately.
 * 2. Coalesces concurrent lookups per hostname so multiple threads never duplicate DNS timeouts or DoH requests.
 * 3. Tries Android system DNS with a strict 450ms timeout (healthy DNS resolves in <50ms; dead VPN DNS fails fast).
 * 4. Falls back to DNS-over-HTTPS (Cloudflare 1.1.1.1 / Google 8.8.8.8 by literal IP) and bypasses failing
 *    system DNS for 5 minutes so map tiles and API calls stay instant.
 */
object ResilientOkHttpProvider {

    private const val TAG = "ResilientDns"
    private const val CACHE_TTL_MS = 5 * 60 * 1000L
    private const val SYSTEM_DNS_TIMEOUT_MS = 450L
    private const val SYSTEM_DNS_COOLDOWN_MS = 5 * 60 * 1000L

    private data class CachedDns(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long
    )

    private val dnsCache = ConcurrentHashMap<String, CachedDns>()
    private val inFlightLookups = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()
    private val systemDnsExecutor = Executors.newCachedThreadPool()

    @Volatile
    private var bypassSystemDnsUntilMs: Long = 0L

    private val dohBootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .callTimeout(3500, TimeUnit.MILLISECONDS)
            .build()
    }

    private val resilientDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            parseIpv4Bytes(hostname)?.let { bytes ->
                return listOf(InetAddress.getByAddress(hostname, bytes))
            }

            val now = System.currentTimeMillis()
            dnsCache[hostname]?.let { cached ->
                if (cached.expiresAtMs > now && cached.addresses.isNotEmpty()) {
                    return cached.addresses
                }
            }

            // Coalesce concurrent lookups for the same hostname.
            val newFuture = CompletableFuture<List<InetAddress>>()
            val existingFuture = inFlightLookups.putIfAbsent(hostname, newFuture)
            if (existingFuture != null) {
                return try {
                    existingFuture.get(5, TimeUnit.SECONDS)
                } catch (t: Throwable) {
                    throw UnknownHostException("Unable to resolve host \"$hostname\"").apply { initCause(t) }
                }
            }

            try {
                val resolved = resolveUncached(hostname)
                newFuture.complete(resolved)
                return resolved
            } catch (t: Throwable) {
                newFuture.completeExceptionally(t)
                throw t
            } finally {
                inFlightLookups.remove(hostname, newFuture)
            }
        }
    }

    private fun resolveUncached(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        dnsCache[hostname]?.let { cached ->
            if (cached.expiresAtMs > now && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }
        }

        if (now >= bypassSystemDnsUntilMs) {
            val systemResult = lookupSystemWithTimeout(hostname)
            if (systemResult.isNotEmpty()) {
                dnsCache[hostname] = CachedDns(systemResult, now + CACHE_TTL_MS)
                return systemResult
            }
            bypassSystemDnsUntilMs = now + SYSTEM_DNS_COOLDOWN_MS
            Log.w(TAG, "System DNS failed/timed out for $hostname; switching to DoH fallback for 5m")
        }

        val dohAddresses = queryDoh("https://1.1.1.1/dns-query?name=$hostname&type=A", hostname)
            .ifEmpty { queryDoh("https://8.8.8.8/resolve?name=$hostname&type=A", hostname) }

        if (dohAddresses.isNotEmpty()) {
            Log.i(TAG, "DoH resolved $hostname -> ${dohAddresses.joinToString { it.hostAddress.orEmpty() }}")
            dnsCache[hostname] = CachedDns(dohAddresses, System.currentTimeMillis() + CACHE_TTL_MS)
            return dohAddresses
        }

        throw UnknownHostException("Unable to resolve host \"$hostname\" via system DNS or DoH")
    }

    fun prewarmDnsAsync(vararg hostnames: String) {
        for (host in hostnames) {
            systemDnsExecutor.execute {
                runCatching { resilientDns.lookup(host) }
            }
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(resilientDns)
            .addInterceptor { chain ->
                val req = chain.request()
                try {
                    chain.proceed(req)
                } catch (t: Throwable) {
                    Log.w("AirAlertsHttp", "${req.method} ${req.url} FAILED: ${t.javaClass.simpleName}: ${t.message}")
                    throw t
                }
            }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun lookupSystemWithTimeout(hostname: String): List<InetAddress> {
        val future = systemDnsExecutor.submit(
            Callable {
                try {
                    Dns.SYSTEM.lookup(hostname)
                } catch (_: Throwable) {
                    emptyList()
                }
            }
        )
        return try {
            future.get(SYSTEM_DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            future.cancel(true)
            emptyList()
        }
    }

    private fun queryDoh(url: String, hostname: String): List<InetAddress> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()
            dohBootstrapClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val answers = json.optJSONArray("Answer") ?: return emptyList()
                val result = mutableListOf<InetAddress>()
                for (i in 0 until answers.length()) {
                    val item = answers.optJSONObject(i) ?: continue
                    if (item.optInt("type") == 1) {
                        val ipStr = item.optString("data")
                        val bytes = parseIpv4Bytes(ipStr) ?: continue
                        result += InetAddress.getByAddress(hostname, bytes)
                    }
                }
                result
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DoH lookup failed for $hostname ($url): ${t.message}")
            emptyList()
        }
    }

    private fun parseIpv4Bytes(ip: String): ByteArray? {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for (i in 0 until 4) {
            val value = parts[i].toIntOrNull() ?: return null
            if (value !in 0..255) return null
            bytes[i] = value.toByte()
        }
        return bytes
    }
}
