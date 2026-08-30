package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.core.EmergencyRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ============================================================
// LOG TAGS
// ============================================================

private const val NETWORK_TAG = "NetworkMonitor"
private const val API_TAG = "CloudApi"
private const val RETROFIT_TAG = "RetrofitClient"


// ============================================================
// 1. NETWORK CONNECTIVITY MONITOR  (unchanged)
// ============================================================

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

    val isOnline: Flow<Boolean> = callbackFlow {

        Log.d(NETWORK_TAG, "================================")
        Log.d(NETWORK_TAG, "NetworkMonitor Flow STARTED")
        Log.d(NETWORK_TAG, "================================")

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                Log.d(NETWORK_TAG, ">>> NETWORK AVAILABLE: $network")

                val caps =
                    connectivityManager.getNetworkCapabilities(network)

                Log.d(
                    NETWORK_TAG,
                    "Available capabilities: $caps"
                )

                val result = trySend(true)

                if (result.isFailure) {
                    Log.e(
                        NETWORK_TAG,
                        "FAILED to emit true from onAvailable()",
                        result.exceptionOrNull()
                    )
                }
            }

            override fun onLost(network: Network) {
                Log.d(NETWORK_TAG, ">>> NETWORK LOST: $network")

                val result = trySend(false)

                if (result.isFailure) {
                    Log.e(
                        NETWORK_TAG,
                        "FAILED to emit false from onLost()",
                        result.exceptionOrNull()
                    )
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {

                val hasInternet =
                    networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )

                val validated =
                    networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )

                val wifi =
                    networkCapabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI
                    )

                val cellular =
                    networkCapabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_CELLULAR
                    )

                Log.d(
                    NETWORK_TAG,
                    """
                    >>> CAPABILITIES CHANGED
                    Network       = $network
                    Internet      = $hasInternet
                    Validated     = $validated
                    WiFi          = $wifi
                    Cellular      = $cellular
                    Capabilities  = $networkCapabilities
                    """.trimIndent()
                )
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: android.net.LinkProperties
            ) {
                Log.d(
                    NETWORK_TAG,
                    ">>> LINK PROPERTIES: $network | $linkProperties"
                )
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
            .build()

        Log.d(
            NETWORK_TAG,
            "Registering network callback..."
        )

        try {

            connectivityManager.registerNetworkCallback(
                request,
                callback
            )

            Log.d(
                NETWORK_TAG,
                "Network callback REGISTERED successfully"
            )

        } catch (e: Exception) {

            Log.e(
                NETWORK_TAG,
                "FAILED to register network callback",
                e
            )
        }

        // Check current network immediately
        val activeNetwork =
            connectivityManager.activeNetwork

        Log.d(
            NETWORK_TAG,
            "Active network = $activeNetwork"
        )

        if (activeNetwork != null) {

            val caps =
                connectivityManager.getNetworkCapabilities(
                    activeNetwork
                )

            Log.d(
                NETWORK_TAG,
                "Active network capabilities = $caps"
            )

            val hasInternet =
                caps?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                ) == true

            val validated =
                caps?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ) == true

            Log.d(
                NETWORK_TAG,
                "INITIAL CHECK | Internet=$hasInternet | Validated=$validated"
            )

            trySend(hasInternet)

        } else {

            Log.d(
                NETWORK_TAG,
                "INITIAL CHECK | No active network"
            )

            trySend(false)
        }

        awaitClose {

            Log.d(
                NETWORK_TAG,
                "NetworkMonitor Flow CLOSED"
            )

            try {

                connectivityManager.unregisterNetworkCallback(
                    callback
                )

                Log.d(
                    NETWORK_TAG,
                    "Network callback UNREGISTERED"
                )

            } catch (e: Exception) {

                Log.e(
                    NETWORK_TAG,
                    "Error unregistering network callback",
                    e
                )
            }
        }
    }
}

// ============================================================
// 2. RETROFIT API DEFINITION (unchanged)
// ============================================================

interface CloudApi {
    @POST("api/sync")
    suspend fun syncRequests(
        @Body requests: List<EmergencyRequest>
    ): retrofit2.Response<Unit>
}


// ============================================================
// 3. RETROFIT HTTP CLIENT  — now configurable + timeouts + logging
// ============================================================

object RetrofitClient {

    private const val PREFS_NAME = "meshlink_prefs"
    private const val KEY_SERVER_URL = "server_base_url"

    // Only used the very first time, before the user configures a real address.
    private const val DEFAULT_BASE_URL = "http://172.20.10.5:3000/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private var cachedBaseUrl: String? = null
    private var cachedApi: CloudApi? = null

    /** Call this from a Settings screen when the user enters/scans the backend's address. */
    fun setBaseUrl(context: Context, url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, normalized)
            .apply()
        // Invalidate cache so the next call rebuilds Retrofit with the new address.
        cachedApi = null
        cachedBaseUrl = null
        Log.d(RETROFIT_TAG, "Base URL updated to: $normalized")
    }

    fun getBaseUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun getApi(context: Context): CloudApi {
        val baseUrl = getBaseUrl(context)

        cachedApi?.let {
            if (cachedBaseUrl == baseUrl) return it
        }

        Log.d(RETROFIT_TAG, "Building Retrofit client for BASE_URL = $baseUrl")

        val logging = HttpLoggingInterceptor { message ->
            Log.d(RETROFIT_TAG, message)
        }.apply { level = HttpLoggingInterceptor.Level.BODY }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CloudApi::class.java)

        cachedBaseUrl = baseUrl
        cachedApi = api
        return api
    }
}


// ============================================================
// 4. SYNC HELPER — now takes a Context so it can resolve the right base URL
// ============================================================

suspend fun syncEmergencyRequests(
    context: Context,
    requests: List<EmergencyRequest>
) {
    Log.d(API_TAG, "Starting emergency request sync (${requests.size} request(s))")

    if (requests.isEmpty()) {
        Log.d(API_TAG, "No requests to synchronize")
        return
    }

    try {
        requests.forEachIndexed { index, request ->
            Log.d(API_TAG, "Request #${index + 1}: $request")
        }

        val response = RetrofitClient.getApi(context).syncRequests(requests)

        Log.d(API_TAG, "HTTP status code: ${response.code()} | successful=${response.isSuccessful}")

        if (response.isSuccessful) {
            Log.d(API_TAG, "SYNC SUCCESS — uploaded ${requests.size} request(s)")
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e(API_TAG, "SYNC FAILED — HTTP ${response.code()} — $errorBody")
        }

    } catch (e: HttpException) {
        Log.e(API_TAG, "HTTP exception while syncing", e)
    } catch (e: java.net.ConnectException) {
        Log.e(API_TAG, "Could not connect to backend at ${RetrofitClient.getBaseUrl(context)}", e)
    } catch (e: java.net.SocketTimeoutException) {
        Log.e(API_TAG, "Connection timed out", e)
    } catch (e: Exception) {
        Log.e(API_TAG, "Unexpected error while syncing requests", e)
    }
}