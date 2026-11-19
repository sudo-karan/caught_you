package com.caughtyou.app.detection

import android.location.Location

enum class DetectionStatus {
    CHECKING,
    SAFE,
    CAUGHT,
    UNABLE_TO_VERIFY
}

data class DetectionReport(
    val status: DetectionStatus,
    val location: Location? = null,
    val reasons: List<String> = emptyList(),
    val message: String = ""
)
