package com.example.library.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_photos")
data class LocalPhoto(
    @PrimaryKey val id: String, // typically UUID or hash of local file path
    val fileUri: String,        // Local filepath or content URI
    val fileName: String,       // "DSC_0128.jpg"
    val timestamp: Long,        // when it was imported/created
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val shadows: Float = 0.0f,
    val highlights: Float = 0.0f,
    val filterName: String = "None",
    val blurIntensity: Float = 0.0f,
    val maskCenterX: Float = 0.5f,
    val maskCenterY: Float = 0.5f,
    val maskRadius: Float = 0.3f,
    val cropLeft: Float = 0.0f,
    val cropTop: Float = 0.0f,
    val cropRight: Float = 1.0f,
    val cropBottom: Float = 1.0f,
    val assignedUserId: String? = null,
    val assignedUserName: String? = null,
    val isPublished: Boolean = false,
    val publishedUrl: String? = null,
    val rating: Int = 0,               // 0 to 5 stars
    val isFlagged: Boolean = false,    // flagged status
    val sharpness: Float = 0.0f,       // 0.0 to 1.0
    val vignette: Float = 0.0f,        // 0.0 to 1.0
    val temperature: Float = 0.0f,     // -1.0 to 1.0 (warmth)
    val tint: Float = 0.0f,            // -1.0 to 1.0
    val exposure: Float = 0.0f,        // -1.0 to 1.0
    val straighten: Float = 0.0f,      // -45.0 to 45.0 degrees
    val borderType: String = "None",   // "None", "Classic White", "Gallery Black"
    val gpsLatitude: Double = 0.0,
    val gpsLongitude: Double = 0.0,
    val whites: Float = 0.0f,          // -1.0 to 1.0 (whites level)
    val blacks: Float = 0.0f,          // -1.0 to 1.0 (blacks level)
    val projectId: String? = null,
    val isPromotedToLibrary: Boolean = true
)

@Entity(tableName = "local_projects")
data class LocalProject(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_settings")
data class LocalSettings(
    @PrimaryKey val id: Int = 1, // Singleton row
    val autoApplyPresets: Boolean = false,
    val presetBrightness: Float = 1.0f,
    val presetContrast: Float = 1.0f,
    val presetSaturation: Float = 1.0f,
    val presetShadows: Float = 0.0f,
    val presetHighlights: Float = 0.0f,
    val presetFilter: String = "None",
    val presetBlur: Float = 0.0f,
    val presetMaskRadius: Float = 0.3f,
    val tetherDirectoryUri: String? = null,
    val activeEventId: String? = null,
    val lastSelectedClientId: String? = null,
    val lastAppliedBrightness: Float = 1.0f,
    val lastAppliedContrast: Float = 1.0f,
    val lastAppliedSaturation: Float = 1.0f,
    val lastAppliedShadows: Float = 0.0f,
    val lastAppliedHighlights: Float = 0.0f,
    val lastAppliedFilter: String = "None",
    val lastAppliedBlur: Float = 0.0f,
    val lastAppliedMaskRadius: Float = 0.3f,
    val exportDirectoryUri: String? = null
)

@Entity(tableName = "local_registrations")
data class LocalRegistration(
    @PrimaryKey val id: String, // Registration ID (matches Firestore registration document ID)
    val eventId: String,        // Config/Event ID
    val name: String,           // Owner's full name
    val email: String,
    val pets: String,           // Comma-separated list of pet names
    val notes: String,
    val timestamp: String,      // ISO format check-in time
    val photosUploaded: Int = 0,
    val photoFiles: String = "",
    val isBookingPaid: Boolean = false,
    val paymentMethod: String = "",
    val paymentAmount: Double = 0.0,
    val paymentRef: String = "",
    val paymentTimestamp: String = "",
    val portalPin: String = ""
)

@Entity(tableName = "local_configs")
data class LocalConfig(
    @PrimaryKey val id: String, // Event ID (e.g. "19924")
    val eventName: String,
    val eventDate: String,
    val eventLocation: String,
    val photographerBrand: String,
    val logoUrl: String? = null,
    val portalTheme: String = "classic",
    val priceOnDay: Double = 15.00,
    val pricePerPhoto: Double = 15.00,
    val includedFreeDownloads: Int = 1,
    val sourceCollection: String = "configs"
)

data class BrushPoint(
    val x: Float,
    val y: Float,
    val radius: Float,
    val type: String // "BOKEH", "SHADOW", "CLEAR"
)

data class EditSnapshot(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val shadows: Float,
    val highlights: Float,
    val filterName: String,
    val blurIntensity: Float,
    val maskCenterX: Float,
    val maskCenterY: Float,
    val maskRadius: Float,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float,
    val rating: Int,
    val isFlagged: Boolean,
    val sharpness: Float,
    val vignette: Float,
    val temperature: Float,
    val tint: Float,
    val exposure: Float,
    val brushPoints: List<BrushPoint>
)
