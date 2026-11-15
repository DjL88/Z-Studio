package com.example.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.library.data.LocalConfig
import com.example.library.data.LocalPhoto
import com.example.library.data.LocalRegistration
import com.example.library.data.BrushPoint
import com.example.library.data.EditSnapshot
import com.example.library.ui.LibraryScreen
import com.example.viewmodel.PhotoshootViewModel
import com.example.viewmodel.CameraConnectionState
import com.example.editor.service.ImageFilterProcessor
import com.example.editor.ui.EditorSliders
import com.example.editor.ui.RotationDial
import com.example.editor.ui.UnifiedCropRotate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoshootApp(viewModel: PhotoshootViewModel) {
    val context = LocalContext.current
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val allPhotos by viewModel.allPhotos.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val filteredPhotos by viewModel.filteredPhotos.collectAsStateWithLifecycle()
    val allConfigs by viewModel.allConfigs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeConfigId by viewModel.activeConfigId.collectAsStateWithLifecycle()
    val activeConfig by viewModel.activeConfig.collectAsStateWithLifecycle()
    val registrations by viewModel.registrations.collectAsStateWithLifecycle()
    val allRegistrations by viewModel.allRegistrations.collectAsStateWithLifecycle()
    val onStageReg by viewModel.onStageRegistration.collectAsStateWithLifecycle()
    val selectedPhoto by viewModel.selectedPhoto.collectAsStateWithLifecycle()
    val denoisedPreviewUrl by viewModel.denoisedPreviewUrl.collectAsStateWithLifecycle()

    val isPublishing by viewModel.isPublishing.collectAsStateWithLifecycle()
    val publishOverwritePrompt by viewModel.publishOverwritePrompt.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingFirebase.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val isFolderPolling by viewModel.isFolderPolling.collectAsStateWithLifecycle()
    val cameraState by viewModel.cameraConnectionState.collectAsStateWithLifecycle()
    val simulatedConnection by viewModel.simulatedConnection.collectAsStateWithLifecycle()
    val publishStatus by viewModel.publishStatus.collectAsStateWithLifecycle()

    // Interactive Slider States
    val brightness by viewModel.editBrightness.collectAsStateWithLifecycle()
    val contrast by viewModel.editContrast.collectAsStateWithLifecycle()
    val saturation by viewModel.editSaturation.collectAsStateWithLifecycle()
    val shadows by viewModel.editShadows.collectAsStateWithLifecycle()
    val highlights by viewModel.editHighlights.collectAsStateWithLifecycle()
    val whites by viewModel.editWhites.collectAsStateWithLifecycle()
    val blacks by viewModel.editBlacks.collectAsStateWithLifecycle()
    val filterName by viewModel.editFilterName.collectAsStateWithLifecycle()
    val blurIntensity by viewModel.editBlurIntensity.collectAsStateWithLifecycle()
    val maskX by viewModel.editMaskCenterX.collectAsStateWithLifecycle()
    val maskY by viewModel.editMaskCenterY.collectAsStateWithLifecycle()
    val maskRadius by viewModel.editMaskRadius.collectAsStateWithLifecycle()
    val cropL by viewModel.cropLeft.collectAsStateWithLifecycle()
    val cropT by viewModel.cropTop.collectAsStateWithLifecycle()
    val cropR by viewModel.cropRight.collectAsStateWithLifecycle()
    val cropB by viewModel.cropBottom.collectAsStateWithLifecycle()
    val cropMode by viewModel.cropMode.collectAsStateWithLifecycle()

    val rating by viewModel.editRating.collectAsStateWithLifecycle()
    val isFlagged by viewModel.editIsFlagged.collectAsStateWithLifecycle()
    val sharpness by viewModel.editSharpness.collectAsStateWithLifecycle()
    val vignette by viewModel.editVignette.collectAsStateWithLifecycle()
    val temperature by viewModel.editTemperature.collectAsStateWithLifecycle()
    val tint by viewModel.editTint.collectAsStateWithLifecycle()
    val exposure by viewModel.editExposure.collectAsStateWithLifecycle()
    val straighten by viewModel.editStraighten.collectAsStateWithLifecycle()
    val borderType by viewModel.editBorderType.collectAsStateWithLifecycle()
    val hasCopiedEdits by viewModel.hasCopiedEdits.collectAsStateWithLifecycle()
    val gpsLatitude by viewModel.editGpsLatitude.collectAsStateWithLifecycle()
    val gpsLongitude by viewModel.editGpsLongitude.collectAsStateWithLifecycle()

    val tetherGpsEnabled by viewModel.tetherGpsEnabled.collectAsStateWithLifecycle()
    val tetherGpsLatitude by viewModel.tetherGpsLatitude.collectAsStateWithLifecycle()
    val tetherGpsLongitude by viewModel.tetherGpsLongitude.collectAsStateWithLifecycle()

    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val brushType by viewModel.brushType.collectAsStateWithLifecycle()
    val brushSize by viewModel.brushSize.collectAsStateWithLifecycle()
    val brushPoints by viewModel.brushPoints.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()

    // Interactive Crop Selector variables
    var activeCropHandle by remember { mutableStateOf<String?>(null) }

    // Dropdown States
    var showEventDropdown by remember { mutableStateOf(false) }
    var showClientDropdown by remember { mutableStateOf(false) }
    var showAddClientDialog by remember { mutableStateOf(false) }
    var showClientSelectionDialog by remember { mutableStateOf(false) }

    var showCreatePhotoshootDialog by remember { mutableStateOf(false) }
    var newPhotoshootName by remember { mutableStateOf("") }
    var newPhotoshootLocation by remember { mutableStateOf("") }
    var newPhotoshootDate by remember { mutableStateOf("") }

    // Adjustable panel and header visibility variables
    var showLeftPanel by remember { mutableStateOf(true) }
    var showRightPanel by remember { mutableStateOf(true) }
    var showTopHeader by remember { mutableStateOf(true) }
    var rightPanelTab by remember { mutableStateOf(0) }
    var isFullScreen by remember { mutableStateOf(false) }

    var currentPage by remember { mutableStateOf(0) }
    val selectedPhotosForExport = remember { androidx.compose.runtime.mutableStateMapOf<String, LocalPhoto>() }

    var showTetherPanel by remember { mutableStateOf(true) }
    var showSessionCaptures by remember { mutableStateOf(true) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var showExifDialog by remember { mutableStateOf(false) }

    // Advanced Export Dialog variables
    var showAdvancedExportDialog by remember { mutableStateOf(false) }
    var exportAspect by remember { mutableStateOf("Original") }
    var exportFitMode by remember { mutableStateOf("Crop to Fit") }
    var exportBorderStyle by remember { mutableStateOf("None") }
    var exportCustomBorderColor by remember { mutableStateOf(android.graphics.Color.WHITE) }
    var exportBorderThickness by remember { mutableStateOf(0.04f) }
    var exportSplitCount by remember { mutableStateOf(1) }

    val manualImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importManualImage(uri)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importUploadedPhoto(uri, "uploaded_${System.currentTimeMillis()}.jpg")
        }
    }

    LaunchedEffect(isFullScreen) {
        if (isFullScreen) {
            showLeftPanel = false
            showRightPanel = false
            showTetherPanel = false
            showSessionCaptures = false
            showTopHeader = false
        } else {
            showLeftPanel = true
            showRightPanel = true
            showTetherPanel = true
            showSessionCaptures = true
            showTopHeader = true
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage == 3) {
            showSessionCaptures = false
        } else if (!isFullScreen) {
            showSessionCaptures = true
        }
    }

    LaunchedEffect(syncError) {
        syncError?.let {
            Toast.makeText(context, "Sync Error: $it", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(publishStatus) {
        publishStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearPublishStatus()
        }
    }

    // SAF Directory picker launcher
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist access permission
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setTetherDirectory(uri.toString())
        }
    }

    // SAF Export Directory picker launcher
    val exportDirectoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist access permission
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setExportDirectory(uri.toString())
        }
    }

    // Sleek Interface Theme Palette
    val studioBackground = Color(0xFF1C1B1F)  // Sleek deep charcoal purple
    val panelBackground = Color(0xFF2B2930)   // Sleek surface / panel
    val accentWarm = Color(0xFFD0BCFF)        // Sleek lavender / accent
    val textPrimary = Color(0xFFE6E1E5)       // Sleek text primary
    val textSecondary = Color(0xFFCAC4D0)     // Sleek text secondary
    val accentDark = Color(0xFF381E72)        // Sleek deep violet (dark container)
    val borderColor = Color(0xFF49454F)       // Sleek border

    Scaffold(
        topBar = {
            if (showTopHeader) {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_app_icon),
                                        contentDescription = "Tether Studio",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                val modelNameText = when (val state = cameraState) {
                                    is CameraConnectionState.Connected -> state.model
                                    is CameraConnectionState.Error -> "Incompatible Device"
                                    else -> "Tether Studio"
                                }
                                val connectionStatusText = when (val state = cameraState) {
                                    is CameraConnectionState.Connected -> "CONNECTED • USB-C"
                                    is CameraConnectionState.Error -> state.message.uppercase(Locale.ROOT)
                                    else -> "AWAITING CAMERA"
                                }
                                val connectionColor = when (cameraState) {
                                    is CameraConnectionState.Connected -> accentWarm
                                    is CameraConnectionState.Error -> Color(0xFFE57373)
                                    else -> Color(0xFFEF5350)
                                }
                                Text(
                                    text = modelNameText,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    fontSize = 16.sp,
                                    color = textPrimary
                                )
                                Text(
                                    text = connectionStatusText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = connectionColor,
                                    letterSpacing = 1.2.sp
                                )
                            }
                            if (!viewModel.isFirebaseAvailable) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = accentDark,
                                        contentColor = accentWarm
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = "OFFLINE SANDBOX MODE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (!isPortrait) {
                            // Universal Active Client Dropdown
                            Box(modifier = Modifier.padding(end = 6.dp)) {
                                Button(
                                    onClick = { showClientDropdown = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (onStageReg != null) accentDark else borderColor,
                                        contentColor = if (onStageReg != null) accentWarm else textPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (onStageReg != null) accentWarm.copy(alpha = 0.8f) else borderColor),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("app_bar_client_dropdown_button")
                                ) {
                                    Icon(
                                        imageVector = if (onStageReg != null) Icons.Default.Group else Icons.Default.PersonOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (onStageReg != null) accentWarm else textPrimary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = onStageReg?.let { "${it.name}${if (it.pets.isNotEmpty()) " (${it.pets})" else ""}" } ?: "Assign Client (On-Stage)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = onStageReg?.let { "On-Stage • ${if (it.isBookingPaid) "Paid (£${it.paymentAmount})" else "Unpaid"}" } ?: "Select client for photos",
                                            fontSize = 10.sp,
                                            color = if (onStageReg != null) accentWarm.copy(alpha = 0.9f) else textSecondary,
                                            fontWeight = FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if (onStageReg != null) accentWarm else textPrimary)
                                }

                                DropdownMenu(
                                    expanded = showClientDropdown,
                                    onDismissRequest = { showClientDropdown = false },
                                    modifier = Modifier.background(panelBackground)
                                ) {
                                    ClientDropdownContent(
                                        allRegistrations = allRegistrations,
                                        activeConfig = activeConfig,
                                        onStageReg = onStageReg,
                                        allPhotos = allPhotos,
                                        accentWarm = accentWarm,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        borderColor = borderColor,
                                        onSelectClient = { client -> viewModel.selectOnStageClient(client) },
                                        onDismiss = { showClientDropdown = false }
                                    )
                                }
                            }

                            // Quick Event Selector
                            Box(modifier = Modifier.padding(end = 8.dp)) {
                                Button(
                                    onClick = { showEventDropdown = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = borderColor,
                                        contentColor = textPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, borderColor)
                                ) {
                                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = activeConfig?.let { 
                                                if (it.eventLocation.isNotEmpty()) "${it.eventName} (${it.eventLocation})" else it.eventName
                                            } ?: "Select Shoot Event",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        activeConfig?.let { config ->
                                            if (config.eventDate.isNotEmpty()) {
                                                Text(
                                                    text = config.eventDate,
                                                    fontSize = 10.sp,
                                                    color = textSecondary,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }

                                DropdownMenu(
                                    expanded = showEventDropdown,
                                    onDismissRequest = { showEventDropdown = false },
                                    modifier = Modifier.background(panelBackground)
                                ) {
                                    EventDropdownContent(
                                        allConfigs = allConfigs,
                                        activeConfig = activeConfig,
                                        onStageReg = onStageReg,
                                        syncError = syncError,
                                        accentWarm = accentWarm,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        borderColor = borderColor,
                                        onSelectEvent = { config -> viewModel.selectEvent(config) },
                                        onCreateLocalPhotoshoot = { showCreatePhotoshootDialog = true },
                                        onBypassStandalone = { viewModel.enableStandaloneMode() },
                                        onDismiss = { showEventDropdown = false }
                                    )
                                }
                            }
                        }

                        // Sync Events and Fullscreen Header toggles
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            // Manual Image Import Button
                            IconButton(
                                onClick = {
                                    manualImagePickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("app_bar_import_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Import Photo from Device",
                                    tint = textPrimary
                                )
                            }

                            // Sync Firebase configs
                            IconButton(
                                onClick = { viewModel.syncConfigs() },
                                modifier = Modifier.testTag("sync_firebase_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accentWarm, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Sync Events", tint = textPrimary)
                                }
                            }

                            // Fullscreen toggle
                            IconButton(
                                onClick = { showTopHeader = false }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Hide Header",
                                    tint = textPrimary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = panelBackground,
                        titleContentColor = textPrimary
                    )
                )

                if (isPortrait) {
                    Surface(
                        color = panelBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Event Dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { showEventDropdown = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = borderColor,
                                        contentColor = textPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, borderColor),
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column(
                                        horizontalAlignment = Alignment.Start,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = activeConfig?.let { 
                                                if (it.eventLocation.isNotEmpty()) "${it.eventName} (${it.eventLocation})" else it.eventName
                                            } ?: "Select Shoot Event",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        activeConfig?.let { config ->
                                            if (config.eventDate.isNotEmpty()) {
                                                Text(
                                                    text = config.eventDate,
                                                    fontSize = 9.sp,
                                                    color = textSecondary,
                                                    fontWeight = FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }

                                DropdownMenu(
                                    expanded = showEventDropdown,
                                    onDismissRequest = { showEventDropdown = false },
                                    modifier = Modifier.background(panelBackground)
                                ) {
                                    EventDropdownContent(
                                        allConfigs = allConfigs,
                                        activeConfig = activeConfig,
                                        onStageReg = onStageReg,
                                        syncError = syncError,
                                        accentWarm = accentWarm,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        borderColor = borderColor,
                                        onSelectEvent = { config -> viewModel.selectEvent(config) },
                                        onCreateLocalPhotoshoot = { showCreatePhotoshootDialog = true },
                                        onBypassStandalone = { viewModel.enableStandaloneMode() },
                                        onDismiss = { showEventDropdown = false }
                                    )
                                }
                            }

                            // Client Dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { showClientDropdown = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (onStageReg != null) accentDark else borderColor,
                                        contentColor = if (onStageReg != null) accentWarm else textPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (onStageReg != null) accentWarm.copy(alpha = 0.8f) else borderColor),
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (onStageReg != null) Icons.Default.Group else Icons.Default.PersonOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (onStageReg != null) accentWarm else textPrimary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column(
                                        horizontalAlignment = Alignment.Start,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = onStageReg?.let { "${it.name}${if (it.pets.isNotEmpty()) " (${it.pets})" else ""}" } ?: "Assign Client",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = onStageReg?.let { "On-Stage • ${if (it.isBookingPaid) "Paid" else "Unpaid"}" } ?: "Select Client",
                                            fontSize = 9.sp,
                                            color = if (onStageReg != null) accentWarm.copy(alpha = 0.9f) else textSecondary,
                                            fontWeight = FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (onStageReg != null) accentWarm else textPrimary)
                                }

                                DropdownMenu(
                                    expanded = showClientDropdown,
                                    onDismissRequest = { showClientDropdown = false },
                                    modifier = Modifier.background(panelBackground)
                                ) {
                                    ClientDropdownContent(
                                        allRegistrations = allRegistrations,
                                        activeConfig = activeConfig,
                                        onStageReg = onStageReg,
                                        allPhotos = allPhotos,
                                        accentWarm = accentWarm,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        borderColor = borderColor,
                                        onSelectClient = { client -> viewModel.selectOnStageClient(client) },
                                        onDismiss = { showClientDropdown = false }
                                    )
                                }
                            }
                        }
                    }
                }

                // Premium Material 3 Carousel TabRow (Narrow side-by-side icons & text)
                if (!isPortrait) {
                    val isTethered = cameraState is CameraConnectionState.Connected
                    TabRow(
                        selectedTabIndex = currentPage,
                        containerColor = panelBackground,
                        contentColor = accentWarm
                    ) {
                        Tab(
                            selected = currentPage == 0,
                            onClick = { currentPage = 0 }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Group, contentDescription = "Queue Tab", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Queue", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Tab(
                            selected = currentPage == 1,
                            onClick = { currentPage = 1 }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Cable, contentDescription = "Tether Tab", modifier = Modifier.size(16.dp))
                                    if (isTethered) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 5.dp, y = (-4).dp)
                                                .size(6.dp)
                                                .background(Color.Red, CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tether & Live View", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Tab(
                            selected = currentPage == 2,
                            onClick = { currentPage = 2 }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Collections, contentDescription = "Library Tab", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Library", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Tab(
                            selected = currentPage == 3,
                            onClick = { currentPage = 3 }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Edit Tab", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Tab(
                            selected = currentPage == 4,
                            onClick = { currentPage = 4 }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = "Publish Tab", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Publish", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = borderColor, thickness = 1.dp)
                }
            }
        }
    },
    bottomBar = {
        if (isPortrait && showTopHeader) {
            val isTethered = cameraState is CameraConnectionState.Connected
            NavigationBar(
                containerColor = panelBackground,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentPage == 0,
                    onClick = { currentPage = 0 },
                    icon = { Icon(Icons.Default.Group, contentDescription = "Queue") },
                    label = { Text("Queue", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentDark,
                        selectedTextColor = accentWarm,
                        indicatorColor = accentWarm,
                        unselectedIconColor = textSecondary,
                        unselectedTextColor = textSecondary
                    )
                )
                NavigationBarItem(
                    selected = currentPage == 1,
                    onClick = { currentPage = 1 },
                    icon = {
                        Box {
                            Icon(Icons.Default.Cable, contentDescription = "Tether")
                            if (isTethered) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(6.dp)
                                        .background(Color.Red, CircleShape)
                                )
                            }
                        }
                    },
                    label = { Text("Tether", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentDark,
                        selectedTextColor = accentWarm,
                        indicatorColor = accentWarm,
                        unselectedIconColor = textSecondary,
                        unselectedTextColor = textSecondary
                    )
                )
                NavigationBarItem(
                    selected = currentPage == 2,
                    onClick = { currentPage = 2 },
                    icon = { Icon(Icons.Default.Collections, contentDescription = "Library") },
                    label = { Text("Library", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentDark,
                        selectedTextColor = accentWarm,
                        indicatorColor = accentWarm,
                        unselectedIconColor = textSecondary,
                        unselectedTextColor = textSecondary
                    )
                )
                NavigationBarItem(
                    selected = currentPage == 3,
                    onClick = { currentPage = 3 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Edit") },
                    label = { Text("Edit", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentDark,
                        selectedTextColor = accentWarm,
                        indicatorColor = accentWarm,
                        unselectedIconColor = textSecondary,
                        unselectedTextColor = textSecondary
                    )
                )
                NavigationBarItem(
                    selected = currentPage == 4,
                    onClick = { currentPage = 4 },
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = "Publish") },
                    label = { Text("Publish", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accentDark,
                        selectedTextColor = accentWarm,
                        indicatorColor = accentWarm,
                        unselectedIconColor = textSecondary,
                        unselectedTextColor = textSecondary
                    )
                )
            }
        }
    },
    containerColor = studioBackground
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val actualShowLeftPanel = false
            val actualShowTetherLeftPanel = false
            val actualShowRightPanel = (currentPage == 3 && showRightPanel)

            Layout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(studioBackground),
                content = {
                    if (actualShowLeftPanel) {
                        // --- LEFT PANEL: Client Queue (Width: 33%) ---
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            color = panelBackground,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CLIENT QUEUE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentWarm,
                            letterSpacing = 1.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { showClientSelectionDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search & Select Client",
                                    tint = accentWarm,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { showAddClientDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = "Add Client Manually",
                                    tint = accentWarm,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // On Stage (Active Client) section
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (onStageReg != null) Color(0xFF24222A) else Color(0xFF1E1D22)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (onStageReg != null) accentWarm else borderColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("on_stage_card"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ON STAGE NOW",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textSecondary,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = onStageReg?.name ?: "No client selected",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (onStageReg != null) accentDark else borderColor)
                                        .clickable(enabled = onStageReg != null) { viewModel.selectOnStageClient(null) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (onStageReg != null) Icons.Default.Group else Icons.Default.PersonOutline,
                                        contentDescription = "Vacate Stage",
                                        tint = if (onStageReg != null) accentWarm else textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (onStageReg != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // ID Pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(accentDark)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "ID: ${onStageReg!!.id.take(8)}",
                                            color = accentWarm,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Event / Pets Pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(borderColor)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (onStageReg!!.pets.isNotEmpty()) onStageReg!!.pets else "Paws in the Park",
                                            color = textPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (onStageReg!!.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Note: ${onStageReg!!.notes}",
                                        fontSize = 11.sp,
                                        color = textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Portraits: ${onStageReg!!.photosUploaded}",
                                        fontSize = 12.sp,
                                        color = textSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Vacate Stage",
                                        color = Color(0xFFEF5350),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { viewModel.selectOnStageClient(null) }
                                    )
                                }
                            } else {
                                Text(
                                    text = "Tap a customer in the list below to place them on stage before shooting.",
                                    fontSize = 11.sp,
                                    color = textSecondary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = borderColor, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Queue List
                    if (registrations.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PersonOutline, contentDescription = null, tint = textSecondary, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Queue Empty",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Clients check in via iPad\nKiosk to appear here.",
                                    color = textSecondary.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(registrations) { reg ->
                                val isSelected = onStageReg?.id == reg.id
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) accentDark else Color(0xFF24222A)
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) accentWarm else borderColor
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectOnStageClient(reg) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = reg.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (reg.photosUploaded > 0) Color(0xFF2E7D32) else borderColor)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${reg.photosUploaded} saved",
                                                    color = textPrimary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        if (reg.pets.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "🐾 ${reg.pets}",
                                                fontSize = 12.sp,
                                                color = textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        if (reg.notes.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = reg.notes,
                                                fontSize = 11.sp,
                                                color = textSecondary.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Put on stage or Show active
                                            Text(
                                                text = if (isSelected) "★ ON STAGE" else "☆ PUT ON STAGE",
                                                color = if (isSelected) accentWarm else textSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { viewModel.selectOnStageClient(if (isSelected) null else reg) }
                                                    .padding(vertical = 4.dp)
                                            )

                                            val activePhoto = selectedPhoto
                                            if (activePhoto != null && activePhoto.assignedUserId != reg.id) {
                                                Text(
                                                    text = "ASSIGN PHOTO ➜",
                                                    color = accentWarm,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clickable { viewModel.assignPhotoToClient(activePhoto, reg) }
                                                        .padding(vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
            }

                    // Compact Client Switcher Squeezed below the Queue
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1D22)),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("on_stage_switcher"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val onStage = onStageReg
                            val currentIndex = registrations.indexOfFirst { it.id == onStage?.id }
                            val hasPrev = registrations.isNotEmpty() && currentIndex > 0
                            val hasNext = registrations.isNotEmpty() && (currentIndex < registrations.size - 1)

                            IconButton(
                                onClick = {
                                    if (registrations.isNotEmpty()) {
                                        val newIndex = if (currentIndex == -1) 0 else (currentIndex - 1).coerceAtLeast(0)
                                        viewModel.selectOnStageClient(registrations[newIndex])
                                    }
                                },
                                enabled = hasPrev,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (hasPrev) accentDark else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Client",
                                    tint = if (hasPrev) accentWarm else textSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "CYCLE ON STAGE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 1.sp
                                )
                                if (onStage != null) {
                                    Text(
                                        text = onStage.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentWarm,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = "None Selected",
                                        fontSize = 11.sp,
                                        color = textSecondary.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (registrations.isNotEmpty()) {
                                        val newIndex = if (currentIndex == -1) 0 else (currentIndex + 1).coerceAtMost(registrations.size - 1)
                                        viewModel.selectOnStageClient(registrations[newIndex])
                                    }
                                },
                                enabled = hasNext,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (hasNext) accentDark else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Client",
                                    tint = if (hasNext) accentWarm else textSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    }
                }
            }


                if (actualShowTetherLeftPanel) {
                    // --- TETHER PANEL (Page 3) (Width: 33%) ---
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        color = panelBackground,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                                .imePadding()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Cable, contentDescription = null, tint = accentWarm, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TETHER INTERFACE",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentWarm,
                                        letterSpacing = 1.sp
                                    )
                                }
                                IconButton(
                                    onClick = { showTetherPanel = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Panel", tint = textSecondary, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Tether Status Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isFolderPolling) Icons.Default.CloudSync else Icons.Default.LinkOff,
                                            contentDescription = null,
                                            tint = if (isFolderPolling) accentWarm else Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isFolderPolling) "NIKON SYNC ACTIVE" else "TETHER STANDBY",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFolderPolling) accentWarm else Color.Red
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Monitor Nikon Camera sync directory (USB/WiFi snap folder).",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { directoryPickerLauncher.launch(null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = borderColor),
                                        modifier = Modifier.fillMaxWidth().testTag("bind_camera_folder_button"),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bind Camera Sync Folder", fontSize = 11.sp)
                                    }
                                    if (!settings.tetherDirectoryUri.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Active: ${Uri.decode(settings.tetherDirectoryUri!!).split("/").lastOrNull() ?: "Bound"}",
                                            fontSize = 10.sp,
                                            color = accentWarm,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Backup Upload Button in Tether panel
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null,
                                            tint = accentWarm,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "MANUAL PORTRAIT IMPORT",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Directly upload local portrait shots to edit and test high-precision bokeh depth focus.",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { imagePickerLauncher.launch("image/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentWarm),
                                        modifier = Modifier.fillMaxWidth().testTag("tether_upload_button"),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Upload Portrait Image", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Local Image Export SAF Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (!settings.exportDirectoryUri.isNullOrEmpty()) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (!settings.exportDirectoryUri.isNullOrEmpty()) accentWarm else textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "LOCAL EXPORT DESTINATION",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!settings.exportDirectoryUri.isNullOrEmpty()) accentWarm else textSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Choose the directory where finished, edited images will be exported locally via SAF.",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { exportDirectoryPickerLauncher.launch(null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = borderColor),
                                        modifier = Modifier.fillMaxWidth().testTag("bind_export_folder_button"),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.DriveFolderUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bind Local Export Folder", fontSize = 11.sp)
                                    }
                                    if (!settings.exportDirectoryUri.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Active: ${Uri.decode(settings.exportDirectoryUri!!).split("/").lastOrNull() ?: "Bound"}",
                                            fontSize = 10.sp,
                                            color = accentWarm,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Status: Falling back to external Pictures gallery",
                                            fontSize = 10.sp,
                                            color = textSecondary,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Simulated Nikon Shutter Release Box / USB-C Tether Console
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "USB-C TETHER CONSOLE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Nikon PTP hardware stream",
                                        fontSize = 9.sp,
                                        color = textSecondary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    when (val state = cameraState) {
                                        is CameraConnectionState.Connected -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(text = "CAMERA", fontSize = 8.sp, color = textSecondary)
                                                    Text(text = state.model, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentWarm)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(text = "BATTERY", fontSize = 8.sp, color = textSecondary)
                                                    Text(text = state.battery, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("APERTURE", fontSize = 8.sp, color = textSecondary)
                                                    Text(state.aperture, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                }
                                                Column {
                                                    Text("SHUTTER", fontSize = 8.sp, color = textSecondary)
                                                    Text(state.shutter, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                }
                                                Column {
                                                    Text("ISO", fontSize = 8.sp, color = textSecondary)
                                                    Text(state.iso, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("FOCUS", fontSize = 8.sp, color = textSecondary)
                                                    Text(state.focusMode, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Surface(
                                                color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF4CAF50))
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "AUTO-READ ACTIVE (Camera Photos Syncing)",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF81C784)
                                                    )
                                                }
                                            }
                                        }
                                        else -> {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                    .padding(12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.LinkOff, contentDescription = null, tint = textSecondary, modifier = Modifier.size(24.dp))
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = "No hardware PTP camera detected.\nConnect camera via USB-C to start tethering.",
                                                        fontSize = 10.sp,
                                                        color = textSecondary,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Tether GPS Exif Metadata Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = accentWarm,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Tether GPS EXIF Metadata",
                                                color = textPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Switch(
                                            checked = tetherGpsEnabled,
                                            onCheckedChange = { viewModel.updateTetherGps(it, tetherGpsLatitude, tetherGpsLongitude) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = accentWarm,
                                                checkedTrackColor = accentWarm.copy(alpha = 0.5f),
                                                uncheckedThumbColor = textSecondary,
                                                uncheckedTrackColor = borderColor
                                            ),
                                            modifier = Modifier.scale(0.75f).testTag("tether_gps_switch")
                                        )
                                    }

                                    if (tetherGpsEnabled) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("LATITUDE", fontSize = 8.sp, color = textSecondary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                BasicTextField(
                                                    value = if (tetherGpsLatitude == 0.0) "" else tetherGpsLatitude.toString(),
                                                    onValueChange = { val value = it.toDoubleOrNull() ?: 0.0; viewModel.updateTetherGps(true, value, tetherGpsLongitude) },
                                                    textStyle = androidx.compose.ui.text.TextStyle(color = textPrimary, fontSize = 11.sp),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Next
                                                    ),
                                                    keyboardActions = KeyboardActions(
                                                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                        .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                        .testTag("tether_lat_input")
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("LONGITUDE", fontSize = 8.sp, color = textSecondary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                BasicTextField(
                                                    value = if (tetherGpsLongitude == 0.0) "" else tetherGpsLongitude.toString(),
                                                    onValueChange = { val value = it.toDoubleOrNull() ?: 0.0; viewModel.updateTetherGps(true, tetherGpsLatitude, value) },
                                                    textStyle = androidx.compose.ui.text.TextStyle(color = textPrimary, fontSize = 11.sp),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Done
                                                    ),
                                                    keyboardActions = KeyboardActions(
                                                        onDone = {
                                                            keyboardController?.hide()
                                                            focusManager.clearFocus()
                                                        }
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                        .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                        .testTag("tether_lon_input")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("QUICK STUDIO PRESETS", fontSize = 8.sp, color = textSecondary)
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val presets = listOf(
                                                Triple("Paris", 48.8566, 2.3522),
                                                Triple("Tokyo", 35.6894, 139.6917),
                                                Triple("NYC", 40.7128, -74.0060),
                                                Triple("London", 51.5074, -0.1278)
                                            )
                                            presets.forEach { (name, lat, lon) ->
                                                val isSelected = (tetherGpsLatitude == lat && tetherGpsLongitude == lon)
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSelected) accentWarm else borderColor.copy(alpha = 0.5f))
                                                        .clickable {
                                                            viewModel.updateTetherGps(true, lat, lon)
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        }
                                                        .padding(vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = name,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) accentDark else textPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Preset Preferences Engine Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Auto-Apply Edits to Next Shot", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Switch(
                                            checked = settings.autoApplyPresets,
                                            onCheckedChange = { viewModel.toggleAutoApplyPresets(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = accentWarm,
                                                checkedTrackColor = accentWarm.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                    if (selectedPhoto != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.copyEdits() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = borderColor,
                                                    contentColor = textPrimary
                                                ),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Copy Edits", fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = { viewModel.pasteEdits() },
                                                enabled = hasCopiedEdits,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = accentWarm,
                                                    contentColor = Color.Black
                                                ),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Paste Edits", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = { viewModel.saveEditingAsPreset() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = borderColor,
                                                contentColor = textPrimary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Lock Current Edits as Preset", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- CENTER PANEL: Real-time Photo Canvas & Editor (Fills 2/3rds space, leaving 1/3rd for side panels) ---
                val isAnySidePanelVisible = actualShowLeftPanel || actualShowTetherLeftPanel || actualShowRightPanel
                val centerPanelWeight = if (isAnySidePanelVisible) 0.67f else 1.0f
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    color = panelBackground,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Scrollable content area or Box
                        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                            if (currentPage == 0) {
                                ClientQueueScreen(
                                    registrations = registrations,
                                    onStageReg = onStageReg,
                                    allPhotos = allPhotos,
                                    viewModel = viewModel,
                                    panelBackground = panelBackground,
                                    borderColor = borderColor,
                                    accentWarm = accentWarm,
                                    accentDark = accentDark,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    onSearchClick = { showClientSelectionDialog = true },
                                    onAddClick = { showAddClientDialog = true },
                                    selectedPhoto = selectedPhoto,
                                    isPortrait = isPortrait
                                )
                            } else if (currentPage == 1) {
                                TetherScreen(
                                    cameraState = cameraState,
                                    isFolderPolling = isFolderPolling,
                                    tetherGpsEnabled = tetherGpsEnabled,
                                    tetherGpsLatitude = tetherGpsLatitude,
                                    tetherGpsLongitude = tetherGpsLongitude,
                                    settings = settings,
                                    viewModel = viewModel,
                                    directoryPickerLauncher = directoryPickerLauncher,
                                    exportDirectoryPickerLauncher = exportDirectoryPickerLauncher,
                                    imagePickerLauncher = imagePickerLauncher,
                                    manualImagePickerLauncher = manualImagePickerLauncher,
                                    panelBackground = panelBackground,
                                    borderColor = borderColor,
                                    accentWarm = accentWarm,
                                    accentDark = accentDark,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    isPortrait = isPortrait
                                )
                            } else if (currentPage == 2) {
                                LibraryScreen(
                                    viewModel = viewModel,
                                    allPhotos = allPhotos,
                                    allProjects = allProjects,
                                    allRegistrations = allRegistrations,
                                    activeConfig = activeConfig,
                                    onStageRegistration = onStageReg,
                                    selectedProjectId = selectedProjectId,
                                    studioBackground = studioBackground,
                                    panelBackground = panelBackground,
                                    accentWarm = accentWarm,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    accentDark = accentDark,
                                    borderColor = borderColor,
                                    selectedPhotosForExport = selectedPhotosForExport,
                                    onToggleSelectForExport = { photo ->
                                        if (selectedPhotosForExport.containsKey(photo.id)) {
                                            selectedPhotosForExport.remove(photo.id)
                                        } else {
                                            selectedPhotosForExport[photo.id] = photo
                                        }
                                    },
                                    onNavigateToExport = { currentPage = 4 },
                                    onNavigateToEdit = { currentPage = 3 }
                                )
                            } else if (currentPage == 4) {
                                ExportScreen(
                                    viewModel = viewModel,
                                    selectedPhotosForExport = selectedPhotosForExport,
                                    onRemoveFromExport = { photo -> selectedPhotosForExport.remove(photo.id) },
                                    onClearExportList = { selectedPhotosForExport.clear() },
                                    panelBackground = panelBackground,
                                    borderColor = borderColor,
                                    accentWarm = accentWarm,
                                    accentDark = accentDark,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    isPortrait = isPortrait
                                )
                            } else if (selectedPhoto == null) {
                    // Empty studio editor canvas state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF332D2D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = accentWarm,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = if (cameraState is CameraConnectionState.Connected) "CAMERA ACTIVE" else "NO CAMERA CONNECTED",
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 16.sp,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (cameraState is CameraConnectionState.Connected) {
                                    "No photo selected. Trigger your Nikon camera shutter or select a photo from the queue/carousel."
                                } else {
                                    "Connect your Nikon camera via tethering or import folder in the Tether Console tab to start capturing."
                                },
                                color = textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 320.dp)
                            )
                            Spacer(Modifier.height(20.dp))
                            
                            // Interactive Upload Card
                            Card(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C24)),
                                border = BorderStroke(2.dp, accentWarm),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .width(320.dp)
                                    .testTag("upload_portrait_photo_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = accentWarm,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "UPLOAD PORTRAIT PHOTO",
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap to choose a local portrait file to edit & apply custom focal blurs",
                                        fontSize = 11.sp,
                                        color = textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    manualImagePickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = borderColor),
                                modifier = Modifier.testTag("empty_state_import_button")
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = textPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Import Photo from Device", color = textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Active photo editor
                    val photo = selectedPhoto!!
                    var canvasSize by remember { mutableStateOf(Size.Zero) }
                    var scale by remember { mutableStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    var activeCropRatioName by remember { mutableStateOf("Original") }
                    var isCropLandscape by remember { mutableStateOf(true) }

                    LaunchedEffect(photo.id) {
                        scale = 1f
                        offset = Offset.Zero
                    }

                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = photo.fileName,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (photo.assignedUserId != null) "Assigned to: ${photo.assignedUserName}" else "Unassigned photo",
                                    fontSize = 11.sp,
                                    color = if (photo.assignedUserId != null) accentWarm else textSecondary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic panel toggle in Portrait mode (to hide/show the bottom controls easily)
                                if (isPortrait && currentPage != 3) {
                                    val isPanelVisible = when (currentPage) {
                                        0 -> showLeftPanel
                                        1 -> showRightPanel
                                        2 -> showTetherPanel
                                        else -> false
                                    }
                                    val toggleIcon = when (currentPage) {
                                        0 -> Icons.Default.Group
                                        1 -> Icons.Default.Tune
                                        else -> Icons.Default.Cable
                                    }
                                    val panelDesc = when (currentPage) {
                                        0 -> "Queue Panel"
                                        1 -> "Adjustments Panel"
                                        else -> "Tether Panel"
                                    }
                                    IconButton(
                                        onClick = {
                                            when (currentPage) {
                                                0 -> showLeftPanel = !showLeftPanel
                                                1 -> showRightPanel = !showRightPanel
                                                2 -> showTetherPanel = !showTetherPanel
                                            }
                                        },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (isPanelVisible) accentWarm else borderColor
                                        ),
                                        modifier = Modifier.testTag("portrait_toggle_panel_button")
                                    ) {
                                        Icon(
                                            imageVector = toggleIcon,
                                            contentDescription = "Toggle $panelDesc",
                                            tint = if (isPanelVisible) accentDark else textPrimary
                                        )
                                    }
                                }

                                if (isPortrait) {
                                    IconButton(
                                        onClick = { viewModel.setCropMode(!cropMode) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (cropMode) accentWarm else borderColor,
                                            contentColor = if (cropMode) accentDark else textPrimary
                                        ),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Crop, contentDescription = "Crop Tool", modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.setCropMode(!cropMode) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (cropMode) accentWarm else borderColor,
                                            contentColor = if (cropMode) accentDark else textPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (cropMode) "Crop Done" else "Crop", fontSize = 12.sp)
                                    }
                                }
                                IconButton(
                                    onClick = { isFullScreen = !isFullScreen },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = if (isFullScreen) accentWarm else borderColor)
                                ) {
                                    Icon(
                                        imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Toggle Fullscreen",
                                        tint = if (isFullScreen) accentDark else textPrimary
                                    )
                                }
                                IconButton(
                                    onClick = { showSessionCaptures = !showSessionCaptures },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = if (showSessionCaptures) accentWarm else borderColor)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Collections,
                                        contentDescription = "Toggle Session Captures Carousel",
                                        tint = if (showSessionCaptures) accentDark else textPrimary
                                    )
                                }
                                IconButton(
                                    onClick = { showExifDialog = true },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = borderColor),
                                    modifier = Modifier.testTag("photo_info_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Exif Info",
                                        tint = textPrimary
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deletePhoto(photo) },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = borderColor)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Photo", tint = Color(0xFFEF5350))
                                }
                            }
                        }

                        if (cropMode) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(borderColor.copy(alpha = 0.4f))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ratio:", fontSize = 11.sp, color = textSecondary, fontWeight = FontWeight.Bold)
                                val ratios = listOf("Original", "1:1", "2:3", "3:4", "16:9")
                                ratios.forEach { ratioName ->
                                    val isSel = activeCropRatioName == ratioName
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) accentWarm else Color.Transparent)
                                            .clickable {
                                                activeCropRatioName = ratioName
                                                when (ratioName) {
                                                    "Original" -> viewModel.updateSliders(cropL = 0f, cropT = 0f, cropR = 1f, cropB = 1f)
                                                    "1:1" -> {
                                                        viewModel.updateSliders(cropL = 0.125f, cropT = 0f, cropR = 0.875f, cropB = 1f)
                                                    }
                                                    "2:3" -> {
                                                        if (isCropLandscape) {
                                                            viewModel.updateSliders(cropL = 0f, cropT = 0.15f, cropR = 1f, cropB = 0.85f)
                                                        } else {
                                                            viewModel.updateSliders(cropL = 0.15f, cropT = 0f, cropR = 0.85f, cropB = 1f)
                                                        }
                                                    }
                                                    "3:4" -> {
                                                        if (isCropLandscape) {
                                                            viewModel.updateSliders(cropL = 0f, cropT = 0.125f, cropR = 1f, cropB = 0.875f)
                                                        } else {
                                                            viewModel.updateSliders(cropL = 0.125f, cropT = 0f, cropR = 0.875f, cropB = 1f)
                                                        }
                                                    }
                                                    "16:9" -> {
                                                        if (isCropLandscape) {
                                                            viewModel.updateSliders(cropL = 0f, cropT = 0.22f, cropR = 1f, cropB = 0.78f)
                                                        } else {
                                                            viewModel.updateSliders(cropL = 0.22f, cropT = 0f, cropR = 0.78f, cropB = 1f)
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(ratioName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) accentDark else textPrimary)
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Orientation
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(borderColor)
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CropLandscape,
                                        contentDescription = "Landscape",
                                        tint = if (isCropLandscape) accentWarm else textSecondary,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { 
                                                isCropLandscape = true 
                                                val r = activeCropRatioName
                                                if (r != "Original") {
                                                    activeCropRatioName = ""
                                                    activeCropRatioName = r
                                                    when (r) {
                                                        "1:1" -> viewModel.updateSliders(cropL = 0.125f, cropT = 0f, cropR = 0.875f, cropB = 1f)
                                                        "2:3" -> viewModel.updateSliders(cropL = 0f, cropT = 0.15f, cropR = 1f, cropB = 0.85f)
                                                        "3:4" -> viewModel.updateSliders(cropL = 0f, cropT = 0.125f, cropR = 1f, cropB = 0.875f)
                                                        "16:9" -> viewModel.updateSliders(cropL = 0f, cropT = 0.22f, cropR = 1f, cropB = 0.78f)
                                                    }
                                                }
                                            }
                                            .padding(4.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CropPortrait,
                                        contentDescription = "Portrait",
                                        tint = if (!isCropLandscape) accentWarm else textSecondary,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { 
                                                isCropLandscape = false 
                                                val r = activeCropRatioName
                                                if (r != "Original") {
                                                    activeCropRatioName = ""
                                                    activeCropRatioName = r
                                                    when (r) {
                                                        "1:1" -> viewModel.updateSliders(cropL = 0.125f, cropT = 0f, cropR = 0.875f, cropB = 1f)
                                                        "2:3" -> viewModel.updateSliders(cropL = 0.15f, cropT = 0f, cropR = 0.85f, cropB = 1f)
                                                        "3:4" -> viewModel.updateSliders(cropL = 0.125f, cropT = 0f, cropR = 0.875f, cropB = 1f)
                                                        "16:9" -> viewModel.updateSliders(cropL = 0.22f, cropT = 0f, cropR = 0.78f, cropB = 1f)
                                                    }
                                                }
                                            }
                                            .padding(4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            // Straighten Rotation Dial integrated directly into Cropping!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(borderColor.copy(alpha = 0.4f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Straighten:", fontSize = 11.sp, color = textSecondary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                var lastStraighten by remember { mutableStateOf(straighten) }
                                RotationDial(
                                    angle = straighten,
                                    onAngleChange = { newVal ->
                                        if ((lastStraighten < 0f && newVal >= 0f) || (lastStraighten > 0f && newVal <= 0f)) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        lastStraighten = newVal
                                        viewModel.updateSliders(straighten = newVal)
                                    },
                                    onValueChangeFinished = {
                                        viewModel.saveToHistory()
                                    },
                                    range = -45f..45f,
                                    accentColor = accentWarm,
                                    borderColor = borderColor,
                                    textColorPrimary = textPrimary,
                                    textColorSecondary = textSecondary,
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%+.1f°", straighten),
                                    fontSize = 11.sp,
                                    color = accentWarm,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(48.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live Canvas Display Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                .onGloballyPositioned { coordinates ->
                                    canvasSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val aspectTargetRatio = remember(activeCropRatioName, isCropLandscape) {
                                when (activeCropRatioName) {
                                    "1:1" -> 1f
                                    "2:3" -> if (isCropLandscape) 1.5f else 0.667f
                                    "3:4" -> if (isCropLandscape) 1.333f else 0.75f
                                    "16:9" -> if (isCropLandscape) 1.777f else 0.5625f
                                    else -> if (isCropLandscape) 1.333f else 0.75f
                                }
                            }

                            // Render Image with Compose Color Matrix Filters
                            val cMatrix = remember(brightness, contrast, saturation, shadows, highlights, filterName, temperature, tint, exposure) {
                                val cm = ImageFilterProcessor.createColorMatrix(
                                    brightness = brightness,
                                    contrast = contrast,
                                    saturation = saturation,
                                    shadows = shadows,
                                    highlights = highlights,
                                    exposure = exposure,
                                    temperature = temperature,
                                    tint = tint,
                                    filterName = filterName
                                )
                                if (false) {
                                    val dummyCm = android.graphics.ColorMatrix()
                                
                                val b = brightness
                                val bMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                    b, 0f, 0f, 0f, 0f,
                                    0f, b, 0f, 0f, 0f,
                                    0f, 0f, b, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                                cm.postConcat(bMatrix)

                                val c = contrast
                                val translate = (-.5f * c + .5f) * 255f
                                val cMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                    c, 0f, 0f, 0f, translate,
                                    0f, c, 0f, 0f, translate,
                                    0f, 0f, c, 0f, translate,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                                cm.postConcat(cMatrix)

                                val s = saturation
                                val sMatrix = android.graphics.ColorMatrix().apply { setSaturation(s) }
                                cm.postConcat(sMatrix)

                                val shadowsCoeff = shadows * 30f
                                val highlightsCoeff = highlights * 30f
                                val shadowHighlightMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                    1f, 0f, 0f, 0f, shadowsCoeff + highlightsCoeff,
                                    0f, 1f, 0f, 0f, shadowsCoeff + highlightsCoeff,
                                    0f, 0f, 1f, 0f, shadowsCoeff + highlightsCoeff,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                                cm.postConcat(shadowHighlightMatrix)

                                // Exposure adjustments
                                val expMult = if (exposure >= 0f) (1f + exposure) else (1f / (1f - exposure))
                                val exposureMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                    expMult, 0f, 0f, 0f, 0f,
                                    0f, expMult, 0f, 0f, 0f,
                                    0f, 0f, expMult, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                                cm.postConcat(exposureMatrix)

                                // Temperature (Warmth) adjustments (-1f..1f)
                                val tempMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                    1f + temperature * 0.12f, 0f, 0f, 0f, temperature * 15f,
                                    0f, 1f + temperature * 0.05f, 0f, 0f, temperature * 5f,
                                    0f, 0f, 1f - temperature * 0.15f, 0f, -temperature * 20f,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                                cm.postConcat(tempMatrix)

                                // Tint adjustments (-1f..1f)
                                val tintMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                    1f + tint * 0.05f, 0f, 0f, 0f, tint * 5f,
                                    0f, 1f - tint * 0.10f, 0f, 0f, -tint * 10f,
                                    0f, 0f, 1f + tint * 0.05f, 0f, tint * 5f,
                                    0f, 0f, 0f, 1f, 0f
                                ))
                                cm.postConcat(tintMatrix)

                                when (filterName) {
                                    "Monochrome" -> {
                                        val bwMatrix = android.graphics.ColorMatrix().apply { setSaturation(0.0f) }
                                        cm.postConcat(bwMatrix)
                                    }
                                    "Warm Sepia" -> {
                                        val sepiaMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                            0.393f, 0.769f, 0.189f, 0f, 0f,
                                            0.349f, 0.686f, 0.168f, 0f, 0f,
                                            0.272f, 0.534f, 0.131f, 0f, 0f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        cm.postConcat(sepiaMatrix)
                                    }
                                    "Cinematic Vintage" -> {
                                        val vintageMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                            0.95f, 0f, 0f, 0f, 10f,
                                            0f, 0.90f, 0f, 0f, 5f,
                                            0f, 0f, 0.80f, 0f, -10f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        cm.postConcat(vintageMatrix)
                                    }
                                    "Cool Cyan" -> {
                                        val cyanMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                            0.80f, 0f, 0f, 0f, -15f,
                                            0f, 0.95f, 0f, 0f, 10f,
                                            0f, 0f, 1.10f, 0f, 20f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        cm.postConcat(cyanMatrix)
                                    }
                                    "High Contrast Punch" -> {
                                        val punchMatrix = android.graphics.ColorMatrix().apply { setSaturation(1.3f) }
                                        val highContr = android.graphics.ColorMatrix(floatArrayOf(
                                            1.2f, 0f, 0f, 0f, -25f,
                                            0f, 1.2f, 0f, 0f, -25f,
                                            0f, 0f, 1.2f, 0f, -25f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        cm.postConcat(punchMatrix)
                                        cm.postConcat(highContr)
                                    }
                                    "Golden Hour" -> {
                                        val goldenMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                            1.25f, 0f, 0f, 0f, 15f,
                                            0f, 1.10f, 0f, 0f, 5f,
                                            0f, 0f, 0.90f, 0f, -15f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        cm.postConcat(goldenMatrix)
                                    }
                                    "Forest Green" -> {
                                        val forestMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                            0.90f, 0f, 0f, 0f, -10f,
                                            0f, 1.20f, 0f, 0f, 15f,
                                            0f, 0f, 0.95f, 0f, -5f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        cm.postConcat(forestMatrix)
                                    }
                                    "Cyberpunk" -> {
                                        val cyberMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                            1.15f, 0f, 0f, 0f, 25f,
                                            0f, 0.90f, 0f, 0f, -10f,
                                            0f, 0f, 1.25f, 0f, 30f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        cm.postConcat(cyberMatrix)
                                    }
                                    "Moody Noir" -> {
                                        val bw = android.graphics.ColorMatrix().apply { setSaturation(0.05f) }
                                        val highC = android.graphics.ColorMatrix(floatArrayOf(
                                            1.4f, 0f, 0f, 0f, -35f,
                                            0f, 1.4f, 0f, 0f, -35f,
                                            0f, 0f, 1.4f, 0f, -35f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        cm.postConcat(bw)
                                        cm.postConcat(highC)
                                    }
                                    "Soft Pastel" -> {
                                        val pastelMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                            0.95f, 0f, 0f, 0f, 20f,
                                            0f, 0.95f, 0f, 0f, 20f,
                                            0f, 0f, 0.95f, 0f, 20f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        val lowC = android.graphics.ColorMatrix(floatArrayOf(
                                            0.85f, 0f, 0f, 0f, 25f,
                                            0f, 0.85f, 0f, 0f, 25f,
                                            0f, 0f, 0.85f, 0f, 25f,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                        cm.postConcat(pastelMatrix)
                                        cm.postConcat(lowC)
                                    }
                                }
                                } // End of deactivated hardcoded matrix block

                                androidx.compose.ui.graphics.ColorMatrix(cm.array)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        drawContent()
                                        val activeClient = onStageReg
                                        if (activeClient != null && !activeClient.isBookingPaid) {
                                            val textPaint = android.graphics.Paint().apply {
                                                color = android.graphics.Color.argb(100, 255, 255, 255)
                                                textSize = size.width * 0.05f
                                                isAntiAlias = true
                                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                            }
                                            drawContext.canvas.nativeCanvas.apply {
                                                save()
                                                rotate(-30f, size.width / 2f, size.height / 2f)
                                                drawText("PROOF ONLY - UNPAID SESSION", size.width * 0.05f, size.height * 0.35f, textPaint)
                                                drawText("PROOF ONLY - UNPAID SESSION", size.width * 0.05f, size.height * 0.65f, textPaint)
                                                restore()
                                            }
                                        }
                                        // Draw Focal Blur representation overlays if blur is active and we are NOT cropping
                                        if (blurIntensity > 0.0f && !cropMode && editMode != "BRUSH") {
                                            val cx = maskX * size.width
                                            val cy = maskY * size.height
                                            val radius = maskRadius * size.width
                                            
                                            // Soft focus guide circle
                                            drawCircle(
                                                color = accentWarm.copy(alpha = 0.5f),
                                                radius = radius,
                                                center = Offset(cx, cy),
                                                style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                                            )
                                            drawCircle(
                                                color = Color.Black.copy(alpha = 0.4f * blurIntensity),
                                                radius = size.width * 2f,
                                                center = Offset(cx, cy),
                                                style = Stroke(width = size.width * 2f - radius)
                                            )
                                        }

                                        // Draw Live Vignette if enabled
                                        if (vignette != 0f) {
                                            val cx = size.width / 2f
                                            val cy = size.height / 2f
                                            val radius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
                                            val vColor = if (vignette < 0f) {
                                                Color.White.copy(alpha = Math.abs(vignette) * 0.85f)
                                            } else {
                                                Color.Black.copy(alpha = vignette * 0.85f)
                                            }
                                            drawRect(
                                                brush = Brush.radialGradient(
                                                    0f to Color.Transparent,
                                                    0.4f to Color.Transparent,
                                                    1f to vColor,
                                                    center = Offset(cx, cy),
                                                    radius = radius
                                                ),
                                                size = size
                                            )
                                        }

                                        // Draw Brush stroke points live if editMode == "BRUSH"
                                        if (editMode == "BRUSH" && !cropMode) {
                                            brushPoints.forEach { pt ->
                                                val cx = pt.x * size.width
                                                val cy = pt.y * size.height
                                                val radius = pt.radius * size.width * 0.15f // scale size proportionally
                                                
                                                val color = when (pt.type) {
                                                    "BOKEH" -> Color(0xFF81C784).copy(alpha = 0.4f) // soft green for depth blur
                                                    "SHADOW" -> Color(0xFFE57373).copy(alpha = 0.4f) // soft red for shadows
                                                    "FACIAL_BLUR" -> Color(0xFFFFB74D).copy(alpha = 0.6f) // soft skin/orange color for facial blur
                                                    else -> Color(0xFF90A4AE).copy(alpha = 0.5f) // soft gray for clear eraser
                                                }
                                                
                                                // Draw soft radial brush preview gradient
                                                drawCircle(
                                                    brush = Brush.radialGradient(
                                                        colors = listOf(color, color.copy(alpha = 0f)),
                                                        center = Offset(cx, cy),
                                                        radius = maxOf(1f, radius)
                                                    ),
                                                    radius = maxOf(1f, radius),
                                                    center = Offset(cx, cy)
                                                )
                                            }
                                        }
                                    }
                                    // Handle drag to move focus spot OR brush painting gestures
                                    .pointerInput(cropMode, editMode, brushSize, brushType) {
                                        if (!cropMode) {
                                            if (editMode == "BRUSH") {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        // Always save a history state on drag start
                                                        viewModel.saveToHistory()
                                                        // Dispatch first point
                                                        viewModel.addBrushPoint(
                                                            BrushPoint(
                                                                x = offset.x / canvasSize.width,
                                                                y = offset.y / canvasSize.height,
                                                                radius = brushSize,
                                                                type = brushType
                                                            )
                                                        )
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        // Dispatch points continuously as we drag
                                                        val currentPos = change.position
                                                        viewModel.addBrushPoint(
                                                            BrushPoint(
                                                                x = currentPos.x / canvasSize.width,
                                                                y = currentPos.y / canvasSize.height,
                                                                radius = brushSize,
                                                                type = brushType
                                                            )
                                                        )
                                                    }
                                                )
                                            } else {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val newX = (maskX + dragAmount.x / canvasSize.width).coerceIn(0.01f, 0.99f)
                                                    val newY = (maskY + dragAmount.y / canvasSize.height).coerceIn(0.01f, 0.99f)
                                                    viewModel.updateSliders(maskCenterX = newX, maskCenterY = newY)
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (cropMode) {
                                    UnifiedCropRotate(
                                        imageUri = denoisedPreviewUrl ?: photo.fileUri,
                                        cropLeft = cropL,
                                        cropTop = cropT,
                                        cropRight = cropR,
                                        cropBottom = cropB,
                                        straighten = straighten,
                                        aspectRatio = aspectTargetRatio,
                                        onCropRotateChange = { cL, cT, cR, bL, angle ->
                                            viewModel.updateSliders(cropL = cL, cropT = cT, cropR = cR, cropB = bL, straighten = angle)
                                        },
                                        colorMatrix = cMatrix,
                                        accentColor = accentWarm,
                                        borderColor = borderColor,
                                        textColorPrimary = textPrimary,
                                        textColorSecondary = textSecondary,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(context)
                                            .data(denoisedPreviewUrl ?: photo.fileUri)
                                            .size(coil.size.Size.ORIGINAL)
                                            .build(),
                                        contentDescription = "Studio portrait focus viewport",
                                        contentScale = ContentScale.Fit,
                                        colorFilter = ColorFilter.colorMatrix(cMatrix),
                                        modifier = (if (activeCropRatioName == "Original" || activeCropRatioName == "") {
                                            Modifier.fillMaxSize()
                                        } else {
                                            Modifier
                                                .fillMaxHeight()
                                                .aspectRatio(aspectTargetRatio, matchHeightConstraintsFirst = true)
                                        })
                                            .pointerInput(cropMode) {
                                                if (!cropMode) {
                                                    detectTransformGestures { _, pan, zoom, _ ->
                                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                                        val maxPanX = (scale - 1f) * size.width / 2f
                                                        val maxPanY = (scale - 1f) * size.height / 2f
                                                        offset = Offset(
                                                            x = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                                            y = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                                        )
                                                    }
                                                }
                                            }
                                            .graphicsLayer {
                                                if (!cropMode) {
                                                    rotationZ = straighten
                                                    if (scale > 1f) {
                                                        scaleX = scale
                                                        scaleY = scale
                                                        translationX = offset.x
                                                        translationY = offset.y
                                                    } else {
                                                        val cw = cropR - cropL
                                                        val ch = cropB - cropT
                                                        if (cw > 0f && ch > 0f) {
                                                            val baseScale = 1f / maxOf(cw, ch)
                                                            scaleX = baseScale
                                                            scaleY = baseScale
                                                            val centerX = (cropL + cropR) / 2f
                                                            val centerY = (cropT + cropB) / 2f
                                                            translationX = -(centerX - 0.5f) * size.width * baseScale
                                                            translationY = -(centerY - 0.5f) * size.height * baseScale
                                                        }
                                                    }
                                                }
                                            }
                                    )
                                }

                                // Left Chevron (Previous)
                                IconButton(
                                    onClick = { viewModel.selectPreviousPhoto() },
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Photo", tint = Color.White)
                                }

                                // Right Chevron (Next)
                                IconButton(
                                    onClick = { viewModel.selectNextPhoto() },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Photo", tint = Color.White)
                                }

                                // Floating Metadata Overlay Pill
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "ISO 400 • 50mm • f/1.8 • 1/200",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }

                                // Floating Undo Button next to the image itself in the top-left corner
                                if (canUndo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(12.dp)
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.undoLastStep() },
                                            colors = IconButtonDefaults.iconButtonColors(
                                                containerColor = Color.Black.copy(alpha = 0.7f),
                                                contentColor = accentWarm
                                            ),
                                            modifier = Modifier
                                                .size(36.dp)
                                                .shadow(4.dp, CircleShape)
                                                .border(1.dp, borderColor, CircleShape)
                                                .testTag("floating_undo_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Undo,
                                                contentDescription = "Undo Last Step",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // CROP MODE BOUNDING OVERLAYS REMOVED - HANDLED VIA UNIFIED GESTURE OVERLAY COMPONENT IN THE EDITOR MODULE
                                if (false) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(Unit) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        val cxLeft = cropL * canvasSize.width
                                                        val cxTop = cropT * canvasSize.height
                                                        val cxRight = cropR * canvasSize.width
                                                        val cxBottom = cropB * canvasSize.height
                                                        
                                                        // Generous grab radius of 80f for easier control on tablets
                                                        activeCropHandle = when {
                                                            offset.x in (cxLeft - 80f)..(cxLeft + 80f) && offset.y in (cxTop - 80f)..(cxTop + 80f) -> "TL"
                                                            offset.x in (cxRight - 80f)..(cxRight + 80f) && offset.y in (cxTop - 80f)..(cxTop + 80f) -> "TR"
                                                            offset.x in (cxLeft - 80f)..(cxLeft + 80f) && offset.y in (cxBottom - 80f)..(cxBottom + 80f) -> "BL"
                                                            offset.x in (cxRight - 80f)..(cxRight + 80f) && offset.y in (cxBottom - 80f)..(cxBottom + 80f) -> "BR"
                                                            offset.x in (cxLeft + 80f)..(cxRight - 80f) && offset.y in (cxTop + 80f)..(cxBottom - 80f) -> "CENTER"
                                                            else -> null
                                                        }
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val handle = activeCropHandle ?: return@detectDragGestures
                                                        val dX = dragAmount.x / canvasSize.width
                                                        val dY = dragAmount.y / canvasSize.height

                                                        var nL = cropL
                                                        var nT = cropT
                                                        var nR = cropR
                                                        var nB = cropB

                                                        when (handle) {
                                                            "TL" -> {
                                                                nL = (cropL + dX).coerceIn(0f, cropR - 0.1f)
                                                                nT = (cropT + dY).coerceIn(0f, cropB - 0.1f)
                                                            }
                                                            "TR" -> {
                                                                nR = (cropR + dX).coerceIn(cropL + 0.1f, 1f)
                                                                nT = (cropT + dY).coerceIn(0f, cropB - 0.1f)
                                                            }
                                                            "BL" -> {
                                                                nL = (cropL + dX).coerceIn(0f, cropR - 0.1f)
                                                                nB = (cropB + dY).coerceIn(cropT + 0.1f, 1f)
                                                            }
                                                            "BR" -> {
                                                                nR = (cropR + dX).coerceIn(cropL + 0.1f, 1f)
                                                                nB = (cropB + dY).coerceIn(cropT + 0.1f, 1f)
                                                            }
                                                            "CENTER" -> {
                                                                val width = cropR - cropL
                                                                val height = cropB - cropT
                                                                val newL = (cropL + dX).coerceIn(0f, 1f - width)
                                                                val newT = (cropT + dY).coerceIn(0f, 1f - height)
                                                                nL = newL
                                                                nT = newT
                                                                nR = newL + width
                                                                nB = newT + height
                                                            }
                                                        }
                                                        viewModel.updateSliders(cropL = nL, cropT = nT, cropR = nR, cropB = nB)
                                                    }
                                                )
                                            }
                                    ) {
                                        val l = cropL * size.width
                                        val t = cropT * size.height
                                        val r = cropR * size.width
                                        val b = cropB * size.height

                                        // Darken outside crop window
                                        drawRect(color = Color.Black.copy(alpha = 0.6f), size = Size(l, size.height))
                                        drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(l, 0f), size = Size(r - l, t))
                                        drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(r, 0f), size = Size(size.width - r, size.height))
                                        drawRect(color = Color.Black.copy(alpha = 0.6f), topLeft = Offset(l, b), size = Size(r - l, size.height - b))

                                        // Draw crop window borders
                                        drawRect(
                                            color = accentWarm,
                                            topLeft = Offset(l, t),
                                            size = Size(r - l, b - t),
                                            style = Stroke(width = 3f)
                                        )

                                        // Grid lines (Rule of Thirds)
                                        val col1 = l + (r - l) / 3f
                                        val col2 = l + 2 * (r - l) / 3f
                                        val row1 = t + (b - t) / 3f
                                        val row2 = t + 2 * (b - t) / 3f

                                        drawLine(Color.White.copy(alpha = 0.4f), Offset(col1, t), Offset(col1, b), strokeWidth = 1f)
                                        drawLine(Color.White.copy(alpha = 0.4f), Offset(col2, t), Offset(col2, b), strokeWidth = 1f)
                                        drawLine(Color.White.copy(alpha = 0.4f), Offset(l, row1), Offset(r, row1), strokeWidth = 1f)
                                        drawLine(Color.White.copy(alpha = 0.4f), Offset(l, row2), Offset(r, row2), strokeWidth = 1f)

                                        // Draw beautiful professional bracket corners instead of just simple circles!
                                        val handleLen = 30f
                                        val handleStroke = 6f
                                        
                                        // Top Left Corner
                                        drawLine(accentWarm, Offset(l, t), Offset(l + handleLen, t), strokeWidth = handleStroke)
                                        drawLine(accentWarm, Offset(l, t), Offset(l, t + handleLen), strokeWidth = handleStroke)
                                        
                                        // Top Right Corner
                                        drawLine(accentWarm, Offset(r, t), Offset(r - handleLen, t), strokeWidth = handleStroke)
                                        drawLine(accentWarm, Offset(r, t), Offset(r, t + handleLen), strokeWidth = handleStroke)

                                        // Bottom Left Corner
                                        drawLine(accentWarm, Offset(l, b), Offset(l + handleLen, b), strokeWidth = handleStroke)
                                        drawLine(accentWarm, Offset(l, b), Offset(l, b - handleLen), strokeWidth = handleStroke)

                                        // Bottom Right Corner
                                        drawLine(accentWarm, Offset(r, b), Offset(r - handleLen, b), strokeWidth = handleStroke)
                                        drawLine(accentWarm, Offset(r, b), Offset(r, b - handleLen), strokeWidth = handleStroke)
                                    }
                                }
                            }
                        }

                        if (showSessionCaptures) {
                            Spacer(modifier = Modifier.height(12.dp))

                            // Carousel of photos under the main photo
                            Text(
                                text = "SESSION CAPTURES (${filteredPhotos.size} SHOTS)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            if (filteredPhotos.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No shots taken yet", fontSize = 11.sp, color = textSecondary)
                                }
                            } else {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredPhotos) { p ->
                                        val isSel = selectedPhoto?.id == p.id
                                        Box(
                                            modifier = Modifier
                                                .size(90.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    width = if (isSel) 2.5.dp else 1.dp,
                                                    color = if (isSel) accentWarm else if (p.isPublished) Color(0xFF2E7D32) else borderColor,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.selectPhoto(p) }
                                        ) {
                                            AsyncImage(
                                                model = p.fileUri,
                                                contentDescription = "Session capture thumbnail",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer { rotationZ = p.straighten }
                                            )
                                            
                                            // Indicators on thumbnail
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .fillMaxWidth()
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (p.rating > 0) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(10.dp))
                                                        Text("${p.rating}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.width(1.dp))
                                                }
                                                if (p.isFlagged) {
                                                    Icon(Icons.Default.Flag, contentDescription = "Flagged", tint = Color(0xFFEF5350), modifier = Modifier.size(10.dp))
                                                }
                                            }

                                            if (p.isPublished) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF2E7D32)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = "Published", tint = Color.White, modifier = Modifier.size(8.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                } // End Box wrapping editor
                } // End Column of Center Panel
            } // End Center Panel Surface

            if (actualShowRightPanel) {
                // --- RIGHT PANEL: Advanced Lightroom-Style Edit adjustments (Width: 33%) ---
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    color = panelBackground,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = accentWarm, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "EDIT ADJUSTMENTS",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentWarm,
                                    letterSpacing = 1.sp
                                )
                            }
                            IconButton(
                                onClick = { showRightPanel = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Panel", tint = textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedPhoto == null) {
                            Box(
                                modifier = Modifier.fillMaxSize().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Select a photo to edit", color = textSecondary, fontSize = 12.sp)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .imePadding()
                            ) {
                                // PRESET / FILTER PICKER QUICK ROW
                                Text(
                                    text = "ARTISTIC STYLES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                val filtersList = listOf(
                                    "None", "Monochrome", "Warm Sepia", "Cinematic Vintage", 
                                    "Cool Cyan", "High Contrast Punch", "Golden Hour", 
                                    "Forest Green", "Cyberpunk", "Moody Noir", "Soft Pastel"
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(filtersList) { f ->
                                        val isSel = filterName == f
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) accentWarm else borderColor)
                                                .clickable { viewModel.updateSliders(filterName = f) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = f,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) accentDark else textPrimary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                 // SLIDERS INFO CARD
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = borderColor.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = accentWarm, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Double-tap any slider label to reset it to default.",
                                            fontSize = 9.sp,
                                            color = textSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "LIGHTING & EXPOSURE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                // Brightness (Default: 1.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastBrightness by remember { mutableStateOf(brightness) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(brightness = 1f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Brightness", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%.2f", brightness), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = brightness,
                                        onValueChange = { newVal ->
                                            if ((lastBrightness < 1f && newVal >= 1f) || (lastBrightness > 1f && newVal <= 1f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastBrightness = newVal
                                            viewModel.updateSliders(brightness = newVal)
                                        },
                                        valueRange = 0f..2f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentWarm,
                                            activeTrackColor = accentWarm,
                                            inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                EditorSliders(
                                    initialExposure = exposure,
                                    initialContrast = contrast,
                                    initialSaturation = saturation,
                                    onValuesChanged = { expVal, contrVal, satVal ->
                                        viewModel.updateSliders(
                                            exposure = expVal,
                                            contrast = contrVal,
                                            saturation = satVal
                                        )
                                    },
                                    accentColor = accentWarm,
                                    borderColor = borderColor,
                                    textColorPrimary = textPrimary,
                                    textColorSecondary = textSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                // Highlights (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastHighlights by remember { mutableStateOf(highlights) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(highlights = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Highlights", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%+.2f", highlights), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = highlights,
                                        onValueChange = { newVal ->
                                            if ((lastHighlights < 0f && newVal >= 0f) || (lastHighlights > 0f && newVal <= 0f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                             }
                                             lastHighlights = newVal
                                             viewModel.updateSliders(highlights = newVal)
                                         },
                                         valueRange = -1f..1f,
                                         colors = SliderDefaults.colors(
                                             thumbColor = accentWarm,
                                             activeTrackColor = accentWarm,
                                             inactiveTrackColor = borderColor
                                         ),
                                         modifier = Modifier.height(32.dp)
                                     )
                                }

                                // Shadows (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastShadows by remember { mutableStateOf(shadows) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(shadows = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Shadows", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%+.2f", shadows), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = shadows,
                                        onValueChange = { newVal ->
                                            if ((lastShadows < 0f && newVal >= 0f) || (lastShadows > 0f && newVal <= 0f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastShadows = newVal
                                            viewModel.updateSliders(shadows = newVal)
                                        },
                                        valueRange = -1f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentWarm,
                                            activeTrackColor = accentWarm,
                                            inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "COLOR & TONE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )



                                // Whites (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastWhites by remember { mutableStateOf(whites) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(whites = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Whites", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%+.2f", whites), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = whites,
                                        onValueChange = { newVal ->
                                            if ((lastWhites < 0f && newVal >= 0f) || (lastWhites > 0f && newVal <= 0f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastWhites = newVal
                                            viewModel.updateSliders(whites = newVal)
                                        },
                                        valueRange = -1f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentWarm,
                                            activeTrackColor = accentWarm,
                                            inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                // Blacks (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastBlacks by remember { mutableStateOf(blacks) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(blacks = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Blacks", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%+.2f", blacks), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = blacks,
                                        onValueChange = { newVal ->
                                            if ((lastBlacks < 0f && newVal >= 0f) || (lastBlacks > 0f && newVal <= 0f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastBlacks = newVal
                                            viewModel.updateSliders(blacks = newVal)
                                        },
                                        valueRange = -1f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentWarm,
                                            activeTrackColor = accentWarm,
                                            inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                // Temperature (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastTemperature by remember { mutableStateOf(temperature) }
                                    Row(
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .pointerInput(Unit) {
                                                 detectTapGestures(
                                                     onDoubleTap = {
                                                         viewModel.updateSliders(temperature = 0f)
                                                         haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                     }
                                                 )
                                             },
                                         horizontalArrangement = Arrangement.SpaceBetween,
                                         verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Temperature", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%+.2f", temperature), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = temperature,
                                        onValueChange = { newVal ->
                                            if ((lastTemperature < 0f && newVal >= 0f) || (lastTemperature > 0f && newVal <= 0f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastTemperature = newVal
                                            viewModel.updateSliders(temperature = newVal)
                                        },
                                        valueRange = -1f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentWarm,
                                            activeTrackColor = accentWarm,
                                            inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                // Tint (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastTint by remember { mutableStateOf(tint) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(tint = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Tint", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%+.2f", tint), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = tint,
                                        onValueChange = { newVal ->
                                            if ((lastTint < 0f && newVal >= 0f) || (lastTint > 0f && newVal <= 0f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastTint = newVal
                                            viewModel.updateSliders(tint = newVal)
                                        },
                                        valueRange = -1f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentWarm,
                                            activeTrackColor = accentWarm,
                                            inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "EFFECTS & FOCUS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                // Focal Blur (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastBlur by remember { mutableStateOf(blurIntensity) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(blurIntensity = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Focal Blur", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%.2f", blurIntensity), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = blurIntensity,
                                        onValueChange = { newVal ->
                                            if ((lastBlur < 0.01f && newVal >= 0.01f) || (lastBlur > 0.01f && newVal <= 0.01f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastBlur = newVal
                                            viewModel.updateSliders(blurIntensity = newVal)
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentWarm,
                                            activeTrackColor = accentWarm,
                                            inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                // Radial Mask Radius (Default: 0.3f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(maskRadius = 0.3f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Radial Mask Radius", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%.2f", maskRadius), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = maskRadius,
                                        onValueChange = { newVal ->
                                            viewModel.updateSliders(maskRadius = newVal)
                                        },
                                        valueRange = 0.1f..1f,
                                        colors = SliderDefaults.colors(
                                             thumbColor = accentWarm,
                                             activeTrackColor = accentWarm,
                                             inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Master Focal Blur & Custom Brush Switch Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
                                    border = BorderStroke(1.dp, borderColor),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Brush, contentDescription = null, tint = accentWarm, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Focal Blur & Custom Brush", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                            }
                                            Switch(
                                                checked = editMode == "BRUSH" || editMode == "FOCAL_SPOT",
                                                onCheckedChange = { active ->
                                                    if (active) {
                                                        viewModel.setEditMode("BRUSH")
                                                    } else {
                                                        viewModel.setEditMode("NONE")
                                                    }
                                                },
                                                modifier = Modifier.testTag("brush_master_switch"),
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = accentWarm,
                                                    checkedTrackColor = accentWarm.copy(alpha = 0.5f),
                                                    uncheckedThumbColor = textSecondary,
                                                    uncheckedTrackColor = borderColor
                                                )
                                            )
                                        }

                                        if (editMode == "BRUSH" || editMode == "FOCAL_SPOT") {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            
                                            // Row of custom Material 3 FilterChips for "Brush Mode" options ("BRUSH" or "SPOT" mode)
                                            Text("BRUSH MODE OPTIONS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterChip(
                                                    selected = editMode == "FOCAL_SPOT",
                                                    onClick = { viewModel.setEditMode("FOCAL_SPOT") },
                                                    label = { Text("Spot Focus", fontSize = 10.sp) },
                                                    modifier = Modifier.testTag("brush_mode_spot_chip"),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = accentWarm,
                                                        selectedLabelColor = Color.Black
                                                    )
                                                )
                                                FilterChip(
                                                    selected = editMode == "BRUSH",
                                                    onClick = { viewModel.setEditMode("BRUSH") },
                                                    label = { Text("Paint Brush", fontSize = 10.sp) },
                                                    modifier = Modifier.testTag("brush_mode_paint_chip"),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = accentWarm,
                                                        selectedLabelColor = Color.Black
                                                    )
                                                )
                                            }

                                            if (editMode == "BRUSH") {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                // Brush category choices: chips/buttons for "BOKEH", "SHADOW", or "CLEAR"
                                                Text("BRUSH TYPES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Button(
                                                        onClick = { viewModel.setBrushType("BOKEH") },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (brushType == "BOKEH") Color(0xFF81C784) else borderColor,
                                                            contentColor = if (brushType == "BOKEH") Color.Black else textPrimary
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier.weight(1f).testTag("brush_type_bokeh")
                                                    ) {
                                                        Text("Bokeh", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Button(
                                                        onClick = { viewModel.setBrushType("SHADOW") },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (brushType == "SHADOW") Color(0xFFE57373) else borderColor,
                                                            contentColor = if (brushType == "SHADOW") Color.Black else textPrimary
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier.weight(1f).testTag("brush_type_shadow")
                                                    ) {
                                                        Text("Shadow", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Button(
                                                        onClick = { viewModel.setBrushType("CLEAR") },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (brushType == "CLEAR") Color(0xFF90A4AE) else borderColor,
                                                            contentColor = if (brushType == "CLEAR") Color.Black else textPrimary
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier.weight(1f).testTag("brush_type_clear")
                                                    ) {
                                                        Text("Eraser", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Brush Size: A slider to adjust the brush stroke diameter
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Brush Size", fontSize = 10.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                                    Text(String.format("%.1f px", brushSize * 100f), fontSize = 9.sp, color = accentWarm)
                                                }
                                                Slider(
                                                    value = brushSize,
                                                    onValueChange = { viewModel.setBrushSize(it) },
                                                    valueRange = 0.05f..0.5f,
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = accentWarm,
                                                        activeTrackColor = accentWarm,
                                                        inactiveTrackColor = borderColor
                                                    ),
                                                    modifier = Modifier.height(32.dp).testTag("brush_size_slider")
                                                )
                                            }
                                        }
                                    }
                                }

                                // Vignette (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastVignette by remember { mutableStateOf(vignette) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(vignette = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Vignette", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%.2f", vignette), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = vignette,
                                        onValueChange = { newVal ->
                                            if ((lastVignette < 0.01f && newVal >= 0.01f) || (lastVignette > 0.01f && newVal <= 0.01f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastVignette = newVal
                                            viewModel.updateSliders(vignette = newVal)
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentWarm,
                                            activeTrackColor = accentWarm,
                                            inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                // Sharpness (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastSharpness by remember { mutableStateOf(sharpness) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(sharpness = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Sharpen / Sharpness", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%.2f", sharpness), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Slider(
                                        value = sharpness,
                                        onValueChange = { newVal ->
                                            if ((lastSharpness < 0.01f && newVal >= 0.01f) || (lastSharpness > 0.01f && newVal <= 0.01f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastSharpness = newVal
                                            viewModel.updateSliders(sharpness = newVal)
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = accentWarm,
                                            activeTrackColor = accentWarm,
                                            inactiveTrackColor = borderColor
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "GEOMETRY & FRAMES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                // Straighten (Default: 0.0f)
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    var lastStraighten by remember { mutableStateOf(straighten) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        viewModel.updateSliders(straighten = 0f)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Straighten", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                                        Text(String.format("%+.1f°", straighten), fontSize = 10.sp, color = accentWarm)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Precise horizontal gesture rotation dial
                                    RotationDial(
                                        angle = straighten,
                                        onAngleChange = { newVal ->
                                            if ((lastStraighten < 0f && newVal >= 0f) || (lastStraighten > 0f && newVal <= 0f)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastStraighten = newVal
                                            viewModel.updateSliders(straighten = newVal)
                                        },
                                        range = -45f..45f,
                                        accentColor = accentWarm,
                                        borderColor = borderColor,
                                        textColorPrimary = textPrimary,
                                        textColorSecondary = textSecondary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    )
                                    Text(
                                        text = "Swipe/drag dial to rotate precisely. Double-tap to reset.",
                                        fontSize = 9.sp,
                                        color = textSecondary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                 Spacer(modifier = Modifier.height(8.dp))

                                Spacer(modifier = Modifier.height(16.dp))

                                // RATING & FLAGS CARD
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
                                    border = BorderStroke(1.dp, borderColor),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Rating & Culling", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                for (star in 1..5) {
                                                    val isFilled = star <= rating
                                                    IconButton(
                                                        onClick = { viewModel.updateSliders(rating = star) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                                            contentDescription = "Star $star",
                                                            tint = if (isFilled) accentWarm else textSecondary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = { viewModel.updateSliders(isFlagged = !isFlagged) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Flag,
                                                    contentDescription = "Flag photo",
                                                    tint = if (isFlagged) Color(0xFFEF5350) else textSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Preset Preferences Engine Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
                                    border = BorderStroke(1.dp, borderColor),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Auto-Apply Edits to Next Shot", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Switch(
                                                checked = settings.autoApplyPresets,
                                                onCheckedChange = { viewModel.toggleAutoApplyPresets(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = accentWarm,
                                                    checkedTrackColor = accentWarm.copy(alpha = 0.5f)
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.copyEdits() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = borderColor,
                                                    contentColor = textPrimary
                                                ),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Copy Edits", fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = { viewModel.pasteEdits() },
                                                enabled = hasCopiedEdits,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = accentWarm,
                                                    contentColor = Color.Black
                                                ),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Paste Edits", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = { viewModel.saveEditingAsPreset() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = borderColor,
                                                contentColor = textPrimary
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Lock Current Edits as Preset", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // LOCAL EXPORT & SHARE PANEL
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2A36)),
                                    border = BorderStroke(1.dp, accentWarm.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "LOCAL EXPORT & SHARE",
                                            color = accentWarm,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { 
                                                    selectedPhoto?.let { photo ->
                                                        selectedPhotosForExport[photo.id] = photo
                                                    }
                                                    currentPage = 4
                                                },
                                                enabled = selectedPhoto != null,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF3F3B4A),
                                                    contentColor = textPrimary
                                                ),
                                                modifier = Modifier.weight(1f).height(40.dp).testTag("save_gallery_button"),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Export Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = { viewModel.shareSelectedPhoto(context) },
                                                enabled = selectedPhoto != null,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = accentWarm,
                                                    contentColor = accentDark
                                                ),
                                                modifier = Modifier.weight(1f).height(40.dp).testTag("share_sheet_button"),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Share Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // BIG COMMIT & PUBLISH ACTION BUTTON (ONLY SHOW IF CLIENT IS SELECTED)
                                if (onStageReg != null) {
                                    Button(
                                        onClick = {
                                            if (selectedPhoto?.isPublished == true) {
                                                showOverwriteDialog = true
                                            } else {
                                                viewModel.commitAndPublishPhoto(replaceExisting = false)
                                            }
                                        },
                                        enabled = selectedPhoto != null && !isPublishing,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accentWarm,
                                            contentColor = accentDark
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("commit_publish_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (isPublishing) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = accentDark, strokeWidth = 3.dp)
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "COMMIT & ASSIGN TO ${onStageReg!!.name.uppercase()}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    letterSpacing = 1.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Feedback notice when no client is selected
                                    Surface(
                                        color = Color(0xFFEF5350).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Please select a client from the Queue before committing and publishing portraits.",
                                                color = Color(0xFFEF5350),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        measurePolicy = MeasurePolicy { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                
                if (isPortrait) {
                    if (measurables.size == 2) {
                        val sideIndex = if (actualShowRightPanel) 1 else 0
                        val centerIndex = if (actualShowRightPanel) 0 else 1
                        
                        val ratio = 0.58f + 
                            (if (!showSessionCaptures) 0.12f else 0f) + 
                            (if (!showTopHeader) 0.05f else 0f)
                        val centerHeight = (height * ratio.coerceAtMost(0.80f)).toInt()
                        val sideHeight = height - centerHeight
                        
                        val centerPlaceable = measurables[centerIndex].measure(
                            Constraints(
                                minWidth = width,
                                maxWidth = width,
                                minHeight = centerHeight,
                                maxHeight = centerHeight
                            )
                        )
                        val sidePlaceable = measurables[sideIndex].measure(
                            Constraints(
                                minWidth = width,
                                maxWidth = width,
                                minHeight = sideHeight,
                                maxHeight = sideHeight
                            )
                        )
                        
                        layout(width, height) {
                            centerPlaceable.place(0, 0)
                            sidePlaceable.place(0, centerHeight)
                        }
                    } else {
                        val centerPlaceable = measurables[0].measure(constraints)
                        layout(width, height) {
                            centerPlaceable.place(0, 0)
                        }
                    }
                } else {
                    if (measurables.size == 2) {
                        val sideIndex = if (actualShowRightPanel) 1 else 0
                        val centerIndex = if (actualShowRightPanel) 0 else 1
                        
                        val centerWidth = (width * 0.67f).toInt()
                        val sideWidth = width - centerWidth
                        
                        val centerPlaceable = measurables[centerIndex].measure(
                            Constraints(
                                minWidth = centerWidth,
                                maxWidth = centerWidth,
                                minHeight = height,
                                maxHeight = height
                            )
                        )
                        val sidePlaceable = measurables[sideIndex].measure(
                            Constraints(
                                minWidth = sideWidth,
                                maxWidth = sideWidth,
                                minHeight = height,
                                maxHeight = height
                            )
                        )
                        
                        layout(width, height) {
                            if (actualShowRightPanel) {
                                centerPlaceable.place(0, 0)
                                sidePlaceable.place(centerWidth, 0)
                            } else {
                                sidePlaceable.place(0, 0)
                                centerPlaceable.place(sideWidth, 0)
                            }
                        }
                    } else {
                        val centerPlaceable = measurables[0].measure(constraints)
                        layout(width, height) {
                            centerPlaceable.place(0, 0)
                        }
                    }
                }
            }
        )

        // Floating Restore Header Button
        if (!showTopHeader) {
            Surface(
                color = panelBackground.copy(alpha = 0.85f),
                shape = RoundedCornerShape(bottomStart = 16.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                IconButton(
                    onClick = { showTopHeader = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Restore Header",
                        tint = accentWarm
                    )
                }
            }
        }
    } // End of Box content wrapper
}

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Publish Options",
                        tint = accentWarm,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Portrait Already Published",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This photo has already been uploaded to the database.",
                        color = textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Would you like to replace/overwrite the existing cloud portrait, or save it as a new copy?",
                        color = textSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            containerColor = panelBackground,
            shape = RoundedCornerShape(16.dp),
            confirmButton = {
                Button(
                    onClick = {
                        showOverwriteDialog = false
                        viewModel.commitAndPublishPhoto(replaceExisting = true)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentWarm,
                        contentColor = accentDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replace Existing", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showOverwriteDialog = false
                            viewModel.commitAndPublishPhoto(replaceExisting = false)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = borderColor,
                            contentColor = textPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save as New Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { showOverwriteDialog = false }
                    ) {
                        Text("Cancel", color = textSecondary, fontSize = 12.sp)
                    }
                }
            }
        )
    }

    if (showCreatePhotoshootDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePhotoshootDialog = false },
            title = { Text("Create Local Photoshoot", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = newPhotoshootName,
                        onValueChange = { newPhotoshootName = it },
                        label = { Text("Photoshoot Name") },
                        modifier = Modifier.fillMaxWidth().testTag("photoshoot_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentWarm,
                            focusedLabelColor = accentWarm,
                            unfocusedBorderColor = borderColor
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        )
                    )
                    OutlinedTextField(
                        value = newPhotoshootLocation,
                        onValueChange = { newPhotoshootLocation = it },
                        label = { Text("Location") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentWarm,
                            focusedLabelColor = accentWarm,
                            unfocusedBorderColor = borderColor
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        )
                    )
                    OutlinedTextField(
                        value = newPhotoshootDate,
                        onValueChange = { newPhotoshootDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentWarm,
                            focusedLabelColor = accentWarm,
                            unfocusedBorderColor = borderColor
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (newPhotoshootName.isNotEmpty()) {
                                    viewModel.createPhotoshootEvent(
                                        name = newPhotoshootName,
                                        location = newPhotoshootLocation,
                                        date = if (newPhotoshootDate.isEmpty()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) else newPhotoshootDate
                                    )
                                    showCreatePhotoshootDialog = false
                                    newPhotoshootName = ""
                                    newPhotoshootLocation = ""
                                    newPhotoshootDate = ""
                                }
                            }
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPhotoshootName.isNotEmpty()) {
                            viewModel.createPhotoshootEvent(
                                name = newPhotoshootName,
                                location = newPhotoshootLocation,
                                date = if (newPhotoshootDate.isEmpty()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) else newPhotoshootDate
                            )
                            showCreatePhotoshootDialog = false
                            newPhotoshootName = ""
                            newPhotoshootLocation = ""
                            newPhotoshootDate = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentWarm),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Create & Select", color = accentDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePhotoshootDialog = false }) {
                    Text("Cancel", color = textPrimary)
                }
            },
            containerColor = panelBackground
        )
    }

    if (showAddClientDialog) {
        var clientName by remember { mutableStateOf("") }
        var clientEmail by remember { mutableStateOf("") }
        var clientPets by remember { mutableStateOf("") }
        var clientNotes by remember { mutableStateOf("") }
        var hasError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddClientDialog = false },
            title = {
                Text(
                    text = "REGISTER NEW CLIENT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = accentWarm,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Manually onboard a client. Their profile will be saved locally and pushed instantly to the linked Firestore Event database.",
                        color = textSecondary,
                        fontSize = 11.sp
                    )

                    if (activeConfigId == null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ NO SHOOT EVENT IS SELECTED! Please select an active shoot event from the top right menu first before adding clients.",
                                color = Color(0xFFEF5350),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = clientName,
                        onValueChange = { clientName = it; hasError = false },
                        label = { Text("Full Name", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            autoCorrect = false,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentWarm,
                            focusedLabelColor = accentWarm,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        isError = hasError && clientName.trim().isEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("manual_client_name")
                    )

                    OutlinedTextField(
                        value = clientEmail,
                        onValueChange = { clientEmail = it },
                        label = { Text("Email Address", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            autoCorrect = false,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentWarm,
                            focusedLabelColor = accentWarm,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("manual_client_email")
                    )

                    OutlinedTextField(
                        value = clientPets,
                        onValueChange = { clientPets = it },
                        label = { Text("Pet Name / Breeds", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            autoCorrect = false,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentWarm,
                            focusedLabelColor = accentWarm,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("manual_client_pets")
                    )

                    OutlinedTextField(
                        value = clientNotes,
                        onValueChange = { clientNotes = it },
                        label = { Text("Special Requests / Notes", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            autoCorrect = false,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (clientName.trim().isEmpty()) {
                                    hasError = true
                                } else if (activeConfigId != null) {
                                    viewModel.manuallyCreateClient(
                                        name = clientName.trim(),
                                        email = clientEmail.trim(),
                                        pets = clientPets.trim(),
                                        notes = clientNotes.trim()
                                    ) {
                                        showAddClientDialog = false
                                    }
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentWarm,
                            focusedLabelColor = accentWarm,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("manual_client_notes")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (clientName.trim().isEmpty()) {
                            hasError = true
                        } else if (activeConfigId != null) {
                            viewModel.manuallyCreateClient(
                                name = clientName.trim(),
                                email = clientEmail.trim(),
                                pets = clientPets.trim(),
                                notes = clientNotes.trim()
                            ) {
                                showAddClientDialog = false
                            }
                        }
                    },
                    enabled = activeConfigId != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentWarm,
                        contentColor = accentDark
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("save_manual_client_btn")
                ) {
                    Text("Save Client", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddClientDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = textSecondary)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = panelBackground
        )
    }

    if (showClientSelectionDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredRegistrations = remember(searchQuery, registrations) {
            if (searchQuery.isBlank()) {
                registrations
            } else {
                registrations.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.pets.contains(searchQuery, ignoreCase = true) ||
                    it.email.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showClientSelectionDialog = false },
            title = {
                Text(
                    text = "SEARCH & SELECT CLIENT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = accentWarm,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f)
                        .imePadding()
                ) {
                    Text(
                        text = "Filter and select an onboarded client to put them on stage.",
                        color = textSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search by name, pet, or email", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = textSecondary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = textSecondary
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentWarm,
                            focusedLabelColor = accentWarm,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("client_search_input")
                    )

                    if (filteredRegistrations.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching clients found.",
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredRegistrations) { reg ->
                                val isSelected = onStageReg?.id == reg.id
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) accentDark else Color(0xFF24222A)
                                    ),
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (isSelected) accentWarm else borderColor
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectOnStageClient(reg)
                                            showClientSelectionDialog = false
                                        },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = reg.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = textPrimary
                                            )
                                            if (reg.pets.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "🐾 ${reg.pets}",
                                                    fontSize = 13.sp,
                                                    color = textSecondary
                                                )
                                            }
                                            if (reg.notes.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Notes: ${reg.notes}",
                                                    fontSize = 12.sp,
                                                    color = textSecondary.copy(alpha = 0.8f),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(horizontalAlignment = Alignment.End) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (reg.photosUploaded > 0) Color(0xFF2E7D32) else borderColor)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "${reg.photosUploaded} photos",
                                                    color = textPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "ON STAGE",
                                                    color = accentWarm,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showClientSelectionDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = accentWarm)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = panelBackground
        )
    }

    if (showExifDialog && selectedPhoto != null) {
        val photo = selectedPhoto!!
        val exifDetails = remember(photo.id) {
            val meta = mutableMapOf<String, String>()
            meta["File Name"] = photo.fileName
            meta["Import Date"] = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(photo.timestamp))
            
            // Try to read File Size
            try {
                val file = File(photo.fileUri)
                if (file.exists()) {
                    val kb = file.length() / 1024
                    meta["File Size"] = if (kb > 1024) String.format("%.2f MB", kb / 1024f) else "$kb KB"
                    meta["File Path"] = file.absolutePath
                } else {
                    meta["File Size"] = "Unknown"
                    meta["File Path"] = photo.fileUri
                }
            } catch (e: Exception) {
                meta["File Size"] = "Unknown"
                meta["File Path"] = photo.fileUri
            }

            // EXIF Tags Extraction
            try {
                val exif = if (photo.fileUri.startsWith("content://")) {
                    context.contentResolver.openFileDescriptor(Uri.parse(photo.fileUri), "r")?.use { pfd ->
                        android.media.ExifInterface(pfd.fileDescriptor)
                    }
                } else {
                    val file = File(photo.fileUri)
                    if (file.exists()) {
                        android.media.ExifInterface(file.absolutePath)
                    } else null
                }

                if (exif != null) {
                    val make = exif.getAttribute(android.media.ExifInterface.TAG_MAKE)
                    val model = exif.getAttribute(android.media.ExifInterface.TAG_MODEL)
                    val aperture = exif.getAttribute(android.media.ExifInterface.TAG_F_NUMBER)
                    val shutter = exif.getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME)
                    val iso = exif.getAttribute(android.media.ExifInterface.TAG_ISO_SPEED_RATINGS)
                    val focal = exif.getAttribute(android.media.ExifInterface.TAG_FOCAL_LENGTH)
                    val datetime = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME) ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                    val width = exif.getAttribute(android.media.ExifInterface.TAG_IMAGE_WIDTH)
                    val height = exif.getAttribute(android.media.ExifInterface.TAG_IMAGE_LENGTH)

                    if (!make.isNullOrBlank()) meta["Camera Make"] = make
                    if (!model.isNullOrBlank()) meta["Camera Model"] = model
                    if (width != null && height != null && width != "0" && height != "0") {
                        meta["Dimensions"] = "${width}x${height} px"
                    }
                    if (!aperture.isNullOrBlank()) meta["Aperture"] = "f/$aperture"
                    if (!shutter.isNullOrBlank()) {
                        meta["Shutter Speed"] = try {
                            val d = shutter.toDouble()
                            if (d > 0.0 && d < 1.0) "1/${Math.round(1.0 / d)} sec" else "$shutter sec"
                        } catch (e: Exception) {
                            "$shutter sec"
                        }
                    }
                    if (!iso.isNullOrBlank()) meta["ISO Speed"] = "ISO $iso"
                    if (!focal.isNullOrBlank()) {
                        meta["Focal Length"] = try {
                            "${focal.toDouble().toInt()} mm"
                        } catch (e: Exception) {
                            "$focal mm"
                        }
                    }
                    if (!datetime.isNullOrBlank()) meta["Captured Date"] = datetime
                }
            } catch (e: Exception) {
                meta["EXIF Status"] = "Could not parse: ${e.localizedMessage}"
            }

            // GPS Coordinates
            if (photo.gpsLatitude != 0.0 || photo.gpsLongitude != 0.0) {
                meta["GPS Coordinates"] = String.format("%.5f, %.5f", photo.gpsLatitude, photo.gpsLongitude)
            } else {
                meta["GPS Coordinates"] = "No GPS Metadata"
            }
            meta
        }

        AlertDialog(
            onDismissRequest = { showExifDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = accentWarm, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EXIF METADATA INFO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = accentWarm,
                        letterSpacing = 1.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    exifDetails.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(borderColor.copy(alpha = 0.2f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = key,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary
                            )
                            Text(
                                text = value,
                                fontSize = 11.sp,
                                color = textPrimary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f).padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExifDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = accentWarm, contentColor = accentDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = panelBackground
        )
    }

    if (showAdvancedExportDialog) {
        AlertDialog(
            onDismissRequest = { showAdvancedExportDialog = false },
            title = {
                Text(
                    text = "ADVANCED EXPORT CONFIG",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = accentWarm,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Customize sizes, borders, and panel splits for high-quality publishing.",
                        color = textSecondary,
                        fontSize = 12.sp
                    )

                    // 1. Social Media Size / Aspect Ratio
                    Text(
                        text = "SOCIAL MEDIA FORMATS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentWarm,
                        letterSpacing = 0.5.sp
                    )
                    
                    val formats = listOf(
                        "Original" to "Original camera aspect",
                        "1:1" to "Instagram Square",
                        "4:5" to "Instagram Portrait",
                        "9:16" to "TikTok / Instagram Story",
                        "16:9" to "YouTube / Widescreen",
                        "1.91:1" to "Twitter Landscape",
                        "2:3" to "Pinterest Pin"
                    )

                    formats.forEach { (aspect, desc) ->
                        val isSel = exportAspect == aspect
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) accentDark else Color(0xFF24222A)
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = if (isSel) accentWarm else borderColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    exportAspect = aspect
                                    if (aspect == "Original") {
                                        exportSplitCount = 1
                                    }
                                },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(aspect, fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 13.sp)
                                    Text(desc, color = textSecondary, fontSize = 11.sp)
                                }
                                RadioButton(
                                    selected = isSel,
                                    onClick = {
                                        exportAspect = aspect
                                        if (aspect == "Original") {
                                            exportSplitCount = 1
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentWarm)
                                )
                            }
                        }
                    }

                    // 2. Fitting Mode
                    if (exportAspect != "Original") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "IMAGE FITTING METHOD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentWarm,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val fitModes = listOf("Crop to Fit", "Fit with Letterbox")
                            fitModes.forEach { mode ->
                                val isSel = exportFitMode == mode
                                Button(
                                    onClick = { exportFitMode = mode },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSel) accentWarm else borderColor,
                                        contentColor = if (isSel) Color.Black else textPrimary
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(mode, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 3. Border Options
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "BORDER STYLES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentWarm,
                        letterSpacing = 0.5.sp
                    )

                    val borders = listOf(
                        "None" to "No borders",
                        "Classic White" to "Crisp white minimalist border",
                        "Gallery Black" to "High-contrast dark shadow",
                        "Neon Glow" to "Vibrant multi-color synthwave border",
                        "Custom Color" to "Choose custom background hue"
                    )

                    borders.forEach { (style, desc) ->
                        val isSel = exportBorderStyle == style
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) accentDark else Color(0xFF24222A)
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = if (isSel) accentWarm else borderColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { exportBorderStyle = style },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(style, fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 13.sp)
                                    Text(desc, color = textSecondary, fontSize = 11.sp)
                                }
                                RadioButton(
                                    selected = isSel,
                                    onClick = { exportBorderStyle = style },
                                    colors = RadioButtonDefaults.colors(selectedColor = accentWarm)
                                )
                            }
                        }
                    }

                    // Custom Color Preset Picker
                    if (exportBorderStyle == "Custom Color") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CUSTOM BORDER PRESETS",
                            fontSize = 10.sp,
                            color = textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val colors = listOf(
                                "Peach" to android.graphics.Color.parseColor("#FFB7B2"),
                                "Golden" to android.graphics.Color.parseColor("#FFD166"),
                                "Mint" to android.graphics.Color.parseColor("#06D6A0"),
                                "Royal" to android.graphics.Color.parseColor("#7B2CBF"),
                                "Deep Coral" to android.graphics.Color.parseColor("#F15BB5")
                            )
                            colors.forEach { (label, intVal) ->
                                val isSel = exportCustomBorderColor == intVal
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(intVal))
                                        .border(
                                            width = if (isSel) 3.dp else 1.dp,
                                            color = if (isSel) accentWarm else Color.Transparent
                                        )
                                        .clickable { exportCustomBorderColor = intVal },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSel) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (label == "Peach" || label == "Golden" || label == "Mint") Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Border Thickness
                    if (exportBorderStyle != "None") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "BORDER THICKNESS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentWarm,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val thicknesses = listOf(
                                "Thin" to 0.02f,
                                "Medium" to 0.04f,
                                "Thick" to 0.08f,
                                "X-Thick" to 0.12f
                            )
                            thicknesses.forEach { (label, pct) ->
                                val isSel = exportBorderThickness == pct
                                Button(
                                    onClick = { exportBorderThickness = pct },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSel) accentWarm else borderColor,
                                        contentColor = if (isSel) Color.Black else textPrimary
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 4. Panel Splitting
                    if (exportAspect != "Original") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C24)),
                            border = BorderStroke(1.dp, accentWarm.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "PANEL SPLITTING (MULTI-POST)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentWarm,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Automatically splits a widescreen format into multiple posts for seamless swipe feeds.",
                                    color = textSecondary,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val splits = listOf(
                                        "1 Post" to 1,
                                        "2 Panels" to 2,
                                        "3 Panels" to 3
                                    )
                                    splits.forEach { (label, count) ->
                                        val isSel = exportSplitCount == count
                                        Button(
                                            onClick = { exportSplitCount = count },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSel) accentWarm else borderColor,
                                                contentColor = if (isSel) Color.Black else textPrimary
                                            ),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (exportSplitCount > 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(accentDark.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = when (exportSplitCount) {
                                                2 -> "💡 Splitting will crop/fit the original into a widescreen ratio, then save 2 consecutive $exportAspect posts to create a seamless swipeable photo!"
                                                else -> "💡 Splitting will crop/fit the original into a panoramic ratio, then save 3 consecutive $exportAspect posts to create a seamless swipeable photo!"
                                            },
                                            color = accentWarm,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showAdvancedExportDialog = false
                            viewModel.exportAdvancedPhoto(
                                context = context,
                                aspectName = exportAspect,
                                fitMode = exportFitMode,
                                borderStyle = exportBorderStyle,
                                customBorderColor = exportCustomBorderColor,
                                borderThicknessPct = exportBorderThickness,
                                splitCount = exportSplitCount,
                                isShare = true
                            )
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = accentWarm)
                    ) {
                        Text("Share", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showAdvancedExportDialog = false
                            viewModel.exportAdvancedPhoto(
                                context = context,
                                aspectName = exportAspect,
                                fitMode = exportFitMode,
                                borderStyle = exportBorderStyle,
                                customBorderColor = exportCustomBorderColor,
                                borderThicknessPct = exportBorderThickness,
                                splitCount = exportSplitCount,
                                isShare = false
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentWarm, contentColor = Color.Black)
                    ) {
                        Text("Save to Gallery", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAdvancedExportDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = textSecondary)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = panelBackground
        )
    }

    if (publishOverwritePrompt != null) {
        val prompt = publishOverwritePrompt!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissPublishOverwritePrompt() },
            title = {
                Text(
                    text = "PHOTO ALREADY PUBLISHED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = accentWarm,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Text(
                    text = "One or more selected photos have already been published to the online portal. Would you like to overwrite the existing published version(s) or save as a new version?",
                    color = textPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { prompt.onChoice(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = accentWarm, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Overwrite Existing", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { prompt.onChoice(false) },
                        border = BorderStroke(1.dp, borderColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save as New Version", fontSize = 12.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { viewModel.dismissPublishOverwritePrompt() }
                    ) {
                        Text("Cancel", fontSize = 12.sp, color = textSecondary)
                    }
                }
            },
            containerColor = panelBackground
        )
    }
}

@Composable
fun ClientDropdownContent(
    allRegistrations: List<LocalRegistration>,
    activeConfig: LocalConfig?,
    onStageReg: LocalRegistration?,
    allPhotos: List<LocalPhoto>,
    accentWarm: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    onSelectClient: (LocalRegistration?) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonOff, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Unassigned / General Shoot", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        onClick = {
            onSelectClient(null)
            onDismiss()
        }
    )
    HorizontalDivider(color = borderColor)

    if (allRegistrations.isEmpty()) {
        DropdownMenuItem(
            text = { Text("No registered clients in Queue.", color = textSecondary, fontSize = 12.sp) },
            onClick = { onDismiss() }
        )
    } else {
        val activeClients = remember(allRegistrations, activeConfig) {
            allRegistrations.filter { activeConfig != null && it.eventId == activeConfig.id }
        }
        val otherClients = remember(allRegistrations, activeConfig) {
            allRegistrations.filter { activeConfig == null || it.eventId != activeConfig.id }
        }

        if (activeClients.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentWarm.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ACTIVE EVENT QUEUE (${activeConfig?.eventName?.uppercase() ?: "CURRENT"})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentWarm,
                    letterSpacing = 0.5.sp
                )
            }
            activeClients.forEach { client ->
                val clientPhotos = remember(allPhotos, client) {
                    allPhotos.filter {
                        it.assignedUserId == client.id || (client.photoFiles.isNotEmpty() && client.photoFiles.contains(it.fileName))
                    }
                }
                val totalPhotos = clientPhotos.size
                val publishedPhotos = clientPhotos.count { it.isPublished }
                val isSelected = client.id == onStageReg?.id

                ClientDropdownRowItem(
                    client = client,
                    isSelected = isSelected,
                    totalPhotos = totalPhotos,
                    publishedPhotos = publishedPhotos,
                    accentWarm = accentWarm,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onSelect = {
                        onSelectClient(client)
                        onDismiss()
                    }
                )
            }
        }

        if (otherClients.isNotEmpty()) {
            if (activeClients.isNotEmpty()) {
                HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A2930))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "OTHER EVENT CLIENTS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary,
                    letterSpacing = 0.5.sp
                )
            }
            otherClients.forEach { client ->
                val clientPhotos = remember(allPhotos, client) {
                    allPhotos.filter {
                        it.assignedUserId == client.id || (client.photoFiles.isNotEmpty() && client.photoFiles.contains(it.fileName))
                    }
                }
                val totalPhotos = clientPhotos.size
                val publishedPhotos = clientPhotos.count { it.isPublished }
                val isSelected = client.id == onStageReg?.id

                ClientDropdownRowItem(
                    client = client,
                    isSelected = isSelected,
                    totalPhotos = totalPhotos,
                    publishedPhotos = publishedPhotos,
                    accentWarm = accentWarm,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onSelect = {
                        onSelectClient(client)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ClientDropdownRowItem(
    client: LocalRegistration,
    isSelected: Boolean,
    totalPhotos: Int,
    publishedPhotos: Int,
    accentWarm: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSelect: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    when {
                        publishedPhotos > 0 -> {
                            // Green tick
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(0xFF2E7D32).copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Published",
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        totalPhotos > 0 -> {
                            // Orange dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFFFFB74D), CircleShape)
                            )
                        }
                        else -> {
                            // Red dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFFEF5350), CircleShape)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = client.name,
                            color = if (isSelected) accentWarm else textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (client.pets.isNotEmpty()) {
                            Text("Pets: ${client.pets}", color = textSecondary, fontSize = 11.sp)
                        }
                        val (statusText, statusColor) = when {
                            publishedPhotos > 0 -> "$publishedPhotos published ($totalPhotos total)" to Color(0xFF81C784)
                            totalPhotos > 0 -> "$totalPhotos taken (0 published)" to Color(0xFFFFB74D)
                            else -> "No photos taken" to Color(0xFFEF9A9A)
                        }
                        Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (client.isBookingPaid) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2E7D32).copy(alpha = 0.3f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("PAID", color = Color(0xFF81C784), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFC62828).copy(alpha = 0.3f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("UNPAID", color = Color(0xFFEF9A9A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        onClick = onSelect
    )
}

@Composable
fun EventDropdownContent(
    allConfigs: List<LocalConfig>,
    activeConfig: LocalConfig?,
    onStageReg: LocalRegistration?,
    syncError: String?,
    accentWarm: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    onSelectEvent: (LocalConfig) -> Unit,
    onCreateLocalPhotoshoot: () -> Unit,
    onBypassStandalone: () -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = accentWarm, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create Local Photoshoot", color = accentWarm, fontWeight = FontWeight.Bold)
            }
        },
        onClick = {
            onDismiss()
            onCreateLocalPhotoshoot()
        }
    )
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = textPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bypass to Standalone Mode", color = textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        onClick = {
            onDismiss()
            onBypassStandalone()
        }
    )
    HorizontalDivider(color = borderColor)

    if (syncError != null) {
        DropdownMenuItem(
            text = {
                Text(
                    text = "Sync Error: $syncError",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            onClick = { onDismiss() }
        )
        HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
    }

    if (allConfigs.isEmpty()) {
        DropdownMenuItem(
            text = { Text("No active events. Click Sync.", color = textSecondary) },
            onClick = { onDismiss() }
        )
    } else {
        val sortedConfigs = remember(allConfigs, onStageReg) {
            allConfigs.sortedWith(
                compareByDescending<LocalConfig> { config ->
                    onStageReg != null && config.id == onStageReg.eventId
                }.thenByDescending { config ->
                    config.id
                }
            )
        }

        sortedConfigs.forEach { config ->
            val isActive = activeConfig != null && config.id == activeConfig.id
            val isClientEvent = onStageReg != null && config.id == onStageReg.eventId
            val isLocal = config.id.startsWith("local") || config.id.startsWith("standalone") || config.id.startsWith("photoshoot")

            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isClientEvent) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Client Event",
                                        tint = accentWarm,
                                        modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                    )
                                }
                                val locationStr = if (config.eventLocation.isNotEmpty()) " (${config.eventLocation})" else ""
                                Text(
                                    text = "${config.eventName}$locationStr",
                                    color = if (isActive || isClientEvent) accentWarm else textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            if (config.eventDate.isNotEmpty()) {
                                Text(config.eventDate, color = textSecondary, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(8.dp))

                        val (badgeText, badgeBg, badgeFg) = when {
                            isClientEvent -> Triple("CLIENT EVENT", accentWarm.copy(alpha = 0.2f), accentWarm)
                            isActive -> Triple("ACTIVE", Color(0xFF2E7D32).copy(alpha = 0.3f), Color(0xFF81C784))
                            config.sourceCollection == "configs" -> Triple("LIVE EVENT", Color(0xFF00838F).copy(alpha = 0.3f), Color(0xFF80DEEA))
                            config.sourceCollection == "portal_events" -> Triple("PORTAL EVENT", Color(0xFF5E35B1).copy(alpha = 0.3f), Color(0xFFB39DDB))
                            config.sourceCollection == "archives" -> Triple("ARCHIVED", Color(0xFF424242).copy(alpha = 0.5f), Color(0xFFB0BEC5))
                            isLocal -> Triple("LOCAL", Color(0xFF0288D1).copy(alpha = 0.3f), Color(0xFF81D4FA))
                            else -> Triple("EVENT", Color(0xFF00838F).copy(alpha = 0.3f), Color(0xFF80DEEA))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(badgeText, color = badgeFg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                onClick = {
                    onSelectEvent(config)
                    onDismiss()
                }
            )
        }
    }
}
