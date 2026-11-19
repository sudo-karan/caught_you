package com.caughtyou.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.caughtyou.app.detection.LocationIntegrityDetector
import com.caughtyou.app.detection.DetectionReport
import com.caughtyou.app.detection.DetectionStatus
import com.caughtyou.app.ui.ResultScreen

class MainActivity : ComponentActivity() {

    private val reportState = mutableStateOf(
        DetectionReport(
            status = DetectionStatus.CHECKING,
            message = "Checking device location..."
        )
    )

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (!granted) {
            Log.w(TAG, "User did not grant location permission; Developer Options check will still run")
        }
        // The detector always checks Developer Options, even without location permission.
        startOneTimeCheck()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Caught_You v1.1 launched; beginning one-time check")

        setContent {
            ResultScreen(report = reportState.value)
        }

        if (hasLocationPermission()) {
            startOneTimeCheck()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun startOneTimeCheck() {
        reportState.value = DetectionReport(
            status = DetectionStatus.CHECKING,
            message = "Checking Developer Options and location-provider signals..."
        )
        val detector = LocationIntegrityDetector(applicationContext)
        detector.run { report ->
            Log.i(TAG, "One-time check completed: ${report.status}")
            reportState.value = report
            val color = when (report.status) {
                DetectionStatus.SAFE -> 0xFF15803D.toInt()
                DetectionStatus.CAUGHT -> 0xFFB91C1C.toInt()
                DetectionStatus.CHECKING -> 0xFF202124.toInt()
                DetectionStatus.UNABLE_TO_VERIFY -> 0xFF8A5A00.toInt()
            }
            window.statusBarColor = color
            window.navigationBarColor = color
        }
    }

    private companion object {
        const val TAG = "CaughtYou"
    }
}
