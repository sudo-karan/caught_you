package com.caughtyou.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caughtyou.app.detection.DetectionReport
import com.caughtyou.app.detection.DetectionStatus
import java.util.Locale

@Composable
fun ResultScreen(report: DetectionReport) {
    val background = when (report.status) {
        DetectionStatus.SAFE -> Color(0xFF15803D)
        DetectionStatus.CAUGHT -> Color(0xFFB91C1C)
        DetectionStatus.CHECKING -> Color(0xFF202124)
        DetectionStatus.UNABLE_TO_VERIFY -> Color(0xFF8A5A00)
    }
    val headline = when (report.status) {
        DetectionStatus.SAFE -> "SAFE"
        DetectionStatus.CAUGHT -> "CAUGHT YOU"
        DetectionStatus.CHECKING -> "CHECKING"
        DetectionStatus.UNABLE_TO_VERIFY -> "UNABLE TO VERIFY"
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "CAUGHT_YOU",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = headline,
                    color = Color.White,
                    fontSize = if (report.status == DetectionStatus.UNABLE_TO_VERIFY)  thirtySp() else  fortyEightSp(),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = report.message,
                    color = Color.White,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                report.location?.let { location ->
                    Spacer(Modifier.height(30.dp))
                    Text(
                        text = if (report.status == DetectionStatus.CAUGHT && report.reasons.any { it.contains("mock", true) }) {
                            "REPORTED MOCK COORDINATES"
                        } else {
                            "REPORTED DEVICE COORDINATES"
                        },
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "LAT  ${String.format(Locale.US, "%.6f", location.latitude)}",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "LON  ${String.format(Locale.US, "%.6f", location.longitude)}",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (location.hasAccuracy()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Accuracy ±${location.accuracy.toInt()} m",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp
                        )
                    }
                }

                if (report.reasons.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    report.reasons.forEach { reason ->
                        Text(
                            text = reason,
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun thirtySp() = 30.sp
private fun fortyEightSp() = 48.sp
