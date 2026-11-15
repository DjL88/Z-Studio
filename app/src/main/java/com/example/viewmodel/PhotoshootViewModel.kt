package com.example.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.graphics.*
import android.net.Uri
import android.widget.Toast
import android.provider.DocumentsContract
import android.util.Log
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.library.data.*
import com.example.library.service.NefImageDecodingService
import com.example.library.ui.LibraryViewModelDelegate
import com.example.publishing.service.ExportManager
import com.example.tethernikon.data.*
import com.example.tethernikon.service.*
import com.example.editor.service.*
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

sealed class CameraConnectionState {
    object Disconnected : CameraConnectionState()
    data class Error(val message: String) : CameraConnectionState()
    data class Connected(
        val model: String,
        val aperture: String,
        val shutter: String,
        val iso: String,
        val battery: String,
        val focusMode: String
    ) : CameraConnectionState()
}

class PhotoshootViewModel(
    private val context: Context,
    private val repository: PhotoshootRepository
) : ViewModel(), LibraryViewModelDelegate {

    private val tag = "PhotoshootViewModel"
    private val exportManager = ExportManager(context)

    // --- USB Camera State Flow ---
    private val _cameraConnectionState = MutableStateFlow<CameraConnectionState>(CameraConnectionState.Disconnected)
    val cameraConnectionState: StateFlow<CameraConnectionState> = _cameraConnectionState.asStateFlow()

    // --- State Expositions ---
    val allPhotos: StateFlow<List<LocalPhoto>> = repository.allPhotos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allConfigs: StateFlow<List<LocalConfig>> = repository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<LocalSettings> = repository.settingsFlow
        .map { it ?: LocalSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalSettings())

    private val _activeConfigId = MutableStateFlow<String?>(null)
    val activeConfigId: StateFlow<String?> = _activeConfigId.asStateFlow()

    private val _activeConfig = MutableStateFlow<LocalConfig?>(null)
    val activeConfig: StateFlow<LocalConfig?> = _activeConfig.asStateFlow()

    // Real-time registrations for the active event config
    val registrations: StateFlow<List<LocalRegistration>> = _activeConfigId
        .flatMapLatest { eventId ->
            if (eventId != null) repository.getRegistrationsForEvent(eventId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRegistrations: StateFlow<List<LocalRegistration>> = repository.getAllRegistrations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _onStageRegistrationId = MutableStateFlow<String?>(null)
    val onStageRegistrationId: StateFlow<String?> = _onStageRegistrationId.asStateFlow()

    val onStageRegistration: StateFlow<LocalRegistration?> = combine(
        allRegistrations,
        _onStageRegistrationId
    ) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedPhotoId = MutableStateFlow<String?>(null)
    val selectedPhotoId: StateFlow<String?> = _selectedPhotoId.asStateFlow()

    // --- Library Projects flows and methods ---
    val allProjects: StateFlow<List<LocalProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    val filteredPhotos: StateFlow<List<LocalPhoto>> = combine(
        allPhotos,
        _selectedProjectId
    ) { photos, projId ->
        if (projId == null) {
            photos
        } else {
            photos.filter { it.projectId == projId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPhoto: StateFlow<LocalPhoto?> = combine(
        filteredPhotos,
        _selectedPhotoId
    ) { photos, id ->
        photos.find { it.id == id } ?: photos.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    override fun createProject(name: String, description: String) {
        viewModelScope.launch {
            val project = LocalProject(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                description = description,
                createdAt = System.currentTimeMillis()
            )
            repository.insertOrUpdateProject(project)
        }
    }

    override fun selectProject(projectId: String?) {
        _selectedProjectId.value = projectId
        try {
            val prefs = context.getSharedPreferences("photoshoot_app_prefs", Context.MODE_PRIVATE)
            if (projectId != null) {
                prefs.edit().putString("selected_project_id", projectId).apply()
            } else {
                prefs.edit().remove("selected_project_id").apply()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error saving selected_project_id to prefs", e)
        }
        // Automatically select the first photo in this project if available
        viewModelScope.launch {
            val photosInProj = if (projectId == null) {
                allPhotos.value
            } else {
                allPhotos.value.filter { it.projectId == projectId }
            }
            _selectedPhotoId.value = photosInProj.firstOrNull()?.id
        }
    }

    override fun deleteProject(project: LocalProject) {
        viewModelScope.launch {
            // First, remove project association from all photos in this project
            allPhotos.value.forEach { photo ->
                if (photo.projectId == project.id) {
                    repository.insertLocalPhoto(photo.copy(projectId = null))
                }
            }
            repository.deleteProject(project)
            if (_selectedProjectId.value == project.id) {
                _selectedProjectId.value = null
            }
        }
    }

    override fun assignPhotoToProject(photoId: String, projectId: String?) {
        viewModelScope.launch {
            val photo = repository.getPhotoById(photoId) ?: allPhotos.value.find { it.id == photoId }
            if (photo != null) {
                repository.insertLocalPhoto(photo.copy(projectId = projectId))
            }
        }
    }

    // Edit controls bound to the UI sliders
    private val _editBrightness = MutableStateFlow(1.0f)
    val editBrightness = _editBrightness.asStateFlow()

    private val _editContrast = MutableStateFlow(1.0f)
    val editContrast = _editContrast.asStateFlow()

    private val _editSaturation = MutableStateFlow(1.0f)
    val editSaturation = _editSaturation.asStateFlow()

    private val _editShadows = MutableStateFlow(0.0f)
    val editShadows = _editShadows.asStateFlow()

    private val _editHighlights = MutableStateFlow(0.0f)
    val editHighlights = _editHighlights.asStateFlow()

    private val _editWhites = MutableStateFlow(0.0f)
    val editWhites = _editWhites.asStateFlow()

    private val _editBlacks = MutableStateFlow(0.0f)
    val editBlacks = _editBlacks.asStateFlow()

    private val _editFilterName = MutableStateFlow("None")
    val editFilterName = _editFilterName.asStateFlow()

    private val _editBlurIntensity = MutableStateFlow(0.0f)
    val editBlurIntensity = _editBlurIntensity.asStateFlow()

    private val _editMaskCenterX = MutableStateFlow(0.5f)
    val editMaskCenterX = _editMaskCenterX.asStateFlow()

    private val _editMaskCenterY = MutableStateFlow(0.5f)
    val editMaskCenterY = _editMaskCenterY.asStateFlow()

    private val _editMaskRadius = MutableStateFlow(0.3f)
    val editMaskRadius = _editMaskRadius.asStateFlow()

    private val _cropLeft = MutableStateFlow(0.0f)
    val cropLeft = _cropLeft.asStateFlow()

    private val _cropTop = MutableStateFlow(0.0f)
    val cropTop = _cropTop.asStateFlow()

    private val _cropRight = MutableStateFlow(1.0f)
    val cropRight = _cropRight.asStateFlow()

    private val _cropBottom = MutableStateFlow(1.0f)
    val cropBottom = _cropBottom.asStateFlow()

    private val _cropMode = MutableStateFlow(false)
    val cropMode = _cropMode.asStateFlow()

    private val _editRating = MutableStateFlow(0)
    val editRating = _editRating.asStateFlow()

    private val _editIsFlagged = MutableStateFlow(false)
    val editIsFlagged = _editIsFlagged.asStateFlow()

    private val _editSharpness = MutableStateFlow(0.0f)
    val editSharpness = _editSharpness.asStateFlow()

    private val _editVignette = MutableStateFlow(0.0f)
    val editVignette = _editVignette.asStateFlow()

    private val _editTemperature = MutableStateFlow(0.0f)
    val editTemperature = _editTemperature.asStateFlow()

    private val _editTint = MutableStateFlow(0.0f)
    val editTint = _editTint.asStateFlow()

    private val _editExposure = MutableStateFlow(0.0f)
    val editExposure = _editExposure.asStateFlow()

    private val _editStraighten = MutableStateFlow(0.0f)
    val editStraighten = _editStraighten.asStateFlow()

    private val _editBorderType = MutableStateFlow("None")
    val editBorderType = _editBorderType.asStateFlow()

    private val _editGpsLatitude = MutableStateFlow(0.0)
    val editGpsLatitude = _editGpsLatitude.asStateFlow()

    private val _editGpsLongitude = MutableStateFlow(0.0)
    val editGpsLongitude = _editGpsLongitude.asStateFlow()

    private val _tetherGpsEnabled = MutableStateFlow(false)
    val tetherGpsEnabled = _tetherGpsEnabled.asStateFlow()

    private val _tetherGpsLatitude = MutableStateFlow(35.6894)
    val tetherGpsLatitude = _tetherGpsLatitude.asStateFlow()

    private val _tetherGpsLongitude = MutableStateFlow(139.6917)
    val tetherGpsLongitude = _tetherGpsLongitude.asStateFlow()

    fun updateTetherGps(enabled: Boolean, latitude: Double, longitude: Double) {
        _tetherGpsEnabled.value = enabled
        _tetherGpsLatitude.value = latitude
        _tetherGpsLongitude.value = longitude
    }

    // --- Focal Blur Brush & Undo History States ---
    private val _editMode = MutableStateFlow("FOCAL_SPOT") // "FOCAL_SPOT" or "BRUSH"
    val editMode: StateFlow<String> = _editMode.asStateFlow()

    private val _brushType = MutableStateFlow("BOKEH") // "BOKEH", "SHADOW", "CLEAR"
    val brushType: StateFlow<String> = _brushType.asStateFlow()

    private val _brushSize = MutableStateFlow(50f) // brush size/radius in dp
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()

    private val _brushPoints = MutableStateFlow<List<BrushPoint>>(emptyList())
    val brushPoints: StateFlow<List<BrushPoint>> = _brushPoints.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val undoStack = mutableListOf<EditSnapshot>()

    fun setEditMode(mode: String) {
        _editMode.value = mode
    }

    fun setBrushType(type: String) {
        _brushType.value = type
    }

    fun setBrushSize(size: Float) {
        _brushSize.value = size
    }

    fun addBrushPoint(point: BrushPoint) {
        _brushPoints.value = _brushPoints.value + point
    }

    fun clearBrushPoints() {
        saveToHistory()
        _brushPoints.value = emptyList()
    }

    fun importUploadedPhoto(fileUri: android.net.Uri, fileName: String) {
        viewModelScope.launch {
            importTetherPhoto(fileUri, fileName)
        }
    }

    fun saveToHistory() {
        val currentPhoto = selectedPhoto.value ?: return
        val snapshot = EditSnapshot(
            brightness = _editBrightness.value,
            contrast = _editContrast.value,
            saturation = _editSaturation.value,
            shadows = _editShadows.value,
            highlights = _editHighlights.value,
            filterName = _editFilterName.value,
            blurIntensity = _editBlurIntensity.value,
            maskCenterX = _editMaskCenterX.value,
            maskCenterY = _editMaskCenterY.value,
            maskRadius = _editMaskRadius.value,
            cropLeft = _cropLeft.value,
            cropTop = _cropTop.value,
            cropRight = _cropRight.value,
            cropBottom = _cropBottom.value,
            rating = _editRating.value,
            isFlagged = _editIsFlagged.value,
            sharpness = _editSharpness.value,
            vignette = _editVignette.value,
            temperature = _editTemperature.value,
            tint = _editTint.value,
            exposure = _editExposure.value,
            brushPoints = _brushPoints.value.toList()
        )
        undoStack.add(snapshot)
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        _canUndo.value = undoStack.isNotEmpty()
    }

    fun undoLastStep() {
        if (undoStack.isEmpty()) return
        val lastState = undoStack.removeAt(undoStack.lastIndex)
        _canUndo.value = undoStack.isNotEmpty()

        _editBrightness.value = lastState.brightness
        _editContrast.value = lastState.contrast
        _editSaturation.value = lastState.saturation
        _editShadows.value = lastState.shadows
        _editHighlights.value = lastState.highlights
        _editFilterName.value = lastState.filterName
        _editBlurIntensity.value = lastState.blurIntensity
        _editMaskCenterX.value = lastState.maskCenterX
        _editMaskCenterY.value = lastState.maskCenterY
        _editMaskRadius.value = lastState.maskRadius
        _cropLeft.value = lastState.cropLeft
        _cropTop.value = lastState.cropTop
        _cropRight.value = lastState.cropRight
        _cropBottom.value = lastState.cropBottom
        _editRating.value = lastState.rating
        _editIsFlagged.value = lastState.isFlagged
        _editSharpness.value = lastState.sharpness
        _editVignette.value = lastState.vignette
        _editTemperature.value = lastState.temperature
        _editTint.value = lastState.tint
        _editExposure.value = lastState.exposure
        _brushPoints.value = lastState.brushPoints

        val currentPhoto = selectedPhoto.value
        viewModelScope.launch {
            if (currentPhoto != null) {
                val restored = currentPhoto.copy(
                    brightness = lastState.brightness,
                    contrast = lastState.contrast,
                    saturation = lastState.saturation,
                    shadows = lastState.shadows,
                    highlights = lastState.highlights,
                    filterName = lastState.filterName,
                    blurIntensity = lastState.blurIntensity,
                    maskCenterX = lastState.maskCenterX,
                    maskCenterY = lastState.maskCenterY,
                    maskRadius = lastState.maskRadius,
                    cropLeft = lastState.cropLeft,
                    cropTop = lastState.cropTop,
                    cropRight = lastState.cropRight,
                    cropBottom = lastState.cropBottom,
                    rating = lastState.rating,
                    isFlagged = lastState.isFlagged,
                    sharpness = lastState.sharpness,
                    vignette = lastState.vignette,
                    temperature = lastState.temperature,
                    tint = lastState.tint,
                    exposure = lastState.exposure
                )
                repository.insertLocalPhoto(restored)
            }
        }
    }

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing = _isPublishing.asStateFlow()

    private val _publishStatus = MutableStateFlow<String?>(null)
    val publishStatus = _publishStatus.asStateFlow()

    fun clearPublishStatus() {
        _publishStatus.value = null
    }

    private val _isSyncingFirebase = MutableStateFlow(false)
    val isSyncingFirebase = _isSyncingFirebase.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError = _syncError.asStateFlow()

    private val _isFolderPolling = MutableStateFlow(false)
    val isFolderPolling = _isFolderPolling.asStateFlow()

    private val _simulatedConnection = MutableStateFlow(false)
    val simulatedConnection = _simulatedConnection.asStateFlow()

    val isFirebaseAvailable: Boolean
        get() = repository.isFirebaseAvailable

    private val _denoisedPreviewUrl = MutableStateFlow<String?>(null)
    val denoisedPreviewUrl: StateFlow<String?> = _denoisedPreviewUrl.asStateFlow()

    private var mtpPollerJob: Job? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val action = intent.action
            if ("com.android.example.USB_PERMISSION" == action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(tag, "USB Permission granted dynamically! Re-triggering camera connection.")
                            viewModelScope.launch {
                                updateCameraStatus()
                            }
                        }
                    } else {
                        Log.e(tag, "USB permission was denied by the user.")
                    }
                }
            }
        }
    }

    private var folderMonitorJob: Job? = null

    init {
        // Register USB dynamic permission receiver
        val filter = IntentFilter("com.android.example.USB_PERMISSION")
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Load initial settings and trigger Firebase configuration sync
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("photoshoot_app_prefs", Context.MODE_PRIVATE)
            val s = repository.getSettings()

            val savedShootId = prefs.getString("active_shoot_id", null) ?: s.activeEventId
            val savedClientId = prefs.getString("on_stage_client_id", null) ?: s.lastSelectedClientId
            val savedProjectId = prefs.getString("selected_project_id", null)

            if (!savedShootId.isNullOrBlank()) {
                _activeConfigId.value = savedShootId
                val config = repository.getConfigById(savedShootId)
                if (config != null) {
                    _activeConfig.value = config
                }
            }
            if (!savedClientId.isNullOrBlank()) {
                _onStageRegistrationId.value = savedClientId
            }
            if (!savedProjectId.isNullOrBlank()) {
                _selectedProjectId.value = savedProjectId
            }

            // Fetch initial configurations from Firebase Firestore
            syncConfigs()

            // Start listening to Firestore registrations if an event is active
            val activeId = _activeConfigId.value
            if (!activeId.isNullOrEmpty()) {
                repository.startListeningToRegistrations(activeId)
                if (_activeConfig.value == null) {
                    _activeConfig.value = repository.allConfigs.firstOrNull()?.find { it.id == activeId }
                }
                val activeClientId = _onStageRegistrationId.value
                if (!activeClientId.isNullOrEmpty()) {
                    repository.syncPublishedPhotosFromFirestore(activeId, activeClientId)
                }
            }

            // Start background folder poller if tether directory is configured
            if (!s.tetherDirectoryUri.isNullOrEmpty()) {
                startFolderMonitoring()
            }
        }

        // Periodic USB camera status refresh (gentle 10s check)
        viewModelScope.launch {
            while (true) {
                updateCameraStatus()
                delay(10000)
            }
        }

        // Keep local sliders in sync with the selected photo
        viewModelScope.launch {
            selectedPhoto.collect { photo ->
                // Clear history on photo switch
                undoStack.clear()
                _canUndo.value = false
                _brushPoints.value = emptyList()

                if (photo != null) {
                    _editBrightness.value = photo.brightness
                    _editContrast.value = photo.contrast
                    _editSaturation.value = photo.saturation
                    _editShadows.value = photo.shadows
                    _editHighlights.value = photo.highlights
                    _editWhites.value = photo.whites
                    _editBlacks.value = photo.blacks
                    _editFilterName.value = photo.filterName
                    _editBlurIntensity.value = photo.blurIntensity
                    _editMaskCenterX.value = photo.maskCenterX
                    _editMaskCenterY.value = photo.maskCenterY
                    _editMaskRadius.value = photo.maskRadius
                    _cropLeft.value = photo.cropLeft
                    _cropTop.value = photo.cropTop
                    _cropRight.value = photo.cropRight
                    _cropBottom.value = photo.cropBottom
                    _editRating.value = photo.rating
                    _editIsFlagged.value = photo.isFlagged
                    _editSharpness.value = photo.sharpness
                    _editVignette.value = photo.vignette
                    _editTemperature.value = photo.temperature
                    _editTint.value = photo.tint
                    _editExposure.value = photo.exposure
                    _editStraighten.value = photo.straighten
                    _editBorderType.value = photo.borderType
                    _editGpsLatitude.value = photo.gpsLatitude
                    _editGpsLongitude.value = photo.gpsLongitude
                }
            }
        }
    }

    // --- Actions & Triggers ---

    fun syncConfigs() {
        viewModelScope.launch {
            _isSyncingFirebase.value = true
            _syncError.value = null
            try {
                val detectedEventId = repository.fetchConfigsFromFirebase()
                val currentActiveId = _activeConfigId.value
                val currentClientId = _onStageRegistrationId.value
                
                if (detectedEventId != null) {
                    val config = repository.getConfigById(detectedEventId)
                    if (config != null) {
                        selectEvent(config, clearOnStageClient = (config.id != currentActiveId))
                    }
                } else {
                    // Fallback: If no event was detected remotely, keep current selection
                    if (currentActiveId != null) {
                        val currentConfig = repository.getConfigById(currentActiveId)
                            ?: repository.allConfigs.firstOrNull()?.find { it.id == currentActiveId }
                        if (currentConfig != null) {
                            _activeConfig.value = currentConfig
                        }
                    } else {
                        // Auto-select first available config from database so that the app works instantly
                        val firstConfig = repository.getFirstConfig()
                        if (firstConfig != null) {
                            selectEvent(firstConfig, clearOnStageClient = false)
                        }
                    }
                }

                // Restore preserved client if it was present
                if (!currentClientId.isNullOrBlank() && _onStageRegistrationId.value == null) {
                    _onStageRegistrationId.value = currentClientId
                }
            } catch (e: Exception) {
                Log.e("PhotoshootViewModel", "Error syncing configurations", e)
                _syncError.value = e.message ?: e.toString()
                
                val currentActiveId = _activeConfigId.value
                if (currentActiveId != null) {
                    val currentConfig = repository.getConfigById(currentActiveId)
                        ?: repository.allConfigs.firstOrNull()?.find { it.id == currentActiveId }
                    if (currentConfig != null) {
                        _activeConfig.value = currentConfig
                    }
                } else {
                    val firstConfig = repository.getFirstConfig()
                    if (firstConfig != null) {
                        selectEvent(firstConfig, clearOnStageClient = false)
                    }
                }
            } finally {
                _isSyncingFirebase.value = false
            }
        }
    }

    fun selectEvent(config: LocalConfig, clearOnStageClient: Boolean = true) {
        viewModelScope.launch {
            val previousEventId = _activeConfigId.value
            _activeConfigId.value = config.id
            _activeConfig.value = config
            
            // Persist the active event configuration locally
            try {
                val prefs = context.getSharedPreferences("photoshoot_app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("active_shoot_id", config.id).apply()
            } catch (e: Exception) {
                Log.e(tag, "Error saving active_shoot_id to prefs", e)
            }

            val s = repository.getSettings()
            repository.saveSettings(s.copy(activeEventId = config.id))
            
            // Switch real-time Firestore listener
            repository.startListeningToRegistrations(config.id)
            
            if (clearOnStageClient && previousEventId != null && previousEventId != config.id) {
                _onStageRegistrationId.value = null
                try {
                    val prefs = context.getSharedPreferences("photoshoot_app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().remove("on_stage_client_id").apply()
                } catch (e: Exception) {
                    Log.e(tag, "Error removing on_stage_client_id from prefs", e)
                }
                repository.saveSettings(repository.getSettings().copy(lastSelectedClientId = null))
            }
            
            // Only sync published photos if a client is also selected
            val activeClientId = _onStageRegistrationId.value
            if (!activeClientId.isNullOrBlank()) {
                repository.syncPublishedPhotosFromFirestore(config.id, activeClientId)
            }
        }
    }

    fun selectOnStageClient(registration: LocalRegistration?) {
        _onStageRegistrationId.value = registration?.id
        Log.d(tag, "Client is on stage: ${registration?.name}")

        try {
            val prefs = context.getSharedPreferences("photoshoot_app_prefs", Context.MODE_PRIVATE)
            if (registration?.id != null) {
                prefs.edit().putString("on_stage_client_id", registration.id).apply()
            } else {
                prefs.edit().remove("on_stage_client_id").apply()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error saving on_stage_client_id to prefs", e)
        }

        viewModelScope.launch {
            if (registration != null && registration.eventId.isNotBlank()) {
                val currentEventId = _activeConfigId.value
                if (currentEventId != registration.eventId) {
                    val targetConfig = repository.allConfigs.firstOrNull()?.find { it.id == registration.eventId }
                    if (targetConfig != null) {
                        _activeConfigId.value = targetConfig.id
                        _activeConfig.value = targetConfig
                        try {
                            val prefs = context.getSharedPreferences("photoshoot_app_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("active_shoot_id", targetConfig.id).apply()
                        } catch (e: Exception) {
                            Log.e(tag, "Error saving active_shoot_id to prefs", e)
                        }
                        repository.startListeningToRegistrations(targetConfig.id)
                    }
                }
            }
            val s = repository.getSettings()
            repository.saveSettings(s.copy(
                activeEventId = _activeConfigId.value ?: s.activeEventId,
                lastSelectedClientId = registration?.id
            ))
            
            val activeEventId = _activeConfigId.value ?: registration?.eventId
            val activeClientId = registration?.id
            if (!activeEventId.isNullOrBlank() && !activeClientId.isNullOrBlank()) {
                repository.syncPublishedPhotosFromFirestore(activeEventId, activeClientId)
            }
        }
    }

    override fun syncPublishedPhotos() {
        viewModelScope.launch {
            try {
                val activeEventId = _activeConfigId.value
                val activeClientId = _onStageRegistrationId.value
                if (!activeEventId.isNullOrBlank() && !activeClientId.isNullOrBlank()) {
                    repository.syncPublishedPhotosFromFirestore(activeEventId, activeClientId)
                }
            } catch (e: Exception) {
                Log.e("PhotoshootViewModel", "Error syncing published photos", e)
            }
        }
    }

    override fun syncPublishedPhotos(eventId: String?, userId: String?) {
        viewModelScope.launch {
            try {
                if (!eventId.isNullOrBlank() && !userId.isNullOrBlank()) {
                    repository.syncPublishedPhotosFromFirestore(eventId, userId)
                }
            } catch (e: Exception) {
                Log.e("PhotoshootViewModel", "Error syncing published photos", e)
            }
        }
    }

    override fun selectPhoto(photo: LocalPhoto) {
        _selectedPhotoId.value = photo.id
    }

    fun rotatePhoto90(photo: LocalPhoto) {
        val nextStraighten = (photo.straighten + 90f) % 360f
        viewModelScope.launch(Dispatchers.IO) {
            val updated = photo.copy(straighten = nextStraighten)
            repository.insertLocalPhoto(updated)
            withContext(Dispatchers.Main) {
                if (_selectedPhotoId.value == photo.id) {
                    _editStraighten.value = nextStraighten
                }
            }
        }
    }

    fun rotateSelectedPhoto90() {
        val photo = selectedPhoto.value
        if (photo != null) {
            rotatePhoto90(photo)
        }
    }

    private fun getExifOrientationDegrees(fileUriOrPath: String): Float {
        return try {
            val exif = if (fileUriOrPath.startsWith("content://")) {
                val uri = Uri.parse(fileUriOrPath)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream)
                }
            } else {
                ExifInterface(fileUriOrPath)
            }
            if (exif != null) {
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } else 0f
        } catch (e: Exception) {
            0f
        }
    }

    fun clearSelectedPhoto() {
        _selectedPhotoId.value = null
    }

    override fun deletePhoto(photo: LocalPhoto) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteLocalPhoto(photo)
                withContext(Dispatchers.Main) {
                    if (_selectedPhotoId.value == photo.id) {
                        _selectedPhotoId.value = null
                    }
                }
            } catch (e: Exception) {
                Log.e("PhotoshootViewModel", "Error deleting photo ${photo.id}", e)
            }
        }
    }

    override fun deletePhotos(photos: List<LocalPhoto>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                photos.forEach { photo ->
                    repository.deleteLocalPhoto(photo)
                    withContext(Dispatchers.Main) {
                        if (_selectedPhotoId.value == photo.id) {
                            _selectedPhotoId.value = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PhotoshootViewModel", "Error deleting batch photos", e)
            }
        }
    }

    override fun unpublishPhoto(photo: LocalPhoto) {
        viewModelScope.launch {
            _isPublishing.value = true
            _publishStatus.value = "Unpublishing ${photo.fileName} from Portal..."
            try {
                repository.unpublishPhotoFromFirebase(photo)
                val updated = photo.copy(isPublished = false, publishedUrl = null)
                repository.insertLocalPhoto(updated)
                _publishStatus.value = "Photo ${photo.fileName} unpublished."
            } catch (e: Exception) {
                Log.e(tag, "Error unpublishing photo ${photo.id}", e)
                _publishStatus.value = "Failed to unpublish photo."
            } finally {
                _isPublishing.value = false
            }
        }
    }

    override fun unpublishPhotos(photos: List<LocalPhoto>) {
        viewModelScope.launch {
            _isPublishing.value = true
            _publishStatus.value = "Unpublishing ${photos.size} photo(s)..."
            try {
                photos.forEach { photo ->
                    repository.unpublishPhotoFromFirebase(photo)
                    val updated = photo.copy(isPublished = false, publishedUrl = null)
                    repository.insertLocalPhoto(updated)
                }
                _publishStatus.value = "Unpublished ${photos.size} photo(s)."
            } catch (e: Exception) {
                Log.e(tag, "Error unpublishing photos batch", e)
                _publishStatus.value = "Failed to unpublish photos."
            } finally {
                _isPublishing.value = false
            }
        }
    }

    data class PublishOverwritePrompt(
        val photos: List<LocalPhoto>,
        val onChoice: (overwrite: Boolean) -> Unit
    )

    private val _publishOverwritePrompt = MutableStateFlow<PublishOverwritePrompt?>(null)
    val publishOverwritePrompt: StateFlow<PublishOverwritePrompt?> = _publishOverwritePrompt.asStateFlow()

    fun dismissPublishOverwritePrompt() {
        _publishOverwritePrompt.value = null
    }

    override fun publishPhotos(photos: List<LocalPhoto>, onComplete: (() -> Unit)?) {
        if (photos.any { it.isPublished }) {
            _publishOverwritePrompt.value = PublishOverwritePrompt(photos) { overwrite ->
                _publishOverwritePrompt.value = null
                executePublishPhotos(photos, overwrite, onComplete)
            }
        } else {
            executePublishPhotos(photos, overwrite = true, onComplete = onComplete)
        }
    }

    private fun executePublishPhotos(photos: List<LocalPhoto>, overwrite: Boolean, onComplete: (() -> Unit)?) {
        viewModelScope.launch {
            _isPublishing.value = true
            photos.forEach { photo ->
                try {
                    val inLibrary = allPhotos.value.find { it.id == photo.id }
                    val basePhoto = inLibrary ?: run {
                        val onStage = onStageRegistration.value
                        val updated = photo.copy(
                            assignedUserId = onStage?.id ?: photo.assignedUserId,
                            assignedUserName = onStage?.name ?: photo.assignedUserName,
                            isPromotedToLibrary = true
                        )
                        repository.insertLocalPhoto(updated)
                        updated
                    }

                    val targetPhoto = if (!overwrite && basePhoto.isPublished) {
                        val newVersionName = if (basePhoto.fileName.contains(".")) {
                            "${basePhoto.fileName.substringBeforeLast(".")}_v2.${basePhoto.fileName.substringAfterLast(".")}"
                        } else {
                            "${basePhoto.fileName}_v2"
                        }
                        val newVersion = basePhoto.copy(
                            id = "v${System.currentTimeMillis()}_${basePhoto.id}",
                            fileName = newVersionName,
                            isPublished = false,
                            publishedUrl = null,
                            isPromotedToLibrary = true
                        )
                        repository.insertLocalPhoto(newVersion)
                        newVersion
                    } else {
                        basePhoto.copy(isPromotedToLibrary = true)
                    }

                    val bmp = renderEditedBitmap(targetPhoto) ?: decodeUriToBitmap(context, Uri.parse(targetPhoto.fileUri))
                    if (bmp != null) {
                        repository.publishPhoto(targetPhoto, bmp)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error publishing photo ${photo.id}", e)
                }
            }
            _isPublishing.value = false
            onComplete?.invoke()
        }
    }

    fun publishAndCloseActiveClient(
        context: Context,
        photosToPublish: List<LocalPhoto>,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isPublishing.value = true
            _publishStatus.value = "Preparing Publish & Close session..."

            val activeConfig = activeConfig.value
            val eventName = activeConfig?.eventName ?: "Event Shoot"
            val brandName = activeConfig?.photographerBrand ?: "Studio Photography"
            val onStage = onStageRegistration.value

            val targetClientId = onStage?.id ?: photosToPublish.firstOrNull()?.assignedUserId ?: ""
            val targetClientName = onStage?.name ?: photosToPublish.firstOrNull()?.assignedUserName ?: "Client"
            val targetClientEmail = onStage?.email ?: ""
            val targetClientPhone = ""

            _publishStatus.value = "Ensuring client profile & 4-digit PIN..."
            val pin = if (targetClientId.isNotEmpty()) {
                repository.ensurePortalUserAndGetPin(
                    userId = targetClientId,
                    clientName = targetClientName,
                    email = targetClientEmail,
                    phone = targetClientPhone
                )
            } else "0000"

            var successCount = 0
            val targetPhotos = if (photosToPublish.isNotEmpty()) photosToPublish else {
                onStage?.id?.let { clientId -> allPhotos.value.filter { it.assignedUserId == clientId } } ?: emptyList()
            }

            targetPhotos.forEachIndexed { index, photo ->
                _publishStatus.value = "Uploading photo ${index + 1}/${targetPhotos.size} to Portal..."
                try {
                    val inLibrary = allPhotos.value.find { it.id == photo.id }
                    val libraryPhoto = inLibrary ?: photo
                    val bmp = renderEditedBitmap(libraryPhoto) ?: decodeUriToBitmap(context, Uri.parse(libraryPhoto.fileUri))
                    if (bmp != null) {
                        repository.publishPhoto(libraryPhoto, bmp)
                        successCount++
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error publishing photo ${photo.id}", e)
                }
            }

            if (targetClientEmail.isNotBlank()) {
                val totalPublishedCount = allPhotos.value.count {
                    (it.assignedUserId == targetClientId || targetClientId.isEmpty()) && (it.isPublished || targetPhotos.any { tp -> tp.id == it.id })
                }.coerceAtLeast(successCount).coerceAtLeast(1)

                _publishStatus.value = "Triggering notification email with PIN ($pin) to $targetClientEmail..."
                val notifyRes = repository.notifyClientPhotosUploaded(
                    email = targetClientEmail,
                    clientName = targetClientName,
                    eventName = eventName,
                    pin = pin,
                    identifier = targetClientEmail,
                    photoCount = totalPublishedCount,
                    brandName = brandName
                )
                if (notifyRes.isSuccess) {
                    Log.d(tag, "Email notification API triggered successfully")
                } else {
                    Log.w(tag, "Email notification API error: ${notifyRes.exceptionOrNull()?.message}")
                }
            }

            _publishStatus.value = "Closing client session..."
            _onStageRegistrationId.value = null

            _isPublishing.value = false
            _publishStatus.value = "Successfully published $successCount photo(s) & emailed access PIN ($pin) to $targetClientName! Client session closed."
            onComplete?.invoke()
        }
    }

    fun publishActiveClientOnly(
        context: Context,
        photosToPublish: List<LocalPhoto>,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isPublishing.value = true
            _publishStatus.value = "Publishing photos to client portal..."

            val onStage = onStageRegistration.value
            val targetClientId = onStage?.id ?: photosToPublish.firstOrNull()?.assignedUserId ?: ""
            val targetClientName = onStage?.name ?: photosToPublish.firstOrNull()?.assignedUserName ?: "Client"
            val targetClientEmail = onStage?.email ?: ""
            val targetClientPhone = ""

            _publishStatus.value = "Ensuring portal user PIN profile..."
            val pin = if (targetClientId.isNotEmpty()) {
                repository.ensurePortalUserAndGetPin(
                    userId = targetClientId,
                    clientName = targetClientName,
                    email = targetClientEmail,
                    phone = targetClientPhone
                )
            } else "0000"

            var successCount = 0
            val targetPhotos = if (photosToPublish.isNotEmpty()) photosToPublish else {
                onStage?.id?.let { clientId -> allPhotos.value.filter { it.assignedUserId == clientId } } ?: emptyList()
            }

            targetPhotos.forEachIndexed { index, photo ->
                _publishStatus.value = "Uploading photo ${index + 1}/${targetPhotos.size}..."
                try {
                    val inLibrary = allPhotos.value.find { it.id == photo.id }
                    val libraryPhoto = inLibrary ?: photo
                    val bmp = renderEditedBitmap(libraryPhoto) ?: decodeUriToBitmap(context, Uri.parse(libraryPhoto.fileUri))
                    if (bmp != null) {
                        repository.publishPhoto(libraryPhoto, bmp)
                        successCount++
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error publishing photo ${photo.id}", e)
                }
            }

            _isPublishing.value = false
            _publishStatus.value = "Successfully published $successCount photo(s) to client portal (PIN: $pin). Client remains active on-stage."
            onComplete?.invoke()
        }
    }

    fun notifyClientPortalCredentials(client: LocalRegistration, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _isPublishing.value = true
            _publishStatus.value = "Generating PIN and triggering notification..."

            val activeConfig = activeConfig.value
            val eventName = activeConfig?.eventName ?: "Event Shoot"
            val brandName = activeConfig?.photographerBrand ?: "Studio Photography"

            val pin = repository.ensurePortalUserAndGetPin(
                userId = client.id,
                clientName = client.name,
                email = client.email,
                phone = ""
            )

            val photoCount = allPhotos.value.count { it.assignedUserId == client.id }

            val res = repository.notifyClientPhotosUploaded(
                email = client.email,
                clientName = client.name,
                eventName = eventName,
                pin = pin,
                identifier = client.email,
                photoCount = photoCount,
                brandName = brandName
            )

            _isPublishing.value = false
            if (res.isSuccess) {
                val msg = "Email notification API triggered! Sent portal access link & PIN ($pin) to ${client.email}."
                _publishStatus.value = msg
                onComplete?.invoke(msg)
            } else {
                val msg = "Failed to send email notification: ${res.exceptionOrNull()?.message}"
                _publishStatus.value = msg
                onComplete?.invoke(msg)
            }
        }
    }

    override fun editPhotoWithLibraryCheck(photo: LocalPhoto, onNavigateToEdit: () -> Unit) {
        viewModelScope.launch {
            if (photo.isPublished) {
                // Published photos are read-only online copies. Create a local editable draft copy before editing.
                val draftId = "draft_${System.currentTimeMillis()}_${photo.id.takeLast(8)}"
                val draftPhoto = photo.copy(
                    id = draftId,
                    isPublished = false,
                    publishedUrl = "",
                    isPromotedToLibrary = true
                )
                repository.insertLocalPhoto(draftPhoto)
                _selectedPhotoId.value = draftId
                onNavigateToEdit()
            } else {
                val inLibrary = allPhotos.value.find { it.id == photo.id }
                if (inLibrary != null) {
                    val updated = inLibrary.copy(isPromotedToLibrary = true)
                    repository.insertLocalPhoto(updated)
                    _selectedPhotoId.value = updated.id
                } else {
                    val onStage = onStageRegistration.value
                    val updated = photo.copy(
                        assignedUserId = onStage?.id ?: photo.assignedUserId,
                        assignedUserName = onStage?.name ?: photo.assignedUserName,
                        isPromotedToLibrary = true
                    )
                    repository.insertLocalPhoto(updated)
                    _selectedPhotoId.value = updated.id
                    if (onStage != null) {
                        val updatedFiles = if (onStage.photoFiles.isEmpty()) photo.fileName else "${onStage.photoFiles}, ${photo.fileName}"
                        repository.updateRegistration(onStage.copy(photoFiles = updatedFiles))
                    }
                }
                onNavigateToEdit()
            }
        }
    }

    fun promotePhotosToLibrary(photos: List<LocalPhoto>) {
        viewModelScope.launch {
            val onStage = onStageRegistration.value
            photos.forEach { photo ->
                val updated = photo.copy(
                    assignedUserId = onStage?.id ?: photo.assignedUserId,
                    assignedUserName = onStage?.name ?: photo.assignedUserName,
                    isPromotedToLibrary = true
                )
                repository.insertLocalPhoto(updated)
            }
            if (onStage != null) {
                val newNames = photos.joinToString(", ") { it.fileName }
                val updatedFiles = if (onStage.photoFiles.isEmpty()) newNames else "${onStage.photoFiles}, $newNames"
                repository.updateRegistration(onStage.copy(photoFiles = updatedFiles))
            }
        }
    }

    fun setCropMode(enabled: Boolean) {
        _cropMode.value = enabled
    }

    fun updateSliders(
        brightness: Float = _editBrightness.value,
        contrast: Float = _editContrast.value,
        saturation: Float = _editSaturation.value,
        shadows: Float = _editShadows.value,
        highlights: Float = _editHighlights.value,
        whites: Float = _editWhites.value,
        blacks: Float = _editBlacks.value,
        filterName: String = _editFilterName.value,
        blurIntensity: Float = _editBlurIntensity.value,
        maskCenterX: Float = _editMaskCenterX.value,
        maskCenterY: Float = _editMaskCenterY.value,
        maskRadius: Float = _editMaskRadius.value,
        cropL: Float = _cropLeft.value,
        cropT: Float = _cropTop.value,
        cropR: Float = _cropRight.value,
        cropB: Float = _cropBottom.value,
        rating: Int = _editRating.value,
        isFlagged: Boolean = _editIsFlagged.value,
        sharpness: Float = _editSharpness.value,
        vignette: Float = _editVignette.value,
        temperature: Float = _editTemperature.value,
        tint: Float = _editTint.value,
        exposure: Float = _editExposure.value,
        straighten: Float = _editStraighten.value,
        borderType: String = _editBorderType.value,
        gpsLatitude: Double = _editGpsLatitude.value,
        gpsLongitude: Double = _editGpsLongitude.value
    ) {
        _editBrightness.value = brightness
        _editContrast.value = contrast
        _editSaturation.value = saturation
        _editShadows.value = shadows
        _editHighlights.value = highlights
        _editWhites.value = whites
        _editBlacks.value = blacks
        _editFilterName.value = filterName
        _editBlurIntensity.value = blurIntensity
        _editMaskCenterX.value = maskCenterX
        _editMaskCenterY.value = maskCenterY
        _editMaskRadius.value = maskRadius
        _cropLeft.value = cropL
        _cropTop.value = cropT
        _cropRight.value = cropR
        _cropBottom.value = cropB
        _editRating.value = rating
        _editIsFlagged.value = isFlagged
        _editSharpness.value = sharpness
        _editVignette.value = vignette
        _editTemperature.value = temperature
        _editTint.value = tint
        _editExposure.value = exposure
        _editStraighten.value = straighten
        _editBorderType.value = borderType
        _editGpsLatitude.value = gpsLatitude
        _editGpsLongitude.value = gpsLongitude

        // Save current adjustments to active photo locally
        val currentPhoto = selectedPhoto.value
        viewModelScope.launch {
            if (currentPhoto != null) {
                val updated = currentPhoto.copy(
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    shadows = shadows,
                    highlights = highlights,
                    whites = whites,
                    blacks = blacks,
                    filterName = filterName,
                    blurIntensity = blurIntensity,
                    maskCenterX = maskCenterX,
                    maskCenterY = maskCenterY,
                    maskRadius = maskRadius,
                    cropLeft = cropL,
                    cropTop = cropT,
                    cropRight = cropR,
                    cropBottom = cropB,
                    rating = rating,
                    isFlagged = isFlagged,
                    sharpness = sharpness,
                    vignette = vignette,
                    temperature = temperature,
                    tint = tint,
                    exposure = exposure,
                    straighten = straighten,
                    borderType = borderType,
                    gpsLatitude = gpsLatitude,
                    gpsLongitude = gpsLongitude
                )
                repository.insertLocalPhoto(updated)
            }

            // Persist the latest editing adjustments as session settings
            val s = repository.getSettings()
            repository.saveSettings(s.copy(
                lastAppliedBrightness = brightness,
                lastAppliedContrast = contrast,
                lastAppliedSaturation = saturation,
                lastAppliedShadows = shadows,
                lastAppliedHighlights = highlights,
                lastAppliedFilter = filterName,
                lastAppliedBlur = blurIntensity,
                lastAppliedMaskRadius = maskRadius
            ))
        }
    }

    fun selectNextPhoto() {
        val photos = allPhotos.value
        if (photos.isNotEmpty()) {
            val currentIndex = photos.indexOfFirst { it.id == _selectedPhotoId.value }
            val nextIndex = if (currentIndex == -1 || currentIndex == photos.lastIndex) 0 else currentIndex + 1
            _selectedPhotoId.value = photos[nextIndex].id
        }
    }

    fun selectPreviousPhoto() {
        val photos = allPhotos.value
        if (photos.isNotEmpty()) {
            val currentIndex = photos.indexOfFirst { it.id == _selectedPhotoId.value }
            val prevIndex = if (currentIndex <= 0) photos.lastIndex else currentIndex - 1
            _selectedPhotoId.value = photos[prevIndex].id
        }
    }

    fun bringPhotoToEditor(photo: LocalPhoto) {
        viewModelScope.launch {
            repository.insertLocalPhoto(photo)
            _selectedPhotoId.value = photo.id
            Log.d(tag, "Brought photo ${photo.fileName} into local editor.")
        }
    }

    fun importPhotosFromCameraSDCard() {
        viewModelScope.launch {
            _isPublishing.value = true
            val connector = activePtpConnector
            val onStage = onStageRegistration.value
            var importedCount = 0
            if (connector != null) {
                val handles = withContext(Dispatchers.IO) { connector.getAllObjectHandles() }
                if (handles.isNotEmpty()) {
                    val existingUris = repository.allPhotos.first().map { it.id }
                    withContext(Dispatchers.IO) {
                        for (handle in handles) {
                            val id = handle.toString()
                            if (id !in existingUris) {
                                val bytes = connector.getObjectBytes(handle)
                                if (bytes != null) {
                                    val isRaw = bytes.size >= 4 && ((bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte()) || (bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte()))
                                    val ext = if (isRaw) "NEF" else "JPG"
                                    val fileName = "DSC_${handle}.$ext"
                                    val evName = activeConfig.value?.eventName ?: "Shoot Event"
                                    val evId = activeConfig.value?.id ?: "session_active"
                                    val cachedPath = repository.saveBytesToStructuredStorage(
                                        data = bytes,
                                        rawFileName = fileName,
                                        eventName = evName,
                                        eventId = evId,
                                        clientName = onStage?.name,
                                        clientId = onStage?.id
                                    )
                                    val localPhoto = LocalPhoto(
                                        id = cachedPath.hashCode().toString(),
                                        fileUri = cachedPath,
                                        fileName = fileName,
                                        timestamp = System.currentTimeMillis() - (handles.indexOf(handle) * 10000),
                                        brightness = 1.0f,
                                        contrast = 1.0f,
                                        saturation = 1.0f,
                                        shadows = 0.0f,
                                        highlights = 0.0f,
                                        filterName = "None",
                                        blurIntensity = 0.0f,
                                        maskRadius = 0.3f,
                                        rating = 0,
                                        isFlagged = false,
                                        projectId = _selectedProjectId.value,
                                        assignedUserId = onStage?.id ?: "",
                                        assignedUserName = onStage?.name ?: ""
                                    )
                                    repository.insertLocalPhoto(localPhoto)
                                    importedCount++

                                    if (onStage != null) {
                                        val updatedFiles = if (onStage.photoFiles.isEmpty()) fileName else "${onStage.photoFiles}, $fileName"
                                        val updatedReg = onStage.copy(
                                            photoFiles = updatedFiles
                                        )
                                        repository.updateRegistration(updatedReg)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (importedCount > 0) {
                _publishStatus.value = "Successfully imported $importedCount photo(s) from camera SD card."
            } else {
                _publishStatus.value = "No new photo files found on connected camera SD card."
            }
            _isPublishing.value = false
        }
    }

    private suspend fun generateBeautifulMockStudioPhotos() = withContext(Dispatchers.IO) {
        val width = 1024
        val height = 768
        val colors = listOf(
            Pair("#2C1A1D", "#4A282D"), // Warm mahogany
            Pair("#1B263B", "#415A77"), // Studio classic blue
            Pair("#132A13", "#31572C"), // Emerald backdrop
            Pair("#2B1B17", "#5C4033")  // Dark chocolate/amber
        )
        val subjectNames = listOf("Milo the Golden Retriever", "Luna the Persian Cat", "Winston the Bulldog", "Bella the Shiba Inu")
        for (i in 0 until 4) {
            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val paint = android.graphics.Paint().apply { isAntiAlias = true }
            
            // Draw backdrop gradient
            val bgGrad = android.graphics.RadialGradient(
                width / 2f, height / 2f, width * 0.8f,
                android.graphics.Color.parseColor(colors[i].second),
                android.graphics.Color.parseColor(colors[i].first),
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = bgGrad
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            
            // Draw spotlight
            val spotPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#30FFFFFF")
                style = android.graphics.Paint.Style.FILL
                maskFilter = android.graphics.BlurMaskFilter(120f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(width / 2f, height * 0.4f, width * 0.3f, spotPaint)
            
            // Draw stylized portrait animal head shape
            paint.color = android.graphics.Color.parseColor("#D4A373")
            canvas.drawCircle(width / 2f, height / 2f, 140f, paint)
            
            // Muzzle
            paint.color = android.graphics.Color.parseColor("#FAEDCD")
            canvas.drawCircle(width / 2f, height / 2f + 40f, 60f, paint)
            
            // Nose
            paint.color = android.graphics.Color.parseColor("#212529")
            canvas.drawCircle(width / 2f, height / 2f + 20f, 20f, paint)
            
            // Eyes
            paint.color = android.graphics.Color.parseColor("#343A40")
            canvas.drawCircle(width / 2f - 45f, height / 2f - 15f, 16f, paint)
            canvas.drawCircle(width / 2f + 45f, height / 2f - 15f, 16f, paint)
            
            // Frame border
            val framePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#22FFFFFF")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRect(30f, 30f, width - 30f, height - 30f, framePaint)
            
            val cachedPath = repository.saveImageToCache(bmp, "nikon_sd_mock")
            val localPhoto = LocalPhoto(
                id = cachedPath.hashCode().toString(),
                fileUri = cachedPath,
                fileName = "NIKON_DSC_000${i+1}.JPG",
                timestamp = System.currentTimeMillis() - (i * 60000),
                brightness = 1.0f,
                contrast = 1.0f,
                saturation = 1.0f,
                shadows = 0.0f,
                highlights = 0.0f,
                filterName = "None",
                blurIntensity = 0.0f,
                maskRadius = 0.3f,
                rating = 4 - i % 2,
                isFlagged = (i % 2 == 0),
                projectId = _selectedProjectId.value
            )
            repository.insertLocalPhoto(localPhoto)
        }
    }

    fun saveEditingAsPreset() {
        viewModelScope.launch {
            val s = repository.getSettings()
            val updated = s.copy(
                presetBrightness = _editBrightness.value,
                presetContrast = _editContrast.value,
                presetSaturation = _editSaturation.value,
                presetShadows = _editShadows.value,
                presetHighlights = _editHighlights.value,
                presetFilter = _editFilterName.value,
                presetBlur = _editBlurIntensity.value,
                presetMaskRadius = _editMaskRadius.value
            )
            repository.saveSettings(updated)
            Log.d(tag, "Saved current editing settings as local auto-apply preset")
        }
    }

    data class CopiedEdits(
        val brightness: Float = 1.0f,
        val contrast: Float = 1.0f,
        val saturation: Float = 1.0f,
        val shadows: Float = 0.0f,
        val highlights: Float = 0.0f,
        val whites: Float = 0.0f,
        val blacks: Float = 0.0f,
        val filterName: String = "None",
        val temperature: Float = 0.0f,
        val tint: Float = 0.0f,
        val exposure: Float = 0.0f,
        val vignette: Float = 0.0f,
        val sharpness: Float = 0.0f,
        val straighten: Float = 0.0f,
        val cropLeft: Float = 0.0f,
        val cropTop: Float = 0.0f,
        val cropRight: Float = 1.0f,
        val cropBottom: Float = 1.0f,
        val cropMode: Boolean = false
    )

    private var copiedEditsCache: CopiedEdits? = null
    val hasCopiedEdits = MutableStateFlow(false)

    fun copyEdits() {
        copiedEditsCache = CopiedEdits(
            brightness = _editBrightness.value,
            contrast = _editContrast.value,
            saturation = _editSaturation.value,
            shadows = _editShadows.value,
            highlights = _editHighlights.value,
            whites = _editWhites.value,
            blacks = _editBlacks.value,
            filterName = _editFilterName.value,
            temperature = _editTemperature.value,
            tint = _editTint.value,
            exposure = _editExposure.value,
            vignette = _editVignette.value,
            sharpness = _editSharpness.value,
            straighten = _editStraighten.value,
            cropLeft = _cropLeft.value,
            cropTop = _cropTop.value,
            cropRight = _cropRight.value,
            cropBottom = _cropBottom.value,
            cropMode = _cropMode.value
        )
        hasCopiedEdits.value = true
    }

    fun pasteEdits() {
        val cache = copiedEditsCache ?: return
        _cropLeft.value = cache.cropLeft
        _cropTop.value = cache.cropTop
        _cropRight.value = cache.cropRight
        _cropBottom.value = cache.cropBottom
        _cropMode.value = cache.cropMode
        updateSliders(
            brightness = cache.brightness,
            contrast = cache.contrast,
            saturation = cache.saturation,
            shadows = cache.shadows,
            highlights = cache.highlights,
            whites = cache.whites,
            blacks = cache.blacks,
            filterName = cache.filterName,
            temperature = cache.temperature,
            tint = cache.tint,
            exposure = cache.exposure,
            vignette = cache.vignette,
            sharpness = cache.sharpness,
            straighten = cache.straighten,
            cropL = cache.cropLeft,
            cropT = cache.cropTop,
            cropR = cache.cropRight,
            cropB = cache.cropBottom
        )
    }

    fun toggleAutoApplyPresets(enabled: Boolean) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.saveSettings(s.copy(autoApplyPresets = enabled))
        }
    }

    fun toggleSimulatedConnection(enabled: Boolean) {
        _simulatedConnection.value = enabled
        updateCameraStatus()
    }

    fun setTetherDirectory(uriString: String?) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.saveSettings(s.copy(tetherDirectoryUri = uriString))
            if (!uriString.isNullOrEmpty()) {
                startFolderMonitoring()
            } else {
                stopFolderMonitoring()
            }
        }
    }

    fun setExportDirectory(uriString: String?) {
        viewModelScope.launch {
            val s = repository.getSettings()
            repository.saveSettings(s.copy(exportDirectoryUri = uriString))
        }
    }

    // --- Folder Monitor Engine ---
    private fun startFolderMonitoring() {
        folderMonitorJob?.cancel()
        _isFolderPolling.value = true
        folderMonitorJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val s = repository.getSettings()
                val dirUriStr = s.tetherDirectoryUri
                if (dirUriStr.isNullOrEmpty()) {
                    _isFolderPolling.value = false
                    break
                }
                
                try {
                    val dirUri = Uri.parse(dirUriStr)
                    val treeId = DocumentsContract.getTreeDocumentId(dirUri)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, treeId)
                    context.contentResolver.query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(idIndex)
                            val name = cursor.getString(nameIndex) ?: "Photo.jpg"
                            val mime = cursor.getString(mimeIndex)
                            
                            if (mime != null && (mime.startsWith("image/") || name.endsWith(".jpg", true) || name.endsWith(".png", true))) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(dirUri, docId)
                                val id = fileUri.toString().hashCode().toString()
                                val existing = repository.allPhotos.first().find { it.id == id }
                                if (existing == null) {
                                    importTetherPhoto(fileUri, name)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error polling tether directory", e)
                }
                
                delay(3000) // Poll every 3 seconds for new tethered shots
            }
        }
    }

    private fun stopFolderMonitoring() {
        folderMonitorJob?.cancel()
        folderMonitorJob = null
        _isFolderPolling.value = false
    }

    private var activePtpConnector: PtpCameraConnector? = null
    private var activePtpIpConnector: PtpIpCameraConnector? = null
    private var activeUsbCameraConnector: UsbCameraConnector? = null
    private var usbEventListenerJob: Job? = null
    private var lastDiscoveredDeviceName: String? = null
    private var lastDiscoveryResult: Boolean? = null
    private var lastDiscoveryError: String? = null

    fun connectPtpIpCamera(ipAddress: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _cameraConnectionState.value = CameraConnectionState.Error("Connecting PTP/IP to $ipAddress:15740...")
            activePtpIpConnector?.close()
            val connector = PtpIpCameraConnector(ipAddress)
            val success = connector.connect()
            if (success) {
                activePtpIpConnector = connector
                _cameraConnectionState.value = CameraConnectionState.Connected(
                    model = "Nikon Z (PTP/IP Wi-Fi)",
                    aperture = "f/2.8",
                    shutter = "1/160",
                    iso = "ISO 400",
                    battery = "90%",
                    focusMode = "AF-C"
                )
                Log.d(tag, "PTP/IP Wi-Fi Camera connected at $ipAddress:15740!")

                // Start collecting PTP/IP Event Stream for real-time photo tethering
                connector.eventFlow.collect { event ->
                    when (event) {
                        is PtpIpCameraConnector.PtpIpEvent.ObjectAdded -> {
                            Log.d(tag, "PTP/IP Event: Photo captured on camera! Handle=${event.objectHandle}, isSdram=${event.isSdram}")
                            fetchAndIngestPtpIpObject(connector, event.objectHandle)
                        }
                        is PtpIpCameraConnector.PtpIpEvent.ConnectionError -> {
                            Log.e(tag, "PTP/IP Connection error: ${event.message}")
                            _cameraConnectionState.value = CameraConnectionState.Error("PTP/IP Error: ${event.message}")
                        }
                        else -> {}
                    }
                }
            } else {
                activePtpIpConnector = null
                _cameraConnectionState.value = CameraConnectionState.Error("Failed to connect PTP/IP at $ipAddress:15740")
            }
        }
    }

    private fun updateCameraStatus() {
        if (_simulatedConnection.value) {
            _cameraConnectionState.value = CameraConnectionState.Connected(
                model = "Nikon Z6 II (Simulated)",
                aperture = "f/2.8",
                shutter = "1/160",
                iso = "ISO 800",
                battery = "85%",
                focusMode = "AF-C"
            )
            return
        }

        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (usbManager == null) {
            _cameraConnectionState.value = CameraConnectionState.Disconnected
            activePtpConnector?.close()
            activePtpConnector = null
            return
        }

        val deviceList = usbManager.deviceList
        // Nikon Vendor ID is 1200 (0x04B0). Standard Imaging (PTP/MTP) class is 6.
        val nikonDevice = deviceList.values.find { device ->
            device.vendorId == 1200 || device.vendorId == 0x04B0 || device.deviceClass == 6
        }

        if (nikonDevice != null) {
            val manufacturer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                nikonDevice.manufacturerName
            } else null
            
            val productName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                nikonDevice.productName
            } else null

            var modelName = when {
                !productName.isNullOrEmpty() -> productName
                !manufacturer.isNullOrEmpty() -> "$manufacturer Camera"
                else -> {
                    when (nikonDevice.productId) {
                        0x0446 -> "Nikon Z6 II"
                        0x0447 -> "Nikon Z7 II"
                        0x0448 -> "Nikon Z9"
                        0x0449 -> "Nikon Z fc"
                        0x044a -> "Nikon Z5"
                        0x044b -> "Nikon Z30"
                        else -> "Nikon Z Series Camera"
                    }
                }
            }

            // Check if we have permission
            if (!usbManager.hasPermission(nikonDevice)) {
                _cameraConnectionState.value = CameraConnectionState.Disconnected
                activePtpConnector?.close()
                activePtpConnector = null

                // Request USB Permission
                val intent = android.content.Intent("com.android.example.USB_PERMISSION")
                val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
                val permissionIntent = android.app.PendingIntent.getBroadcast(context, 0, intent, flags)
                usbManager.requestPermission(nikonDevice, permissionIntent)
                return
            }

            // Run capability discovery before opening the full PTP session (only if not already connected)
            if (activePtpConnector == null) {
                val currentDeviceName = nikonDevice.deviceName
                val isCompatible: Boolean
                val errorMsg: String?
                
                if (lastDiscoveredDeviceName == currentDeviceName && lastDiscoveryResult != null) {
                    isCompatible = lastDiscoveryResult!!
                    errorMsg = lastDiscoveryError
                } else {
                    val discoveryService = NikonCapabilityDiscoveryService(context)
                    val discoveryResult = discoveryService.discoverCapabilities(nikonDevice)
                    isCompatible = discoveryResult.isCompatible
                    errorMsg = discoveryResult.errorReason ?: "Unsupported PTP Device"
                    
                    if (discoveryResult.model != "Unknown") {
                        modelName = discoveryResult.model
                    }
                    
                    lastDiscoveredDeviceName = currentDeviceName
                    lastDiscoveryResult = isCompatible
                    lastDiscoveryError = errorMsg
                }
                
                if (!isCompatible) {
                    Log.e(tag, "Nikon compatibility discovery failed: ${errorMsg ?: "Device not supported"}")
                    _cameraConnectionState.value = CameraConnectionState.Error(
                        errorMsg ?: "Unsupported PTP Device"
                    )
                    mtpPollerJob?.cancel()
                    mtpPollerJob = null
                    return
                }
            }

            // We have permission & compatible device, ensure session is open
            var connector = activePtpConnector
            if (connector == null) {
                connector = PtpCameraConnector(context, nikonDevice)
                if (connector.openSession()) {
                    activePtpConnector = connector
                    startMtpEventPoller(connector)
                } else {
                    connector.close()
                    connector = null
                }
            } else if (mtpPollerJob == null || mtpPollerJob?.isActive == false) {
                startMtpEventPoller(connector)
            }

            // Open dedicated UsbCameraConnector for Interrupt IN event flow & clean JPEG extraction
            if (activeUsbCameraConnector == null) {
                val usbConn = UsbCameraConnector(context, nikonDevice)
                if (usbConn.openSession()) {
                    activeUsbCameraConnector = usbConn
                    startUsbEventListener(usbConn)
                } else {
                    usbConn.close()
                }
            }

            // Query live exposure info from camera
            val info = activePtpConnector?.getCameraExposureInfo()
            
            val currentState = _cameraConnectionState.value
            val prevAperture = if (currentState is CameraConnectionState.Connected) currentState.aperture else "-"
            val prevShutter = if (currentState is CameraConnectionState.Connected) currentState.shutter else "-"
            val prevIso = if (currentState is CameraConnectionState.Connected) currentState.iso else "-"
            val prevBattery = if (currentState is CameraConnectionState.Connected) currentState.battery else "-"

            _cameraConnectionState.value = CameraConnectionState.Connected(
                model = modelName,
                aperture = if (info != null && info.aperture != "-") info.aperture else prevAperture,
                shutter = if (info != null && info.shutter != "-") info.shutter else prevShutter,
                iso = if (info != null && info.iso != "-") info.iso else prevIso,
                battery = if (info != null && info.battery != "-") info.battery else prevBattery,
                focusMode = if (info != null) "AF-C" else "-"
            )

            // Camera connected cleanly
            Log.d(tag, "Camera connected: $modelName. PTP session ready for manual file import.")
        } else {
            mtpPollerJob?.cancel()
            mtpPollerJob = null
            usbEventListenerJob?.cancel()
            usbEventListenerJob = null
            activePtpConnector?.close()
            activePtpConnector = null
            activeUsbCameraConnector?.close()
            activeUsbCameraConnector = null
            lastDiscoveredDeviceName = null
            lastDiscoveryResult = null
            lastDiscoveryError = null
            _cameraConnectionState.value = CameraConnectionState.Disconnected
        }
    }

    private suspend fun importTetherPhoto(fileUri: Uri, fileName: String) = withContext(Dispatchers.IO) {
        try {
            // Load, compress to internal cache directory, and apply active presets
            val bitmap = decodeUriToBitmap(context, fileUri)

            if (bitmap != null) {
                val cachedPath = repository.saveImageToCache(bitmap, "tether")
                
                val s = repository.getSettings()
                val onStage = onStageRegistration.value

                val brightness = if (s.autoApplyPresets) s.presetBrightness else 1.0f
                val contrast = if (s.autoApplyPresets) s.presetContrast else 1.0f
                val saturation = if (s.autoApplyPresets) s.presetSaturation else 1.0f
                val shadows = if (s.autoApplyPresets) s.presetShadows else 0.0f
                val highlights = if (s.autoApplyPresets) s.presetHighlights else 0.0f
                val filterName = if (s.autoApplyPresets) s.presetFilter else "None"
                val blurIntensity = if (s.autoApplyPresets) s.presetBlur else 0.0f
                val maskRadius = if (s.autoApplyPresets) s.presetMaskRadius else 0.3f

                val lat = if (_tetherGpsEnabled.value) _tetherGpsLatitude.value else 0.0
                val lon = if (_tetherGpsEnabled.value) _tetherGpsLongitude.value else 0.0
                if (_tetherGpsEnabled.value) {
                    writeGpsToExif(cachedPath, lat, lon)
                }

                val localPhoto = LocalPhoto(
                    id = cachedPath.hashCode().toString(),
                    fileUri = cachedPath,
                    fileName = fileName,
                    timestamp = System.currentTimeMillis(),
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    shadows = shadows,
                    highlights = highlights,
                    filterName = filterName,
                    blurIntensity = blurIntensity,
                    maskRadius = maskRadius,
                    assignedUserId = onStage?.id,
                    assignedUserName = onStage?.name,
                    projectId = _selectedProjectId.value,
                    gpsLatitude = lat,
                    gpsLongitude = lon,
                    straighten = getExifOrientationDegrees(cachedPath)
                )
                
                repository.insertLocalPhoto(localPhoto)
                _selectedPhotoId.value = localPhoto.id
                Log.d(tag, "Successfully imported Nikon tethered image: $fileName (Auto Assigned to: ${onStage?.name ?: "None"})")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to import tethered photo: $fileName", e)
        }
    }

    // --- Camera Storage Reading & Capture Ingestion ---
    fun simulateNikonShot() {
        viewModelScope.launch {
            _isPublishing.value = true
            val connector = activePtpConnector
            val s = repository.getSettings()
            val onStage = onStageRegistration.value
            val activeSessionId = _activeConfigId.value ?: "active"

            val brightness = if (s.autoApplyPresets) s.presetBrightness else 1.0f
            val contrast = if (s.autoApplyPresets) s.presetContrast else 1.0f
            val saturation = if (s.autoApplyPresets) s.presetSaturation else 1.0f
            val shadows = if (s.autoApplyPresets) s.presetShadows else 0.0f
            val highlights = if (s.autoApplyPresets) s.presetHighlights else 0.0f
            val filterName = if (s.autoApplyPresets) s.presetFilter else "None"
            val blurIntensity = if (s.autoApplyPresets) s.presetBlur else 0.0f
            val maskRadius = if (s.autoApplyPresets) s.presetMaskRadius else 0.3f

            var ingestedPhoto: LocalPhoto? = null

            // Check physical USB camera storage handles
            if (connector != null) {
                val handles = withContext(Dispatchers.IO) { connector.getAllObjectHandles() }
                if (handles.isNotEmpty()) {
                    val lastHandle = handles.last()
                    fetchAndIngestMtpObject(connector, lastHandle)
                } else {
                    Log.d(tag, "No photo handles found on camera storage.")
                }
            } else {
                Log.d(tag, "No physical USB camera connected for reading.")
            }
            _isPublishing.value = false
        }
    }

    fun collectPayment(
        registrationId: String,
        paymentMethod: String,
        paymentAmount: Double,
        paymentRef: String,
        onSuccess: () -> Unit = {}
    ) {
        val sessionId = _activeConfigId.value ?: "active"
        viewModelScope.launch {
            val success = repository.updateClientPayment(
                sessionId = sessionId,
                registrationId = registrationId,
                paymentMethod = paymentMethod,
                paymentAmount = paymentAmount,
                paymentRef = paymentRef
            )
            if (success) {
                onSuccess()
            }
        }
    }

    fun sendClientEmailLink(context: Context, client: LocalRegistration) {
        val activeConf = activeConfig.value
        val portalUrl = "https://hi-domino-d0abb.web.app"
        val pin = if (client.portalPin.isNotEmpty()) client.portalPin else "4829"
        val photoRange = if (client.photoFiles.isNotEmpty()) client.photoFiles else "DSC005 - DSC009"

        val subject = "Your Photos - ${activeConf?.eventName ?: "Pawtraits Photography"}"
        val body = """
            Hi ${client.name},

            Thank you for attending ${activeConf?.eventName ?: "our photoshoot event"}!

            Your photos are ready in your private client portal:
            Portal Link: $portalUrl
            Email: ${client.email}
            4-Digit PIN: $pin
            Assigned Photos: $photoRange

            Best regards,
            ${activeConf?.photographerBrand ?: "David Leitch Photography"}
        """.trimIndent()

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${client.email}")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch email client intent", e)
            Toast.makeText(context, "Copied link and PIN for ${client.name}", Toast.LENGTH_LONG).show()
        }
    }

    // --- Commit & Publish Workflow ---
    // Applies adjustments, renders the final edited Bitmap, uploads to Firebase, updates Firestore databases.
    fun commitAndPublishPhoto(isHero: Boolean = false, replaceExisting: Boolean = false) {
        val currentPhoto = selectedPhoto.value ?: return
        val onStage = onStageRegistration.value
        
        if (onStage == null) {
            _onStageRegistrationId.value = currentPhoto.assignedUserId
        }

        viewModelScope.launch {
            _isPublishing.value = true
            
            // Render the edited Bitmap from local file with our adjustments
            val renderedBitmap = withContext(Dispatchers.IO) {
                renderEditedBitmap(currentPhoto)
            }

            if (renderedBitmap != null) {
                val updatedPhoto = repository.publishPhoto(
                    localPhoto = currentPhoto.copy(
                        assignedUserId = onStage?.id ?: currentPhoto.assignedUserId,
                        assignedUserName = onStage?.name ?: currentPhoto.assignedUserName
                    ),
                    editedBitmap = renderedBitmap,
                    isHero = isHero,
                    replaceExisting = replaceExisting
                )
                _selectedPhotoId.value = updatedPhoto.id
                _publishStatus.value = if (replaceExisting) {
                    "Successfully REPLACED and updated portrait in Firebase!"
                } else {
                    "Successfully PUBLISHED new portrait copy in Firebase!"
                }
                Log.d(tag, "Successfully published and updated registration metadata.")
            } else {
                _publishStatus.value = "Failed to render portrait. Please try again."
                Log.e(tag, "Failed to render adjusted bitmap prior to publish.")
            }
            _isPublishing.value = false
        }
    }

    // Helper method to load local file and render edits on a Bitmap
    private fun renderEditedBitmap(photo: LocalPhoto): Bitmap? {
        val bitmap = decodeRawOrJpeg(photo.fileUri) ?: return null
        val width = bitmap.width
        val height = bitmap.height

        // 1. Apply Cropping
        val cropL = (photo.cropLeft * width).toInt().coerceIn(0, width - 1)
        val cropT = (photo.cropTop * height).toInt().coerceIn(0, height - 1)
        val cropR = (photo.cropRight * width).toInt().coerceIn(cropL + 1, width)
        val cropB = (photo.cropBottom * height).toInt().coerceIn(cropT + 1, height)
        
        val croppedBmp = Bitmap.createBitmap(bitmap, cropL, cropT, cropR - cropL, cropB - cropT)
        val outBmp = Bitmap.createBitmap(croppedBmp.width, croppedBmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)

        val paint = Paint().apply { isAntiAlias = true }

        // 2. Compute Color Matrix using ImageFilterProcessor in the editor module
        val cm = ImageFilterProcessor.createColorMatrix(
            brightness = photo.brightness,
            contrast = photo.contrast,
            saturation = photo.saturation,
            shadows = photo.shadows,
            highlights = photo.highlights,
            exposure = photo.exposure,
            temperature = photo.temperature,
            tint = photo.tint,
            filterName = photo.filterName,
            whites = photo.whites,
            blacks = photo.blacks
        )
        if (false) {
        
        // Brightness adjustments
        val b = photo.brightness
        val brightnessMatrix = ColorMatrix(floatArrayOf(
            b, 0f, 0f, 0f, 0f,
            0f, b, 0f, 0f, 0f,
            0f, 0f, b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(brightnessMatrix)

        // Contrast adjustments
        val c = photo.contrast
        val translate = (-.5f * c + .5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, translate,
            0f, c, 0f, 0f, translate,
            0f, 0f, c, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(contrastMatrix)

        // Saturation adjustments
        val s = photo.saturation
        val saturationMatrix = ColorMatrix().apply { setSaturation(s) }
        cm.postConcat(saturationMatrix)

        // Shadows & Highlights adjustments
        // Simulate via basic high/low exposure curves
        val shadowsCoeff = photo.shadows * 30f
        val highlightsCoeff = photo.highlights * 30f
        val shadowHighlightMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, shadowsCoeff + highlightsCoeff,
            0f, 1f, 0f, 0f, shadowsCoeff + highlightsCoeff,
            0f, 0f, 1f, 0f, shadowsCoeff + highlightsCoeff,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(shadowHighlightMatrix)

        // Exposure adjustments
        val exp = photo.exposure
        val expMult = if (exp >= 0f) (1f + exp) else (1f / (1f - exp))
        val exposureMatrix = ColorMatrix(floatArrayOf(
            expMult, 0f, 0f, 0f, 0f,
            0f, expMult, 0f, 0f, 0f,
            0f, 0f, expMult, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(exposureMatrix)

        // Temperature (Warmth) adjustments (-1f..1f)
        val temp = photo.temperature
        val tempMatrix = ColorMatrix(floatArrayOf(
            1f + temp * 0.12f, 0f, 0f, 0f, temp * 15f,
            0f, 1f + temp * 0.05f, 0f, 0f, temp * 5f,
            0f, 0f, 1f - temp * 0.15f, 0f, -temp * 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(tempMatrix)

        // Tint adjustments (-1f..1f)
        val tVal = photo.tint
        val tintMatrix = ColorMatrix(floatArrayOf(
            1f + tVal * 0.05f, 0f, 0f, 0f, tVal * 5f,
            0f, 1f - tVal * 0.10f, 0f, 0f, -tVal * 10f,
            0f, 0f, 1f + tVal * 0.05f, 0f, tVal * 5f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(tintMatrix)

        // Apply filters
        when (photo.filterName) {
            "Monochrome" -> {
                val bwMatrix = ColorMatrix().apply { setSaturation(0.0f) }
                cm.postConcat(bwMatrix)
            }
            "Warm Sepia" -> {
                val sepiaMatrix = ColorMatrix(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(sepiaMatrix)
            }
            "Cinematic Vintage" -> {
                val vintageMatrix = ColorMatrix(floatArrayOf(
                    0.95f, 0f, 0f, 0f, 10f,
                    0f, 0.90f, 0f, 0f, 5f,
                    0f, 0f, 0.80f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(vintageMatrix)
            }
            "Cool Cyan" -> {
                val cyanMatrix = ColorMatrix(floatArrayOf(
                    0.80f, 0f, 0f, 0f, -15f,
                    0f, 0.95f, 0f, 0f, 10f,
                    0f, 0f, 1.10f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cyanMatrix)
            }
            "High Contrast Punch" -> {
                val punchMatrix = ColorMatrix().apply { setSaturation(1.3f) }
                val highContr = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -25f,
                    0f, 1.2f, 0f, 0f, -25f,
                    0f, 0f, 1.2f, 0f, -25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(punchMatrix)
                cm.postConcat(highContr)
            }
            "Golden Hour" -> {
                val goldenMatrix = ColorMatrix(floatArrayOf(
                    1.15f, 0f, 0f, 0f, 15f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 0.90f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(goldenMatrix)
            }
            "Forest Green" -> {
                val forestMatrix = ColorMatrix(floatArrayOf(
                    0.90f, 0f, 0f, 0f, -10f,
                    0f, 1.15f, 0f, 0f, 15f,
                    0f, 0f, 0.95f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(forestMatrix)
            }
            "Cyberpunk" -> {
                val cyberpunkMatrix = ColorMatrix(floatArrayOf(
                    1.20f, 0f, 0f, 0f, 25f,
                    0f, 0.85f, 0f, 0f, -15f,
                    0f, 0f, 1.30f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cyberpunkMatrix)
            }
            "Moody Noir" -> {
                val bwMatrix = ColorMatrix().apply { setSaturation(0.0f) }
                val noirMatrix = ColorMatrix(floatArrayOf(
                    1.30f, 0f, 0f, 0f, -35f,
                    0f, 1.30f, 0f, 0f, -35f,
                    0f, 0f, 1.30f, 0f, -35f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(bwMatrix)
                cm.postConcat(noirMatrix)
            }
            "Soft Pastel" -> {
                val pastelMatrix = ColorMatrix(floatArrayOf(
                    0.90f, 0f, 0f, 0f, 30f,
                    0f, 0.90f, 0f, 0f, 30f,
                    0f, 0f, 0.90f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                val desatMatrix = ColorMatrix().apply { setSaturation(0.8f) }
                cm.postConcat(pastelMatrix)
                cm.postConcat(desatMatrix)
            }
        }
        } // End of deactivated hardcoded matrix block

        paint.colorFilter = ColorMatrixColorFilter(cm)
        
        // Save the canvas state and apply rotation for straightening if configured
        canvas.save()
        if (photo.straighten != 0f) {
            canvas.rotate(photo.straighten, outBmp.width / 2f, outBmp.height / 2f)
        }
        canvas.drawBitmap(croppedBmp, 0f, 0f, paint)
        canvas.restore()

        // 3. Apply Focal Blur Masking if enabled
        if (photo.blurIntensity > 0.0f) {
            val maskX = photo.maskCenterX * croppedBmp.width
            val maskY = photo.maskCenterY * croppedBmp.height
            val maskR = photo.maskRadius * croppedBmp.width

            // Render a blurred version of the current cropped canvas
            val blurredBmp = Bitmap.createBitmap(croppedBmp.width, croppedBmp.height, Bitmap.Config.ARGB_8888)
            val blurCanvas = Canvas(blurredBmp)
            
            // We draw the base image onto blur canvas
            blurCanvas.drawBitmap(outBmp, 0f, 0f, null)
            
            // Simulate background blur using soft canvas drawing or simple radial gradient blending
            val blurPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            
            // To mask: We overlay a radial gradient where the center focal region is transparent,
            // and the background fades to blurred or tinted studio atmosphere.
            val shader = RadialGradient(
                maskX, maskY, maskR * 1.5f,
                Color.TRANSPARENT,
                Color.argb((photo.blurIntensity * 180).toInt().coerceIn(0, 255), 15, 10, 10), // fade to rich dark focal blur shadow
                Shader.TileMode.CLAMP
            )
            blurPaint.shader = shader
            canvas.drawRect(0f, 0f, croppedBmp.width.toFloat(), croppedBmp.height.toFloat(), blurPaint)
        }

        // Apply Focal Blur Brush strokes if present
        val strokes = _brushPoints.value
        if (strokes.isNotEmpty()) {
            val brushPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            val intensity = if (photo.blurIntensity > 0f) photo.blurIntensity else 0.7f
            for (pt in strokes) {
                val px = pt.x * croppedBmp.width
                val py = pt.y * croppedBmp.height
                val pr = pt.radius * croppedBmp.width
                if (pt.type == "BOKEH") {
                    val shader = RadialGradient(
                        px, py, pr,
                        Color.argb((intensity * 140).toInt().coerceIn(0, 255), 245, 230, 210), // warm bokeh glow
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    brushPaint.shader = shader
                    canvas.drawCircle(px, py, pr, brushPaint)
                } else if (pt.type == "SHADOW") {
                    val shader = RadialGradient(
                        px, py, pr,
                        Color.argb((intensity * 160).toInt().coerceIn(0, 255), 15, 12, 12), // rich shadow defocus
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    brushPaint.shader = shader
                    canvas.drawCircle(px, py, pr, brushPaint)
                } else if (pt.type == "FACIAL_BLUR") {
                    val shader = RadialGradient(
                        px, py, pr,
                        Color.argb(170, 235, 222, 210), // beautiful soft skin blur smoothing overlay
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    brushPaint.shader = shader
                    canvas.drawCircle(px, py, pr, brushPaint)
                }
            }
        }

        // 4. Apply Vignette if enabled
        if (photo.vignette != 0.0f) {
            val cx = outBmp.width / 2f
            val cy = outBmp.height / 2f
            val radius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
            val vignettePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            val vColor = if (photo.vignette < 0f) {
                Color.argb((Math.abs(photo.vignette) * 220).toInt().coerceIn(0, 255), 255, 255, 255)
            } else {
                Color.argb((photo.vignette * 220).toInt().coerceIn(0, 255), 0, 0, 0)
            }
            val colors = intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                vColor
            )
            val positions = floatArrayOf(0f, 0.4f, 1f)
            val shader = RadialGradient(
                cx, cy, radius,
                colors,
                positions,
                Shader.TileMode.CLAMP
            )
            vignettePaint.shader = shader
            canvas.drawRect(0f, 0f, outBmp.width.toFloat(), outBmp.height.toFloat(), vignettePaint)
        }

        // 5. Apply Borders if enabled
        if (photo.borderType != "None" && photo.borderType.isNotEmpty()) {
            val borderWidth = (minOf(outBmp.width, outBmp.height) * 0.05f).coerceAtLeast(10f)
            val borderPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = borderWidth
                color = if (photo.borderType == "Classic White") Color.WHITE else Color.BLACK
            }
            val halfBorder = borderWidth / 2f
            canvas.drawRect(
                halfBorder,
                halfBorder,
                outBmp.width - halfBorder,
                outBmp.height - halfBorder,
                borderPaint
            )

            if (photo.borderType == "Classic White") {
                val keylinePaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    color = Color.DKGRAY
                }
                canvas.drawRect(
                    borderWidth,
                    borderWidth,
                    outBmp.width - borderWidth,
                    outBmp.height - borderWidth,
                    keylinePaint
                )
            }
        }

        croppedBmp.recycle()
        if (photo.sharpness > 0.0f) {
            val sharpened = sharpenBitmap(outBmp, photo.sharpness)
            outBmp.recycle()
            return sharpened
        }
        return outBmp
    }

    fun recallSessionSettings() {
        viewModelScope.launch {
            val s = repository.getSettings()
            updateSliders(
                brightness = s.lastAppliedBrightness,
                contrast = s.lastAppliedContrast,
                saturation = s.lastAppliedSaturation,
                shadows = s.lastAppliedShadows,
                highlights = s.lastAppliedHighlights,
                filterName = s.lastAppliedFilter,
                blurIntensity = s.lastAppliedBlur,
                maskRadius = s.lastAppliedMaskRadius
            )
        }
    }

    fun assignPhotoToClient(photo: LocalPhoto, client: LocalRegistration) {
        viewModelScope.launch {
            val updated = photo.copy(
                assignedUserId = client.id,
                assignedUserName = client.name
            )
            repository.insertLocalPhoto(updated)
            Log.d(tag, "Photo ${photo.fileName} assigned to ${client.name}")
        }
    }

    fun promotePhotoToLibrary(photo: LocalPhoto) {
        viewModelScope.launch {
            val onStage = onStageRegistration.value
            val updated = photo.copy(
                assignedUserId = onStage?.id ?: photo.assignedUserId,
                assignedUserName = onStage?.name ?: photo.assignedUserName,
                isPromotedToLibrary = true
            )
            repository.insertLocalPhoto(updated)
            if (onStage != null) {
                val updatedFiles = if (onStage.photoFiles.isEmpty()) photo.fileName else "${onStage.photoFiles}, ${photo.fileName}"
                repository.updateRegistration(onStage.copy(photoFiles = updatedFiles))
            }
            Log.d(tag, "Promoted photo ${photo.fileName} to Library and assigned to ${onStage?.name ?: "General"}")
        }
    }

    fun manuallyCreateClient(name: String, email: String, pets: String, notes: String, onSuccess: () -> Unit) {
        val eventId = _activeConfigId.value ?: return
        viewModelScope.launch {
            val success = repository.manuallyCreateClient(eventId, name, email, pets, notes)
            if (success) {
                onSuccess()
            }
        }
    }

    fun resetEdits() {
        updateSliders(
            brightness = 1.0f,
            contrast = 1.0f,
            saturation = 1.0f,
            shadows = 0.0f,
            highlights = 0.0f,
            filterName = "None",
            blurIntensity = 0.0f,
            maskCenterX = 0.5f,
            maskCenterY = 0.5f,
            maskRadius = 0.3f,
            cropL = 0.0f,
            cropT = 0.0f,
            cropR = 1.0f,
            cropB = 1.0f,
            straighten = 0.0f,
            borderType = "None",
            gpsLatitude = 0.0,
            gpsLongitude = 0.0
        )
    }

    // --- Standalone & Photoshoot Creation Support ---
    fun createPhotoshootEvent(name: String, location: String, date: String) {
        viewModelScope.launch {
            val uuid = UUID.randomUUID().toString()
            val newConfig = LocalConfig(
                id = uuid,
                eventName = name,
                eventLocation = location,
                eventDate = date,
                photographerBrand = "Standalone Studio",
                logoUrl = null,
                portalTheme = "classic"
            )
            repository.insertLocalConfig(newConfig)
            selectEvent(newConfig)
        }
    }

    fun enableStandaloneMode() {
        viewModelScope.launch {
            val standaloneConfig = LocalConfig(
                id = "standalone_session",
                eventName = "Standalone Tether Mode",
                eventLocation = "Local Tablet",
                eventDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                photographerBrand = "Standalone Studio"
            )
            repository.insertLocalConfig(standaloneConfig)
            selectEvent(standaloneConfig)
        }
    }

    // --- RAW Image & Thumbnail Decoders with Memory-Safe Downsampling ---
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun decodeSampledBitmapFromBytes(imageBytes: ByteArray, reqWidth: Int = 2048, reqHeight: Int = 2048): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inMutable = true
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
        } catch (e: Exception) {
            Log.e(tag, "Failed to decode sampled bitmap from bytes", e)
            null
        }
    }

    private fun decodeRawOrJpeg(fileUri: String, reqWidth: Int = 2048, reqHeight: Int = 2048): Bitmap? {
        val isNef = fileUri.lowercase().endsWith(".nef") || fileUri.lowercase().endsWith(".raw")
        if (isNef) {
            val bitmap = if (fileUri.startsWith("content://")) {
                NefImageDecodingService.decodeNefPreviewFromUri(context, Uri.parse(fileUri))
            } else {
                NefImageDecodingService.decodeNefPreview(context, fileUri)
            }
            if (bitmap != null) return bitmap
        }
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (fileUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }
            } else {
                BitmapFactory.decodeFile(fileUri, boundsOptions)
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight)
                inMutable = true
            }

            if (fileUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                }
            } else {
                BitmapFactory.decodeFile(fileUri, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to decode photo: $fileUri", e)
            null
        }
    }

    private fun decodeUriToBitmap(context: Context, uri: Uri, reqWidth: Int = 2048, reqHeight: Int = 2048): Bitmap? {
        val path = uri.path ?: ""
        val isNef = path.lowercase().endsWith(".nef") || path.lowercase().endsWith(".raw") || uri.toString().lowercase().endsWith(".nef")
        if (isNef) {
            val bitmap = NefImageDecodingService.decodeNefPreviewFromUri(context, uri)
            if (bitmap != null) return bitmap
        }
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight)
                inMutable = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to decode Uri: $uri", e)
            null
        }
    }

    // --- MTP Real-Time Poller & Automated Ingest ---
    private fun startMtpEventPoller(connector: PtpCameraConnector) {
        mtpPollerJob?.cancel()
        mtpPollerJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(tag, "MTP Camera Poller successfully active.")
            var lastHandles = emptySet<Int>()
            try {
                lastHandles = connector.getAllObjectHandles().toSet()
                Log.d(tag, "MTP: Initial camera photo handles count: ${lastHandles.size}")
            } catch (e: Exception) {
                Log.e(tag, "MTP: Failed to fetch initial object handles", e)
            }
            var pollCounter = 0
            var exposureCounter = 0
            while (isActive) {
                try {
                    // 1. Check MTP interrupt event for immediate notice when shutter fires
                    val eventHandle = connector.checkMtpEvent()
                    if (eventHandle != null && eventHandle > 0 && !lastHandles.contains(eventHandle)) {
                        lastHandles = lastHandles + eventHandle
                        Log.d(tag, "MTP Event: ObjectAdded handle $eventHandle")
                        fetchAndIngestMtpObject(connector, eventHandle)
                    }

                    // 2. Polling fallback every 1.5s (15 * 100ms)
                    pollCounter++
                    if (pollCounter >= 15) {
                        pollCounter = 0
                        val currentHandles = connector.getAllObjectHandles().toSet()
                        val newHandles = currentHandles - lastHandles
                        if (newHandles.isNotEmpty()) {
                            Log.d(tag, "MTP: Detected ${newHandles.size} new photo handle(s) on camera!")
                            for (handle in newHandles) {
                                fetchAndIngestMtpObject(connector, handle)
                            }
                            lastHandles = currentHandles
                        }
                    }

                    // 3. Live camera settings refresh every 3s (30 * 100ms)
                    exposureCounter++
                    if (exposureCounter >= 30) {
                        exposureCounter = 0
                        val info = connector.getCameraExposureInfo()
                        val currentState = _cameraConnectionState.value
                        if (currentState is CameraConnectionState.Connected) {
                            val newAperture = if (info.aperture != "-") info.aperture else currentState.aperture
                            val newShutter = if (info.shutter != "-") info.shutter else currentState.shutter
                            val newIso = if (info.iso != "-") info.iso else currentState.iso
                            val newBattery = if (info.battery != "-") info.battery else currentState.battery
                            _cameraConnectionState.value = currentState.copy(
                                aperture = newAperture,
                                shutter = newShutter,
                                iso = newIso,
                                battery = newBattery
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error in MTP polling loop", e)
                }
                delay(100)
            }
        }
    }

    private suspend fun fetchAndIngestMtpObject(connector: PtpCameraConnector, handle: Int) {
        Log.d(tag, "MTP: Pulling image handle $handle immediately into storage...")
        val bytes = withContext(Dispatchers.IO) { connector.getObjectBytes(handle) }
        if (bytes != null && bytes.isNotEmpty()) {
            val formattedName = String.format("DSC%04d.JPG", Math.abs(handle) % 10000)
            val onStage = onStageRegistration.value
            val evName = activeConfig.value?.eventName ?: "Shoot Event"
            val evId = activeConfig.value?.id ?: "session_active"

            val cachedPath = repository.saveBytesToStructuredStorage(
                data = bytes,
                rawFileName = formattedName,
                eventName = evName,
                eventId = evId,
                clientName = onStage?.name,
                clientId = onStage?.id
            )

            val s = repository.getSettings()
            val brightness = if (s.autoApplyPresets) s.presetBrightness else 1.0f
            val contrast = if (s.autoApplyPresets) s.presetContrast else 1.0f
            val saturation = if (s.autoApplyPresets) s.presetSaturation else 1.0f
            val shadows = if (s.autoApplyPresets) s.presetShadows else 0.0f
            val highlights = if (s.autoApplyPresets) s.presetHighlights else 0.0f
            val filterName = if (s.autoApplyPresets) s.presetFilter else "None"
            val blurIntensity = if (s.autoApplyPresets) s.presetBlur else 0.0f
            val maskRadius = if (s.autoApplyPresets) s.presetMaskRadius else 0.3f

            val lat = if (_tetherGpsEnabled.value) _tetherGpsLatitude.value else 0.0
            val lon = if (_tetherGpsEnabled.value) _tetherGpsLongitude.value else 0.0
            if (_tetherGpsEnabled.value) {
                writeGpsToExif(cachedPath, lat, lon)
            }

            val ingestedPhoto = LocalPhoto(
                id = cachedPath.hashCode().toString(),
                fileUri = cachedPath,
                fileName = formattedName,
                timestamp = System.currentTimeMillis(),
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                shadows = shadows,
                highlights = highlights,
                filterName = filterName,
                blurIntensity = blurIntensity,
                maskRadius = maskRadius,
                assignedUserId = onStage?.id,
                assignedUserName = onStage?.name,
                projectId = _selectedProjectId.value,
                gpsLatitude = lat,
                gpsLongitude = lon,
                straighten = getExifOrientationDegrees(cachedPath),
                isPromotedToLibrary = false
            )

            repository.insertLocalPhoto(ingestedPhoto)
            _selectedPhotoId.value = ingestedPhoto.id

            // Update client's photoFiles in Firestore
            if (onStage != null) {
                val assignedPhotos = repository.allPhotos.first()
                    .filter { it.assignedUserId == onStage.id }
                    .sortedBy { it.timestamp }
                if (assignedPhotos.isNotEmpty()) {
                    val rangeText = if (assignedPhotos.size == 1) {
                        assignedPhotos.first().fileName
                    } else {
                        "${assignedPhotos.first().fileName} - ${assignedPhotos.last().fileName}"
                    }
                    repository.updateRegistration(onStage.copy(photoFiles = rangeText))
                }
            }

            Log.d(tag, "Successfully copied & ingested real camera photo to local cache: $formattedName")
        } else {
            Log.e(tag, "Failed to retrieve object bytes for handle $handle")
        }
    }

    private suspend fun fetchAndIngestPtpIpObject(connector: PtpIpCameraConnector, handle: Int) {
        Log.d(tag, "PTP/IP: Fetching object info & image bytes for handle $handle...")
        val info = connector.getObjectInfo(handle)
        val rawName = info?.fileName ?: String.format("DSC%04d.JPG", Math.abs(handle) % 10000)

        val bytes = connector.getObject(handle)
        if (bytes != null && bytes.isNotEmpty()) {
            val onStage = onStageRegistration.value
            val evName = activeConfig.value?.eventName ?: "Shoot Event"
            val evId = activeConfig.value?.id ?: "session_active"

            val cachedPath = repository.saveBytesToStructuredStorage(
                data = bytes,
                rawFileName = rawName,
                eventName = evName,
                eventId = evId,
                clientName = onStage?.name,
                clientId = onStage?.id
            )

            val s = repository.getSettings()
            val brightness = if (s.autoApplyPresets) s.presetBrightness else 1.0f
            val contrast = if (s.autoApplyPresets) s.presetContrast else 1.0f
            val saturation = if (s.autoApplyPresets) s.presetSaturation else 1.0f
            val shadows = if (s.autoApplyPresets) s.presetShadows else 0.0f
            val highlights = if (s.autoApplyPresets) s.presetHighlights else 0.0f
            val filterName = if (s.autoApplyPresets) s.presetFilter else "None"
            val blurIntensity = if (s.autoApplyPresets) s.presetBlur else 0.0f
            val maskRadius = if (s.autoApplyPresets) s.presetMaskRadius else 0.3f

            val lat = if (_tetherGpsEnabled.value) _tetherGpsLatitude.value else 0.0
            val lon = if (_tetherGpsEnabled.value) _tetherGpsLongitude.value else 0.0
            if (_tetherGpsEnabled.value) {
                writeGpsToExif(cachedPath, lat, lon)
            }

            val ingestedPhoto = LocalPhoto(
                id = cachedPath.hashCode().toString(),
                fileUri = cachedPath,
                fileName = rawName,
                timestamp = System.currentTimeMillis(),
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                shadows = shadows,
                highlights = highlights,
                filterName = filterName,
                blurIntensity = blurIntensity,
                maskRadius = maskRadius,
                assignedUserId = onStage?.id,
                assignedUserName = onStage?.name,
                projectId = _selectedProjectId.value,
                gpsLatitude = lat,
                gpsLongitude = lon,
                straighten = getExifOrientationDegrees(cachedPath),
                isPromotedToLibrary = false
            )

            repository.insertLocalPhoto(ingestedPhoto)
            _selectedPhotoId.value = ingestedPhoto.id

            if (onStage != null) {
                val assignedPhotos = repository.allPhotos.first()
                    .filter { it.assignedUserId == onStage.id }
                    .sortedBy { it.timestamp }
                if (assignedPhotos.isNotEmpty()) {
                    val rangeText = if (assignedPhotos.size == 1) {
                        assignedPhotos.first().fileName
                    } else {
                        "${assignedPhotos.first().fileName} - ${assignedPhotos.last().fileName}"
                    }
                    repository.updateRegistration(onStage.copy(photoFiles = rangeText))
                }
            }

            Log.d(tag, "Successfully imported PTP/IP camera photo to local cache: $rawName -> $cachedPath")
        } else {
            Log.e(tag, "Failed to download image bytes over PTP/IP for handle $handle")
        }
    }

    private fun startUsbEventListener(connector: UsbCameraConnector) {
        usbEventListenerJob?.cancel()
        usbEventListenerJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(tag, "USB Camera Interrupt IN listener active...")
            connector.eventFlow.collect { event ->
                when (event) {
                    is UsbCameraConnector.PtpUsbEvent.ObjectAdded -> {
                        Log.d(tag, "USB Event: ObjectAdded handle=${event.objectHandle}, isSdram=${event.isSdram}")
                        fetchAndIngestUsbObject(connector, event.objectHandle)
                    }
                    is UsbCameraConnector.PtpUsbEvent.ConnectionError -> {
                        Log.e(tag, "USB Connection Error: ${event.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun fetchAndIngestUsbObject(connector: UsbCameraConnector, handle: Int) {
        Log.d(tag, "USB PTP: Fetching object info & JPEG bytes for handle $handle...")
        val info = connector.getObjectInfo(handle)
        val rawName = info?.fileName ?: String.format("DSC%04d.JPG", Math.abs(handle) % 10000)

        val bytes = connector.getObject(handle)
        if (bytes != null && bytes.isNotEmpty()) {
            val onStage = onStageRegistration.value
            val evName = activeConfig.value?.eventName ?: "Shoot Event"
            val evId = activeConfig.value?.id ?: "session_active"

            val cachedPath = repository.saveBytesToStructuredStorage(
                data = bytes,
                rawFileName = rawName,
                eventName = evName,
                eventId = evId,
                clientName = onStage?.name,
                clientId = onStage?.id
            )

            val s = repository.getSettings()
            val brightness = if (s.autoApplyPresets) s.presetBrightness else 1.0f
            val contrast = if (s.autoApplyPresets) s.presetContrast else 1.0f
            val saturation = if (s.autoApplyPresets) s.presetSaturation else 1.0f
            val shadows = if (s.autoApplyPresets) s.presetShadows else 0.0f
            val highlights = if (s.autoApplyPresets) s.presetHighlights else 0.0f
            val filterName = if (s.autoApplyPresets) s.presetFilter else "None"
            val blurIntensity = if (s.autoApplyPresets) s.presetBlur else 0.0f
            val maskRadius = if (s.autoApplyPresets) s.presetMaskRadius else 0.3f

            val lat = if (_tetherGpsEnabled.value) _tetherGpsLatitude.value else 0.0
            val lon = if (_tetherGpsEnabled.value) _tetherGpsLongitude.value else 0.0
            if (_tetherGpsEnabled.value) {
                writeGpsToExif(cachedPath, lat, lon)
            }

            val ingestedPhoto = LocalPhoto(
                id = cachedPath.hashCode().toString(),
                fileUri = cachedPath,
                fileName = rawName,
                timestamp = System.currentTimeMillis(),
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                shadows = shadows,
                highlights = highlights,
                filterName = filterName,
                blurIntensity = blurIntensity,
                maskRadius = maskRadius,
                assignedUserId = onStage?.id,
                assignedUserName = onStage?.name,
                projectId = _selectedProjectId.value,
                gpsLatitude = lat,
                gpsLongitude = lon,
                straighten = getExifOrientationDegrees(cachedPath),
                isPromotedToLibrary = false
            )

            repository.insertLocalPhoto(ingestedPhoto)
            _selectedPhotoId.value = ingestedPhoto.id

            if (onStage != null) {
                val assignedPhotos = repository.allPhotos.first()
                    .filter { it.assignedUserId == onStage.id }
                    .sortedBy { it.timestamp }
                if (assignedPhotos.isNotEmpty()) {
                    val rangeText = if (assignedPhotos.size == 1) {
                        assignedPhotos.first().fileName
                    } else {
                        "${assignedPhotos.first().fileName} - ${assignedPhotos.last().fileName}"
                    }
                    repository.updateRegistration(onStage.copy(photoFiles = rangeText))
                }
            }

            Log.d(tag, "Successfully imported USB camera photo to local cache: $rawName -> $cachedPath")
        } else {
            Log.e(tag, "Failed to download image bytes over USB for handle $handle")
        }
    }

    private fun applyLocalDenoise(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val outPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val centerColor = pixels[idx]
                val ca = (centerColor shr 24) and 0xFF
                val cr = (centerColor shr 16) and 0xFF
                val cg = (centerColor shr 8) and 0xFF
                val cb = centerColor and 0xFF
                
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var sumW = 0f
                
                for (ky in -1..1) {
                    val py = (y + ky).coerceIn(0, height - 1)
                    for (kx in -1..1) {
                        val px = (x + kx).coerceIn(0, width - 1)
                        val neighborColor = pixels[py * width + px]
                        val nr = (neighborColor shr 16) and 0xFF
                        val ng = (neighborColor shr 8) and 0xFF
                        val nb = neighborColor and 0xFF
                        
                        val spatialW = 1f / (1f + (kx * kx + ky * ky) * 0.5f)
                        val diffR = Math.abs(cr - nr)
                        val diffG = Math.abs(cg - ng)
                        val diffB = Math.abs(cb - nb)
                        val rangeW = 1f / (1f + (diffR * diffR + diffG * diffG + diffB * diffB) * 0.002f)
                        
                        val weight = spatialW * rangeW
                        sumR += nr * weight
                        sumG += ng * weight
                        sumB += nb * weight
                        sumW += weight
                    }
                }
                val finalR = (sumR / sumW).toInt().coerceIn(0, 255)
                val finalG = (sumG / sumW).toInt().coerceIn(0, 255)
                val finalB = (sumB / sumW).toInt().coerceIn(0, 255)
                outPixels[idx] = (ca shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }

    // --- Cloud-Offloaded AI Denoise (Proxy Workflow) ---
    fun triggerCloudDenoise() {
        val currentPhoto = selectedPhoto.value ?: return
        viewModelScope.launch {
            _isPublishing.value = true
            _publishStatus.value = "Offloading AI Denoise to Cloud..."
            try {
                val originalBmp = renderEditedBitmap(currentPhoto)
                if (originalBmp == null) {
                    _publishStatus.value = "Denoise Error: Failed to render image."
                    _isPublishing.value = false
                    return@launch
                }
                
                // Downscale image to a proxy resolution (max 1024px) to preserve cloud memory and bandwidth
                val maxDim = 1024
                val scale = minOf(1f, maxDim.toFloat() / maxOf(originalBmp.width, originalBmp.height))
                val proxyBmp = if (scale < 1f) {
                    val w = (originalBmp.width * scale).toInt()
                    val h = (originalBmp.height * scale).toInt()
                    Bitmap.createScaledBitmap(originalBmp, w, h, true)
                } else {
                    originalBmp
                }

                val outStream = java.io.ByteArrayOutputStream()
                proxyBmp.compress(Bitmap.CompressFormat.JPEG, 85, outStream)
                val base64Str = android.util.Base64.encodeToString(outStream.toByteArray(), android.util.Base64.NO_WRAP)

                // MEMORY SAFETY: Proactively recycle proxy and original bitmaps to avoid OutOfMemoryError
                if (proxyBmp != originalBmp) {
                    proxyBmp.recycle()
                }

                var success = false
                try {
                    // Call Cloud Function using Retrofit proxy client
                    val baseUrl = "https://europe-west3-ais-dev-x5rwn356472izhu4vs3lqr.cloudfunctions.net"
                    val service = DenoiseClient.createService(baseUrl)
                    val response = service.denoiseImage(DenoiseRequest(imageBase64 = base64Str))

                    if (response.isSuccessful && response.body()?.denoisedImageUrl != null) {
                        val cleanUrl = response.body()!!.denoisedImageUrl!!
                        _denoisedPreviewUrl.value = cleanUrl
                        _publishStatus.value = "AI Denoise successfully offloaded and applied!"
                        Log.d(tag, "Denoised Image URL: $cleanUrl")
                        success = true
                    } else {
                        Log.w(tag, "Cloud function returned unsuccessful response, falling back to local denoise...")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Cloud denoise failed, falling back to local denoise...", e)
                }

                if (!success) {
                    // APPLY HIGH PERFORMANCE LOCAL DENOISE FALLBACK
                    _publishStatus.value = "Cloud offline. Running edge-preserving local denoise..."
                    val localDenoised = applyLocalDenoise(originalBmp)
                    val cacheFile = File(context.cacheDir, "denoised_${currentPhoto.id}.jpg")
                    java.io.FileOutputStream(cacheFile).use { fos ->
                        localDenoised.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    }
                    localDenoised.recycle()
                    _denoisedPreviewUrl.value = Uri.fromFile(cacheFile).toString()
                    _publishStatus.value = "Robust edge-preserving local denoise applied successfully!"
                }

                originalBmp.recycle()
            } catch (e: Exception) {
                _publishStatus.value = "Cloud Denoise Error: ${e.message}"
                Log.e(tag, "AI Denoise Exception", e)
            } finally {
                _isPublishing.value = false
            }
        }
    }

    fun shareSelectedPhoto(context: Context) {
        val currentPhoto = selectedPhoto.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = renderEditedBitmap(currentPhoto) ?: return@launch
            try {
                val cacheDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
                val file = File(cacheDir, "export_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(file).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                    fos.flush()
                }

                // Inject GPS EXIF info
                writeGpsToExif(file.absolutePath, currentPhoto.gpsLatitude, currentPhoto.gpsLongitude)

                val authority = "${context.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)

                exportManager.sharePhotos(listOf(uri), "Share Portrait")
            } catch (e: Exception) {
                Log.e(tag, "Failed to share photo", e)
            }
        }
    }

    fun savePhotoToGallery(context: Context) {
        val currentPhoto = selectedPhoto.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = renderEditedBitmap(currentPhoto) ?: return@launch
            try {
                val filename = "TetherStudio_${System.currentTimeMillis()}.jpg"
                val exportDir = settings.value.exportDirectoryUri
                val savedUri = exportManager.exportPhoto(
                    bitmap = bmp,
                    fileName = filename,
                    folderUriString = exportDir,
                    gpsLatitude = currentPhoto.gpsLatitude,
                    gpsLongitude = currentPhoto.gpsLongitude
                )
                
                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        _publishStatus.value = if (!exportDir.isNullOrEmpty()) {
                            "Photo exported successfully via SAF!"
                        } else {
                            "Photo saved successfully to Pictures/TetherStudio!"
                        }
                    } else {
                        _publishStatus.value = "Failed to export photo."
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to save photo to gallery", e)
                withContext(Dispatchers.Main) {
                    _publishStatus.value = "Failed to save photo to gallery: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun writeGpsToExif(filePath: String, latitude: Double, longitude: Double) {
        if (latitude == 0.0 && longitude == 0.0) return
        try {
            val exif = android.media.ExifInterface(filePath)
            
            val latRef = if (latitude >= 0) "N" else "S"
            val latDMS = dec2DMS(Math.abs(latitude))
            
            val lonRef = if (longitude >= 0) "E" else "W"
            val lonDMS = dec2DMS(Math.abs(longitude))
            
            exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE, latDMS)
            exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
            exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE, lonDMS)
            exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)
            exif.saveAttributes()
            Log.d(tag, "Successfully injected EXIF GPS: $latitude, $longitude")
        } catch (e: Exception) {
            Log.e(tag, "Failed to inject EXIF GPS metadata", e)
        }
    }

    private fun dec2DMS(coord: Double): String {
        val d = coord.toInt()
        val m = ((coord - d) * 60).toInt()
        val s = ((coord - d - m / 60.0) * 3600.0)
        return "$d/1,$m/1,${(s * 1000).toInt()}/1000"
    }

    override fun importManualImage(uri: Uri) {
        viewModelScope.launch {
            try {
                var fileName = "imported_image_${System.currentTimeMillis()}.jpg"
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                if (cursor != null) {
                    try {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                fileName = name
                            }
                        }
                    } finally {
                        cursor.close()
                    }
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }

                if (bytes != null) {
                    val onStage = onStageRegistration.value
                    val evName = activeConfig.value?.eventName ?: "Shoot Event"
                    val evId = activeConfig.value?.id ?: "session_active"
                    val cachedPath = repository.saveBytesToStructuredStorage(
                        data = bytes,
                        rawFileName = fileName,
                        eventName = evName,
                        eventId = evId,
                        clientName = onStage?.name,
                        clientId = onStage?.id
                    )
                    
                    val localPhoto = LocalPhoto(
                        id = cachedPath.hashCode().toString(),
                        fileUri = cachedPath,
                        fileName = fileName,
                        timestamp = System.currentTimeMillis(),
                        brightness = 1.0f,
                        contrast = 1.0f,
                        saturation = 1.0f,
                        shadows = 0.0f,
                        highlights = 0.0f,
                        filterName = "None",
                        blurIntensity = 0.0f,
                        maskRadius = 0.3f,
                        rating = 0,
                        isFlagged = false,
                        projectId = _selectedProjectId.value
                    )
                    repository.insertLocalPhoto(localPhoto)
                    _selectedPhotoId.value = localPhoto.id
                    Log.d(tag, "Successfully imported manual image: $fileName")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to import manual image: $uri", e)
            }
        }
    }

    fun exportAdvancedPhoto(
        context: Context,
        aspectName: String, // "Original", "1:1", "4:5", "9:16", "16:9", "1.91:1", "2:3"
        fitMode: String, // "Crop to Fit", "Fit with Letterbox"
        borderStyle: String, // "None", "Classic White", "Gallery Black", "Neon Glow", "Custom Color"
        customBorderColor: Int, // ARGB color
        borderThicknessPct: Float, // 0f to 0.15f
        splitCount: Int, // 1, 2, or 3
        isShare: Boolean = false
    ) {
        val currentPhoto = selectedPhoto.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isPublishing.value = true
                    _publishStatus.value = "Preparing advanced export..."
                }

                val sourceBmp = renderEditedBitmap(currentPhoto)
                if (sourceBmp == null) {
                    withContext(Dispatchers.Main) {
                        _isPublishing.value = false
                        _publishStatus.value = "Failed to render photo."
                    }
                    return@launch
                }

                val sw = sourceBmp.width
                val sh = sourceBmp.height

                // 1. Determine single panel aspect ratio
                val panelRatio = when (aspectName) {
                    "1:1" -> 1.0f
                    "4:5" -> 0.8f
                    "9:16" -> 9f / 16f
                    "16:9" -> 16f / 9f
                    "1.91:1" -> 1.91f
                    "2:3" -> 2f / 3f
                    else -> sw.toFloat() / sh.toFloat() // "Original"
                }

                // 2. Determine composite canvas aspect ratio
                val compositeRatio = panelRatio * splitCount

                // 3. Determine composite canvas dimensions (CW, CH)
                val CW: Int
                val CH: Int
                if (fitMode == "Crop to Fit") {
                    val srcRatio = sw.toFloat() / sh.toFloat()
                    if (srcRatio > compositeRatio) {
                        CH = sh
                        CW = (sh * compositeRatio).toInt()
                    } else {
                        CW = sw
                        CH = (sw / compositeRatio).toInt()
                    }
                } else { // "Fit with Letterbox"
                    val srcRatio = sw.toFloat() / sh.toFloat()
                    if (srcRatio > compositeRatio) {
                        CW = sw
                        CH = (sw / compositeRatio).toInt()
                    } else {
                        CH = sh
                        CW = (sh * compositeRatio).toInt()
                    }
                }

                // Create composite bitmap
                val compositeBmp = Bitmap.createBitmap(CW, CH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(compositeBmp)

                // Fill background
                val bgColor = when (borderStyle) {
                    "Classic White" -> android.graphics.Color.WHITE
                    "Gallery Black" -> android.graphics.Color.BLACK
                    "Custom Color" -> customBorderColor
                    else -> android.graphics.Color.BLACK // default letterbox color
                }
                canvas.drawColor(bgColor)

                // 4. Draw source image with chosen Fit Mode
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                if (fitMode == "Crop to Fit") {
                    val srcRatio = sw.toFloat() / sh.toFloat()
                    val srcRect: Rect
                    if (srcRatio > compositeRatio) {
                        val cropW = (sh * compositeRatio).toInt()
                        val startX = (sw - cropW) / 2
                        srcRect = Rect(startX, 0, startX + cropW, sh)
                    } else {
                        val cropH = (sw / compositeRatio).toInt()
                        val startY = (sh - cropH) / 2
                        srcRect = Rect(0, startY, sw, startY + cropH)
                    }
                    val dstRect = Rect(0, 0, CW, CH)
                    canvas.drawBitmap(sourceBmp, srcRect, dstRect, paint)
                } else { // "Fit with Letterbox"
                    val srcRatio = sw.toFloat() / sh.toFloat()
                    val dstRect: Rect
                    if (srcRatio > compositeRatio) {
                        val drawH = (CW / srcRatio).toInt()
                        val startY = (CH - drawH) / 2
                        dstRect = Rect(0, startY, CW, startY + drawH)
                    } else {
                        val drawW = (CH * srcRatio).toInt()
                        val startX = (CW - drawW) / 2
                        dstRect = Rect(startX, 0, startX + drawW, CH)
                    }
                    canvas.drawBitmap(sourceBmp, null, dstRect, paint)
                }

                // 5. Draw Borders around individual panels
                val thicknessPx = (Math.min(CW, CH) / splitCount.toFloat() * borderThicknessPct).toInt()
                if (borderStyle != "None" && thicknessPx > 0) {
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = thicknessPx.toFloat()
                    }

                    if (borderStyle == "Neon Glow") {
                        borderPaint.shader = LinearGradient(
                            0f, 0f, CW.toFloat(), CH.toFloat(),
                            intArrayOf(
                                android.graphics.Color.parseColor("#FF007F"), // Neon Pink
                                android.graphics.Color.parseColor("#00F6FF"), // Neon Cyan
                                android.graphics.Color.parseColor("#9D4EDD")  // Purple Glow
                            ),
                            null, Shader.TileMode.CLAMP
                        )
                    } else {
                        borderPaint.color = when (borderStyle) {
                            "Classic White" -> android.graphics.Color.WHITE
                            "Gallery Black" -> android.graphics.Color.BLACK
                            "Custom Color" -> customBorderColor
                            else -> android.graphics.Color.TRANSPARENT
                        }
                    }

                    val panelWidth = CW / splitCount
                    for (i in 0 until splitCount) {
                        val left = i * panelWidth + thicknessPx / 2f
                        val top = thicknessPx / 2f
                        val right = (i + 1) * panelWidth - thicknessPx / 2f
                        val bottom = CH - thicknessPx / 2f
                        canvas.drawRect(left, top, right, bottom, borderPaint)
                    }
                }

                // 6. Slice and Save/Share
                val savedUris = ArrayList<Uri>()
                val authority = "${context.packageName}.fileprovider"
                val exportDir = settings.value.exportDirectoryUri

                for (i in 0 until splitCount) {
                    val left = i * CW / splitCount
                    val right = (i + 1) * CW / splitCount
                    val sliceWidth = right - left
                    val sliceBmp = Bitmap.createBitmap(compositeBmp, left, 0, sliceWidth, CH)

                    val filename = "TetherStudio_${aspectName.replace(":", "x")}_split_${i + 1}_${System.currentTimeMillis()}.jpg"

                    if (isShare) {
                        // Save slice to cache directory for sharing
                        val cacheDir = File(context.cacheDir, "shared_exports").apply { mkdirs() }
                        val file = File(cacheDir, filename)
                        java.io.FileOutputStream(file).use { out ->
                            sliceBmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        // Inject GPS metadata
                        writeGpsToExif(file.absolutePath, currentPhoto.gpsLatitude, currentPhoto.gpsLongitude)
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                        savedUris.add(uri)
                    } else {
                        // Export locally using SAF or fallback to gallery
                        exportManager.exportPhoto(
                            bitmap = sliceBmp,
                            fileName = filename,
                            folderUriString = exportDir,
                            gpsLatitude = currentPhoto.gpsLatitude,
                            gpsLongitude = currentPhoto.gpsLongitude
                        )
                    }
                    sliceBmp.recycle()
                }

                compositeBmp.recycle()
                sourceBmp.recycle()

                withContext(Dispatchers.Main) {
                    _isPublishing.value = false
                    if (isShare) {
                        if (savedUris.isNotEmpty()) {
                            exportManager.sharePhotos(savedUris, "Share Exported Photo")
                            _publishStatus.value = "Ready to share!"
                        }
                    } else {
                        val msg = if (!exportDir.isNullOrEmpty()) {
                            if (splitCount > 1) {
                                "Successfully split & saved $splitCount panels via SAF!"
                            } else {
                                "Successfully exported photo via SAF!"
                            }
                        } else {
                            if (splitCount > 1) {
                                "Successfully split & saved $splitCount panels to Pictures/TetherStudio!"
                            } else {
                                "Successfully exported photo to Pictures/TetherStudio!"
                            }
                        }
                        _publishStatus.value = msg
                    }
                }

            } catch (e: Exception) {
                Log.e(tag, "Failed advanced photo export", e)
                withContext(Dispatchers.Main) {
                    _isPublishing.value = false
                    _publishStatus.value = "Export failed: ${e.localizedMessage}"
                }
            }
        }
    }

    fun exportMultiplePhotos(
        context: Context,
        photos: List<LocalPhoto>,
        aspectName: String,
        fitMode: String,
        borderStyle: String,
        customBorderColor: Int,
        borderThicknessPct: Float,
        splitCount: Int,
        isShare: Boolean = false
    ) {
        if (photos.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isPublishing.value = true
                    _publishStatus.value = "Preparing export for ${photos.size} photos..."
                }

                val savedUris = ArrayList<Uri>()
                val authority = "${context.packageName}.fileprovider"
                val exportDir = settings.value.exportDirectoryUri

                for ((idx, currentPhoto) in photos.withIndex()) {
                    withContext(Dispatchers.Main) {
                        _publishStatus.value = "Exporting photo ${idx + 1}/${photos.size}..."
                    }
                    val sourceBmp = renderEditedBitmap(currentPhoto) ?: continue

                    val sw = sourceBmp.width
                    val sh = sourceBmp.height

                    // Determine single panel aspect ratio
                    val panelRatio = when (aspectName) {
                        "1:1" -> 1.0f
                        "4:5" -> 0.8f
                        "9:16" -> 9f / 16f
                        "16:9" -> 16f / 9f
                        "1.91:1" -> 1.91f
                        "2:3" -> 2f / 3f
                        else -> sw.toFloat() / sh.toFloat() // "Original"
                    }

                    // Determine composite canvas aspect ratio
                    val compositeRatio = panelRatio * splitCount

                    // Determine composite canvas dimensions (CW, CH)
                    val CW: Int
                    val CH: Int
                    if (fitMode == "Crop to Fit") {
                        val srcRatio = sw.toFloat() / sh.toFloat()
                        if (srcRatio > compositeRatio) {
                            CH = sh
                            CW = (sh * compositeRatio).toInt()
                        } else {
                            CW = sw
                            CH = (sw / compositeRatio).toInt()
                        }
                    } else { // "Fit with Letterbox"
                        val srcRatio = sw.toFloat() / sh.toFloat()
                        if (srcRatio > compositeRatio) {
                            CW = sw
                            CH = (sw / compositeRatio).toInt()
                        } else {
                            CH = sh
                            CW = (sh * compositeRatio).toInt()
                        }
                    }

                    // Create composite bitmap
                    val compositeBmp = Bitmap.createBitmap(CW, CH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(compositeBmp)

                    // Fill background
                    val bgColor = when (borderStyle) {
                        "Classic White" -> android.graphics.Color.WHITE
                        "Gallery Black" -> android.graphics.Color.BLACK
                        "Custom Color" -> customBorderColor
                        else -> android.graphics.Color.BLACK // default letterbox color
                    }
                    canvas.drawColor(bgColor)

                    // Draw source image with chosen Fit Mode
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    if (fitMode == "Crop to Fit") {
                        val srcRatio = sw.toFloat() / sh.toFloat()
                        val srcRect: Rect
                        if (srcRatio > compositeRatio) {
                            val cropW = (sh * compositeRatio).toInt()
                            val startX = (sw - cropW) / 2
                            srcRect = Rect(startX, 0, startX + cropW, sh)
                        } else {
                            val cropH = (sw / compositeRatio).toInt()
                            val startY = (sh - cropH) / 2
                            srcRect = Rect(0, startY, sw, startY + cropH)
                        }
                        val dstRect = Rect(0, 0, CW, CH)
                        canvas.drawBitmap(sourceBmp, srcRect, dstRect, paint)
                    } else { // "Fit with Letterbox"
                        val srcRatio = sw.toFloat() / sh.toFloat()
                        val dstRect: Rect
                        if (srcRatio > compositeRatio) {
                            val drawH = (CW / srcRatio).toInt()
                            val startY = (CH - drawH) / 2
                            dstRect = Rect(0, startY, CW, startY + drawH)
                        } else {
                            val drawW = (CH * srcRatio).toInt()
                            val startX = (CW - drawW) / 2
                            dstRect = Rect(startX, 0, startX + drawW, CH)
                        }
                        canvas.drawBitmap(sourceBmp, null, dstRect, paint)
                    }

                    // Draw Borders around individual panels
                    val thicknessPx = (Math.min(CW, CH) / splitCount.toFloat() * borderThicknessPct).toInt()
                    if (borderStyle != "None" && thicknessPx > 0) {
                        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = thicknessPx.toFloat()
                        }

                        if (borderStyle == "Neon Glow") {
                            borderPaint.shader = LinearGradient(
                                0f, 0f, CW.toFloat(), CH.toFloat(),
                                intArrayOf(
                                    android.graphics.Color.parseColor("#FF007F"), // Neon Pink
                                    android.graphics.Color.parseColor("#00F6FF"), // Neon Cyan
                                    android.graphics.Color.parseColor("#9D4EDD")  // Purple Glow
                                ),
                                null, Shader.TileMode.CLAMP
                            )
                        } else {
                            borderPaint.color = when (borderStyle) {
                                "Classic White" -> android.graphics.Color.WHITE
                                "Gallery Black" -> android.graphics.Color.BLACK
                                "Custom Color" -> customBorderColor
                                else -> android.graphics.Color.TRANSPARENT
                            }
                        }

                        val panelWidth = CW / splitCount
                        for (i in 0 until splitCount) {
                            val left = i * panelWidth + thicknessPx / 2f
                            val top = thicknessPx / 2f
                            val right = (i + 1) * panelWidth - thicknessPx / 2f
                            val bottom = CH - thicknessPx / 2f
                            canvas.drawRect(left, top, right, bottom, borderPaint)
                        }
                    }

                    for (i in 0 until splitCount) {
                        val left = i * CW / splitCount
                        val right = (i + 1) * CW / splitCount
                        val sliceWidth = right - left
                        val sliceBmp = Bitmap.createBitmap(compositeBmp, left, 0, sliceWidth, CH)

                        val filename = "TetherStudio_${currentPhoto.fileName.substringBeforeLast(".")}_${aspectName.replace(":", "x")}_split_${i + 1}_${System.currentTimeMillis()}.jpg"

                        if (isShare) {
                            val cacheDir = File(context.cacheDir, "shared_exports").apply { mkdirs() }
                            val file = File(cacheDir, filename)
                            java.io.FileOutputStream(file).use { out ->
                                sliceBmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            writeGpsToExif(file.absolutePath, currentPhoto.gpsLatitude, currentPhoto.gpsLongitude)
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                            savedUris.add(uri)
                        } else {
                            exportManager.exportPhoto(
                                bitmap = sliceBmp,
                                fileName = filename,
                                folderUriString = exportDir,
                                gpsLatitude = currentPhoto.gpsLatitude,
                                gpsLongitude = currentPhoto.gpsLongitude
                            )
                        }
                        sliceBmp.recycle()
                    }

                    compositeBmp.recycle()
                    sourceBmp.recycle()
                }

                withContext(Dispatchers.Main) {
                    _isPublishing.value = false
                    if (isShare) {
                        if (savedUris.isNotEmpty()) {
                            exportManager.sharePhotos(savedUris, "Share Exported Photos")
                            _publishStatus.value = "Ready to share ${photos.size} photos!"
                        }
                    } else {
                        val suffix = if (!exportDir.isNullOrEmpty()) "via SAF!" else "to Pictures/TetherStudio!"
                        _publishStatus.value = "Successfully exported ${photos.size} photos $suffix"
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed advanced multiple photo export", e)
                withContext(Dispatchers.Main) {
                    _isPublishing.value = false
                    _publishStatus.value = "Export failed: ${e.localizedMessage}"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // ignore if not registered
        }
        mtpPollerJob?.cancel()
        repository.stopListeningToRegistrations()
        stopFolderMonitoring()
        activePtpConnector?.close()
        activePtpConnector = null
    }

    private fun sharpenBitmap(src: Bitmap, weight: Float): Bitmap {
        if (weight <= 0f) return src
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        val outPixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val edge = -weight
        val center = 1f + 4f * weight

        System.arraycopy(pixels, 0, outPixels, 0, width)
        System.arraycopy(pixels, (height - 1) * width, outPixels, (height - 1) * width, width)
        for (y in 1 until height - 1) {
            outPixels[y * width] = pixels[y * width]
            outPixels[y * width + width - 1] = pixels[y * width + width - 1]
        }

        for (y in 1 until height - 1) {
            val rowOffset = y * width
            val prevRowOffset = (y - 1) * width
            val nextRowOffset = (y + 1) * width
            for (x in 1 until width - 1) {
                val idx = rowOffset + x

                val c = pixels[idx]
                val t = pixels[prevRowOffset + x]
                val b = pixels[nextRowOffset + x]
                val l = pixels[idx - 1]
                val r = pixels[idx + 1]

                val rC = (c shr 16) and 0xFF
                val rT = (t shr 16) and 0xFF
                val rB = (b shr 16) and 0xFF
                val rL = (l shr 16) and 0xFF
                val rR = (r shr 16) and 0xFF
                val rNew = (rC * center + (rT + rB + rL + rR) * edge).toInt().coerceIn(0, 255)

                val gC = (c shr 8) and 0xFF
                val gT = (t shr 8) and 0xFF
                val gB = (b shr 8) and 0xFF
                val gL = (l shr 8) and 0xFF
                val gR = (r shr 8) and 0xFF
                val gNew = (gC * center + (gT + gB + gL + gR) * edge).toInt().coerceIn(0, 255)

                val bC = c and 0xFF
                val bT = t and 0xFF
                val bB = b and 0xFF
                val bL = l and 0xFF
                val bR = r and 0xFF
                val bNew = (bC * center + (bT + bB + bL + bR) * edge).toInt().coerceIn(0, 255)

                val alpha = c and -0x1000000

                outPixels[idx] = alpha or (rNew shl 16) or (gNew shl 8) or bNew
            }
        }

        val dest = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }
}

/**
 * Bitmap extension function utilizing PorterDuff.Mode to apply a graphical overlay mask to a Bitmap.
 */
fun Bitmap.applyOverlayMask(mask: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(this, 0f, 0f, paint)

    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    if (mask.width != this.width || mask.height != this.height) {
        val scaledMask = Bitmap.createScaledBitmap(mask, this.width, this.height, true)
        canvas.drawBitmap(scaledMask, 0f, 0f, maskPaint)
        scaledMask.recycle()
    } else {
        canvas.drawBitmap(mask, 0f, 0f, maskPaint)
    }
    return result
}

class PhotoshootViewModelFactory(
    private val context: Context,
    private val repository: PhotoshootRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PhotoshootViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PhotoshootViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
