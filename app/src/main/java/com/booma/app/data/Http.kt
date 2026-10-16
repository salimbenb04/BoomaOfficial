package com.booma.app.data

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * One HTTP client for the whole app, as Metrolist does. Connections to YouTube stay open and are
 * reused (HTTP/2), so after the first request there is no new TLS handshake: lists, song lookups and
 * the start of every song are noticeably quicker than opening a fresh connection each time.
 */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
