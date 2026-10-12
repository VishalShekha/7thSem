package com.example.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Gives every phone a stable ID that survives app restarts.
 * (Android does not allow apps to read the phone number or IMEI.)
 */
object DeviceIdentity {
    @SuppressLint("HardwareIds")
    fun getId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrBlank()) return androidId.take(8)

        // Fallback: generate once and persist it
        val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)
        val existing = prefs.getString("node_id", null)
        if (existing != null) return existing

        val fresh = UUID.randomUUID().toString().take(8)
        prefs.edit().putString("node_id", fresh).apply()
        return fresh
    }
}

/**
 * Fetches the phone's real GPS location. Returns null if permission is
 * missing, GPS is off, or no location could be obtained.
 */
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (!granted) return null

        return try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).awaitResult()
                ?: client.lastLocation.awaitResult()
        } catch (e: Exception) {
            null
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.resume(null) }
    }