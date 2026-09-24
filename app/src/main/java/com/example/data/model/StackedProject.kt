package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stacked_projects")
data class StackedProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val targetPlanet: String,
    val totalFrames: Int,
    val stackedFrames: Int,
    val stackingMethod: String,
    val qualityScoreAvg: Float,
    val imagePath: String,
    val rawBestImagePath: String,
    val snrBoostDb: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val waveletFine: Float = 1.0f,
    val waveletMedium: Float = 0.5f,
    val waveletCoarse: Float = 0.2f,
    val contrast: Float = 1.1f,
    val saturation: Float = 1.0f,
    val brightness: Float = 1.0f,
    val redShiftX: Int = 0,
    val redShiftY: Int = 0,
    val blueShiftX: Int = 0,
    val blueShiftY: Int = 0
)
