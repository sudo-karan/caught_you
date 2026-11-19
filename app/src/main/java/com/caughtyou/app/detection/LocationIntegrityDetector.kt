package com.caughtyou.app.detection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Reproduces the location-spoofing signals that are visible in the supplied
 * reference application decompiled Java source:
 *
 * 1. BasActivity.isMockSettingsON(): development_settings_enabled OR h0.c0.
 * 2. f0.onLocationChanged(): h0.c0 = Location.isFromMockProvider().
 * 3. j0 fused-location callback: Location.isMock() on API 31+, otherwise
 *    Location.isFromMockProvider().
 *
 * It deliberately does NOT add root, emulator, proxy, USB, or generic integrity
 * checks. Those references were not established as part of the app's
 * location-spoofing decision path from the supplied source archive.
 *
 * One finite check is performed per app launch. Location updates are removed
 * as soon as the result is finalized.
 */
class LocationIntegrityDetector(private val context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val foundLocations = mutableListOf<Location>()
    private val reasons = linkedSetOf<String>()
    private var spoofedLocation: Location? = null
    private var developerOptionsEnabled = false
    private var verifiedFixReceived = false
    private var latestVerifiedLocation: Location? = null
    private var completed = false
    private var onComplete: ((DetectionReport) -> Unit)? = null
    private var fusedCancellation: CancellationTokenSource? = null
    private var finishAfterFirstFix: Runnable? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.i(TAG, "LocationManager fix received from provider=${location.provider}")
            // Match f0.onLocationChanged(): isFromMockProvider().
            inspectLocation(location, "GPS/network LocationManager", checkMockFlag = true, useModernMockApi = false)
            scheduleFinishAfterFix()
        }

        @Deprecated("Deprecated by Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun run(onResult: (DetectionReport) -> Unit) {
        onComplete = onResult
        Log.i(TAG, "Starting one-time reference application-matched location checks")

        developerOptionsEnabled = readDeveloperOptions()
        if (developerOptionsEnabled) {
            reasons += "Developer Options are enabled (reference application isDeveloperOptionEnabled / isMockSettingsON path)."
        }

        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission is not granted")
            finishWithoutLocationPermission()
            return
        }

        inspectLastKnownLocations()
        startLocationManagerUpdates()
        requestFusedCurrentLocation()

        // Do not monitor indefinitely. If a checked fix arrives, allow a short
        // window for the other source to report; otherwise stop after 12 seconds.
        handler.postDelayed(timeoutRunnable, MAX_WAIT_MS)
    }

    private fun readDeveloperOptions(): Boolean {
        return try {
            val enabled = Settings.Secure.getInt(
                appContext.contentResolver,
                "development_settings_enabled",
                0
            ) == 1
            Log.i(TAG, "reference application Developer Options setting enabled=$enabled")
            enabled
        } catch (exception: Exception) {
            // reference application's source uses a default value of 0 on read failure.
            Log.e(TAG, "Unable to read Developer Options setting", exception)
            false
        }
    }

    private fun inspectLastKnownLocations() {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                val last = locationManager.getLastKnownLocation(provider)
                if (last != null) {
                    Log.i(TAG, "Last-known location found for provider=$provider")
                    inspectLocation(
                        last,
                        "Last-known $provider provider",
                        checkMockFlag = false
                    )
                }
            } catch (exception: SecurityException) {
                Log.e(TAG, "Permission error reading last-known provider=$provider", exception)
            } catch (exception: Exception) {
                Log.e(TAG, "Could not read last-known provider=$provider", exception)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationManagerUpdates() {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    // The source app registers GPS/network LocationListener callbacks.
                    locationManager.requestLocationUpdates(
                        provider,
                        LOCATION_MIN_TIME_MS,
                        LOCATION_MIN_DISTANCE_METERS,
                        locationListener,
                        Looper.getMainLooper()
                    )
                    Log.i(TAG, "Registered finite launch-time listener for provider=$provider")
                }
            } catch (exception: SecurityException) {
                Log.e(TAG, "Permission error registering provider=$provider", exception)
            } catch (exception: Exception) {
                Log.e(TAG, "Could not register provider=$provider", exception)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFusedCurrentLocation() {
        try {
            fusedCancellation = CancellationTokenSource()
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                fusedCancellation!!.token
            ).addOnSuccessListener { location ->
                if (location != null && !completed) {
                    Log.i(TAG, "Fused current location received")
                    // Match j0: isMock() on API 31+, isFromMockProvider() before API 31.
                    inspectLocation(location, "Fused Location Provider", checkMockFlag = true, useModernMockApi = true)
                    scheduleFinishAfterFix()
                } else {
                    Log.i(TAG, "Fused provider returned no current location")
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Fused current-location request failed", exception)
            }
        } catch (exception: SecurityException) {
            Log.e(TAG, "Location permission error requesting fused location", exception)
        } catch (exception: Exception) {
            Log.e(TAG, "Unexpected fused location error", exception)
        }
    }

    private fun inspectLocation(
        location: Location,
        source: String,
        checkMockFlag: Boolean,
        useModernMockApi: Boolean = false
    ) {
        if (completed) return

        synchronized(foundLocations) {
            foundLocations += Location(location)
        }

        if (!checkMockFlag) {
            Log.i(TAG, "Coordinate context source=$source lat=${location.latitude} lon=${location.longitude}")
            return
        }

        verifiedFixReceived = true
        latestVerifiedLocation = Location(location)
        val isMock = try {
            if (useModernMockApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock
            } else {
                @Suppress("DEPRECATION")
                location.isFromMockProvider
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Could not inspect mock-provider flag", exception)
            false
        }

        Log.i(
            TAG,
            "Location source=$source mock=$isMock lat=${location.latitude} lon=${location.longitude}"
        )

        if (isMock) {
            spoofedLocation = Location(location)
            reasons += "Android marked a location as mock (reference application mock-provider check; source: $source)."
        }
    }

    private fun finishWithoutLocationPermission() {
        val report = if (developerOptionsEnabled) {
            DetectionReport(
                status = DetectionStatus.CAUGHT,
                reasons = reasons.toList(),
                message = "Developer Options are enabled. Location permission is unavailable, so coordinates could not be read."
            )
        } else {
            DetectionReport(
                status = DetectionStatus.UNABLE_TO_VERIFY,
                message = "Location permission was not granted. The app cannot classify the device as SAFE."
            )
        }
        onResultNow(report)
    }

    private fun onResultNow(report: DetectionReport) {
        completed = true
        onComplete?.invoke(report)
        onComplete = null
    }

    private fun scheduleFinishAfterFix() {
        if (completed) return
        finishAfterFirstFix?.let(handler::removeCallbacks)
        finishAfterFirstFix = Runnable { finish() }
        handler.postDelayed(finishAfterFirstFix!!, FIX_COLLECTION_WINDOW_MS)
    }

    private val timeoutRunnable = Runnable {
        Log.i(TAG, "Location collection timeout reached")
        finish()
    }

    private fun finish() {
        if (completed) return
        completed = true

        handler.removeCallbacks(timeoutRunnable)
        finishAfterFirstFix?.let(handler::removeCallbacks)
        try {
            locationManager.removeUpdates(locationListener)
        } catch (exception: Exception) {
            Log.e(TAG, "Could not remove location updates", exception)
        }
        try {
            fusedCancellation?.cancel()
        } catch (exception: Exception) {
            Log.e(TAG, "Could not cancel fused location request", exception)
        }

        val latestLocation = synchronized(foundLocations) {
            foundLocations.maxByOrNull { it.time }?.let { Location(it) }
        }
        val displayLocation = spoofedLocation ?: latestLocation

        val report = when {
            developerOptionsEnabled || spoofedLocation != null -> {
                Log.w(TAG, "CAUGHT: reference application-matched spoofing indicator detected")
                DetectionReport(
                    status = DetectionStatus.CAUGHT,
                    location = displayLocation,
                    reasons = reasons.toList(),
                    message = if (spoofedLocation != null) {
                        "Android reported a mock location."
                    } else {
                        "Developer Options are enabled."
                    }
                )
            }
            verifiedFixReceived && latestVerifiedLocation != null -> {
                Log.i(TAG, "SAFE: no reference application-matched spoofing indicators found")
                DetectionReport(
                    status = DetectionStatus.SAFE,
                    location = latestVerifiedLocation,
                    message = "No mock-location or Developer Options indicators were detected."
                )
            }
            else -> {
                Log.w(TAG, "Unable to verify: no checked current location fix was received")
                DetectionReport(
                    status = DetectionStatus.UNABLE_TO_VERIFY,
                    message = "No location fix was available. The app cannot classify this device as SAFE."
                )
            }
        }

        onComplete?.invoke(report)
        onComplete = null
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        private const val TAG = "CaughtYouDetector"
        private const val LOCATION_MIN_TIME_MS = 60_000L
        private const val LOCATION_MIN_DISTANCE_METERS = 10f
        private const val FIX_COLLECTION_WINDOW_MS = 2_500L
        private const val MAX_WAIT_MS = 12_000L
    }
}
