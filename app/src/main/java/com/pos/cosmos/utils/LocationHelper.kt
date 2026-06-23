package com.pos.cosmos.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class UserLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

object LocationHelper {
    private const val TAG = "LocationHelper"

    /**
     * Get the current location using FusedLocationProviderClient
     * Returns UserLocation with 0,0 if location cannot be obtained
     */
    suspend fun getCurrentLocation(context: Context): UserLocation {
        // Check if location permissions are granted
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            return UserLocation(0.0, 0.0)
        }

        // Check if location services are enabled
        if (!isLocationEnabled(context)) {
            Log.w(TAG, "Location services are disabled")
            return UserLocation(0.0, 0.0)
        }

        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            // Try to get last known location first (faster)
            val lastLocation = getLastKnownLocation(fusedLocationClient, context)
            if (lastLocation != null) {
                Log.d(TAG, "Got last known location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                return UserLocation(lastLocation.latitude, lastLocation.longitude)
            }

            // If no last location, request current location
            val currentLocation = requestCurrentLocation(fusedLocationClient, context)
            if (currentLocation != null) {
                Log.d(TAG, "Got current location: ${currentLocation.latitude}, ${currentLocation.longitude}")
                return UserLocation(currentLocation.latitude, currentLocation.longitude)
            }

            Log.w(TAG, "Could not get location")
            UserLocation(0.0, 0.0)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}", e)
            UserLocation(0.0, 0.0)
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private suspend fun getLastKnownLocation(
        fusedLocationClient: FusedLocationProviderClient,
        context: Context
    ): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (!hasLocationPermission(context)) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get last location: ${e.message}")
                    continuation.resume(null)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting last location: ${e.message}")
            continuation.resume(null)
        }
    }

    private suspend fun requestCurrentLocation(
        fusedLocationClient: FusedLocationProviderClient,
        context: Context
    ): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (!hasLocationPermission(context)) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                continuation.resume(location)
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get current location: ${e.message}")
                continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting current location: ${e.message}")
            continuation.resume(null)
        }
    }
}
