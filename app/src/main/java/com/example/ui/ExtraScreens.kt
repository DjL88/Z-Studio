package com.example.ui

import android.net.Uri
import android.content.Intent
import android.content.Context
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.library.data.LocalConfig
import com.example.library.data.LocalPhoto
import com.example.library.data.LocalRegistration
import com.example.library.data.LocalSettings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import com.example.viewmodel.CameraConnectionState
import com.example.viewmodel.PhotoshootViewModel

@Composable
fun ClientQueueScreen(
    registrations: List<LocalRegistration>,
    onStageReg: LocalRegistration?,
    allPhotos: List<LocalPhoto>,
    viewModel: PhotoshootViewModel,
    panelBackground: Color,
    borderColor: Color,
    accentWarm: Color,
    accentDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit,
    selectedPhoto: LocalPhoto?,
    isPortrait: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = panelBackground,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CLIENT QUEUE & TETHER DISPATCH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentWarm,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Manage Active Clients & Session Assignments",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF24222A), CircleShape)
                            .border(1.dp, borderColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search & Select Client",
                            tint = accentWarm,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = accentWarm),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Client",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Client", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isPortrait) {
                // Landscape Split Layout
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Active Client (On Stage Now) & Cycle Switcher (40%)
                    Column(
                        modifier = Modifier.weight(0.4f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OnStageNowCard(
                            onStageReg = onStageReg,
                            accentWarm = accentWarm,
                            accentDark = accentDark,
                            borderColor = borderColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            viewModel = viewModel
                        )

                        OnStageSwitcherCard(
                            registrations = registrations,
                            onStage = onStageReg,
                            accentWarm = accentWarm,
                            accentDark = accentDark,
                            borderColor = borderColor,
                            textSecondary = textSecondary,
                            viewModel = viewModel
                        )
                    }

                    // Right Column: Client Queue List (60%)
                    Box(modifier = Modifier.weight(0.6f)) {
                        ClientList(
                            registrations = registrations,
                            onStageReg = onStageReg,
                            allPhotos = allPhotos,
                            selectedPhoto = selectedPhoto,
                            accentWarm = accentWarm,
                            accentDark = accentDark,
                            borderColor = borderColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                // Portrait Stacked Layout
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OnStageNowCard(
                        onStageReg = onStageReg,
                        accentWarm = accentWarm,
                        accentDark = accentDark,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        viewModel = viewModel
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        ClientList(
                            registrations = registrations,
                            onStageReg = onStageReg,
                            allPhotos = allPhotos,
                            selectedPhoto = selectedPhoto,
                            accentWarm = accentWarm,
                            accentDark = accentDark,
                            borderColor = borderColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            viewModel = viewModel
                        )
                    }

                    OnStageSwitcherCard(
                        registrations = registrations,
                        onStage = onStageReg,
                        accentWarm = accentWarm,
                        accentDark = accentDark,
                        borderColor = borderColor,
                        textSecondary = textSecondary,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun OnStageNowCard(
    onStageReg: LocalRegistration?,
    accentWarm: Color,
    accentDark: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    viewModel: PhotoshootViewModel
) {
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
                            text = "ID: ${onStageReg.id.take(8)}",
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
                            text = if (onStageReg.pets.isNotEmpty()) onStageReg.pets else "Paws in the Park",
                            color = textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (onStageReg.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Note: ${onStageReg.notes}",
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
                    Column {
                        Text(
                            text = "Frames: ${onStageReg.photoFiles.ifEmpty { "Pending capture" }}",
                            fontSize = 12.sp,
                            color = accentWarm,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Portraits: ${onStageReg.photosUploaded}",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    }

                    val context = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { viewModel.sendClientEmailLink(context, onStageReg) },
                            modifier = Modifier
                                .size(34.dp)
                                .background(borderColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email Portal Link",
                                tint = accentWarm,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        var showPaymentModal by remember { mutableStateOf(false) }
                        val activeConf by viewModel.activeConfig.collectAsStateWithLifecycle()
                        val feeAmount = activeConf?.priceOnDay ?: 15.00

                        if (onStageReg.isBookingPaid) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF1B4D2E))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "PAID (£${String.format("%.0f", if (onStageReg.paymentAmount > 0) onStageReg.paymentAmount else feeAmount)})",
                                    color = Color(0xFF81C784),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = { showPaymentModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = accentWarm),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "PAY £${String.format("%.0f", feeAmount)}",
                                    color = Color(0xFF121212),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (showPaymentModal) {
                            PaymentDialog(
                                client = onStageReg,
                                defaultAmount = feeAmount,
                                onDismiss = { showPaymentModal = false },
                                onCollectPayment = { method, amount, ref ->
                                    viewModel.collectPayment(
                                        registrationId = onStageReg.id,
                                        paymentMethod = method,
                                        paymentAmount = amount,
                                        paymentRef = ref
                                    ) {
                                        showPaymentModal = false
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Put a client on stage from the queue list below to begin tethering and automatically assign captured photos.",
                    fontSize = 11.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun OnStageSwitcherCard(
    registrations: List<LocalRegistration>,
    onStage: LocalRegistration?,
    accentWarm: Color,
    accentDark: Color,
    borderColor: Color,
    textSecondary: Color,
    viewModel: PhotoshootViewModel
) {
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
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    .size(36.dp)
                    .background(if (hasPrev) accentDark else Color.Transparent, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Client",
                    tint = if (hasPrev) accentWarm else textSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
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
                    .size(36.dp)
                    .background(if (hasNext) accentDark else Color.Transparent, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Client",
                    tint = if (hasNext) accentWarm else textSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ClientList(
    registrations: List<LocalRegistration>,
    onStageReg: LocalRegistration?,
    allPhotos: List<LocalPhoto>,
    selectedPhoto: LocalPhoto?,
    accentWarm: Color,
    accentDark: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    viewModel: PhotoshootViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                Column(modifier = Modifier.padding(12.dp)) {
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
                        val localAssignedCount = allPhotos.count { it.assignedUserId == reg.id }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (localAssignedCount > 0) accentWarm else borderColor)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${localAssignedCount} taken",
                                color = if (localAssignedCount > 0) Color.Black else textPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (reg.photosUploaded > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF2E7D32))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${reg.photosUploaded} uploaded",
                                    color = textPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                                text = "ASSIGN CURRENT PHOTO ➜",
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


@Composable
fun TetherScreen(
    cameraState: CameraConnectionState,
    isFolderPolling: Boolean,
    tetherGpsEnabled: Boolean,
    tetherGpsLatitude: Double,
    tetherGpsLongitude: Double,
    settings: LocalSettings,
    viewModel: PhotoshootViewModel,
    directoryPickerLauncher: ActivityResultLauncher<Uri?>,
    exportDirectoryPickerLauncher: ActivityResultLauncher<Uri?>,
    imagePickerLauncher: ActivityResultLauncher<String>,
    manualImagePickerLauncher: ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest>,
    panelBackground: Color,
    borderColor: Color,
    accentWarm: Color,
    accentDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    isPortrait: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = panelBackground,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Full width / height Live View Gallery with SD Card live view & Full Screen
            LiveViewSwipeGallery(
                cameraState = cameraState,
                viewModel = viewModel,
                accentWarm = accentWarm,
                accentDark = accentDark,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                borderColor = borderColor,
                directoryPickerLauncher = directoryPickerLauncher,
                manualImagePickerLauncher = manualImagePickerLauncher
            )
        }
    }
}

@Composable
fun RotatablePhotoView(
    fileUri: String,
    rotationDegrees: Float,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    isFillScreen: Boolean = false
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val w = maxWidth
        val h = maxHeight
        val normRotation = ((rotationDegrees % 360f) + 360f) % 360f
        val isRotated90or270 = (normRotation in 45f..135f) || (normRotation in 225f..315f)

        val actualContentScale = if (isFillScreen) ContentScale.Crop else contentScale

        if (isRotated90or270) {
            AsyncImage(
                model = fileUri,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(width = h, height = w)
                    .graphicsLayer {
                        rotationZ = normRotation
                    },
                contentScale = actualContentScale
            )
        } else {
            AsyncImage(
                model = fileUri,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = normRotation
                    },
                contentScale = actualContentScale
            )
        }
    }
}

@Composable
fun LiveViewSwipeGallery(
    cameraState: CameraConnectionState,
    viewModel: PhotoshootViewModel,
    accentWarm: Color,
    accentDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    directoryPickerLauncher: ActivityResultLauncher<Uri?>? = null,
    manualImagePickerLauncher: ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest>? = null
) {
    val allPhotos by viewModel.allPhotos.collectAsStateWithLifecycle()
    val onStageClient by viewModel.onStageRegistration.collectAsStateWithLifecycle()
    val activeConfig by viewModel.activeConfig.collectAsStateWithLifecycle()
    val isPublishing by viewModel.isPublishing.collectAsStateWithLifecycle()

    var isFullScreen by remember { mutableStateOf(false) }

    val clientPhotos = remember(allPhotos, onStageClient, activeConfig) {
        if (onStageClient != null) {
            val clientReg = onStageClient!!
            allPhotos.filter {
                it.assignedUserId == clientReg.id ||
                (clientReg.photoFiles.isNotEmpty() && clientReg.photoFiles.contains(it.fileName))
            }
        } else {
            val currentEventId = activeConfig?.id
            if (currentEventId != null) {
                allPhotos.filter { it.projectId == currentEventId || it.projectId == null }
            } else {
                allPhotos
            }
        }
    }

    val sortedPhotos = remember(clientPhotos) { clientPhotos.sortedByDescending { it.timestamp } }
    var activeIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(sortedPhotos.size) {
        if (sortedPhotos.isNotEmpty()) {
            activeIndex = 0
        }
    }

    val currentIndex = activeIndex.coerceIn(0, (sortedPhotos.size - 1).coerceAtLeast(0))
    val currentPhoto = sortedPhotos.getOrNull(currentIndex)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1D22)),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentDark)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (onStageClient != null) "ON-STAGE LIVE" else "LIVE VIEW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentWarm
                            )
                        }
                        Text(
                            text = onStageClient?.name ?: "All Session Captures",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (onStageClient != null) {
                        Text(
                            text = "Mapped Client Photos (${sortedPhotos.size} files)",
                            fontSize = 9.sp,
                            color = textSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sortedPhotos.isNotEmpty()) {
                        Text(
                            text = "${currentIndex + 1} / ${sortedPhotos.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentWarm
                        )
                    }

                    // Rotate 90° Button
                    IconButton(
                        onClick = { if (currentPhoto != null) viewModel.rotatePhoto90(currentPhoto) },
                        enabled = currentPhoto != null,
                        modifier = Modifier
                            .size(32.dp)
                            .background(borderColor, RoundedCornerShape(6.dp))
                            .testTag("tether_rotate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate 90°",
                            tint = if (currentPhoto != null) accentWarm else textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Full Screen Toggle Button
                    IconButton(
                        onClick = { isFullScreen = true },
                        enabled = currentPhoto != null,
                        modifier = Modifier
                            .size(32.dp)
                            .background(borderColor, RoundedCornerShape(6.dp))
                            .testTag("tether_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expand to Full Screen",
                            tint = if (currentPhoto != null) accentWarm else textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Photo Preview & Swipe Canvas Area (Maximum Space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (currentPhoto != null) {
                    val isRaw = remember(currentPhoto.fileName) {
                        val ext = currentPhoto.fileName.lowercase()
                        ext.endsWith(".nef") || ext.endsWith(".raw") || ext.endsWith(".cr2") || ext.endsWith(".cr3") || ext.endsWith(".arw") || ext.endsWith(".dng") || ext.endsWith(".raf")
                    }

                    RotatablePhotoView(
                        fileUri = currentPhoto.fileUri,
                        rotationDegrees = currentPhoto.straighten,
                        contentDescription = "Live View Capture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        isFillScreen = false
                    )

                    // Format Badge Overlay (Top Right)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRaw) Color(0xFFC48D62) else Color(0xFF0288D1))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isRaw) "RAW" else "JPG",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Watermark Overlay on Unpaid Session
                    if (onStageClient != null && !onStageClient!!.isBookingPaid) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PROOF ONLY — UNPAID SESSION",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .background(Color.Red.copy(alpha = 0.35f))
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Swipe Navigation Overlay Arrows
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (currentIndex > 0) activeIndex-- },
                            enabled = currentIndex > 0,
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (currentIndex > 0) Color.Black.copy(alpha = 0.6f) else Color.Transparent, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Photo",
                                tint = if (currentIndex > 0) Color.White else Color.Transparent
                            )
                        }

                        IconButton(
                            onClick = { if (currentIndex < sortedPhotos.size - 1) activeIndex++ },
                            enabled = currentIndex < sortedPhotos.size - 1,
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (currentIndex < sortedPhotos.size - 1) Color.Black.copy(alpha = 0.6f) else Color.Transparent, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Photo",
                                tint = if (currentIndex < sortedPhotos.size - 1) Color.White else Color.Transparent
                            )
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = accentWarm,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (cameraState is CameraConnectionState.Connected) "LIVE VIEW READY" else "CAMERA STANDBY",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Take a photo or import from camera SD cards / USB storage",
                            color = textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Thumbnail Strip Carousel for Multiple Client Photos
            if (sortedPhotos.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(sortedPhotos) { index, photo ->
                        val isSelected = index == currentIndex
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) accentWarm else borderColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(Color.Black)
                                .clickable { activeIndex = index }
                        ) {
                            AsyncImage(
                                model = photo.fileUri,
                                contentDescription = "Thumbnail ${photo.fileName}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationZ = photo.straighten },
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(vertical = 1.dp)
                            ) {
                                Text(
                                    text = photo.fileName.takeLast(9),
                                    color = if (isSelected) accentWarm else Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        if (currentPhoto != null) {
                            viewModel.promotePhotoToLibrary(currentPhoto)
                        }
                    },
                    enabled = currentPhoto != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0), contentColor = Color.White),
                    modifier = Modifier.weight(1.1f).height(38.dp).testTag("promote_to_library_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "PROMOTE TO LIBRARY", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (currentPhoto != null) {
                            viewModel.bringPhotoToEditor(currentPhoto)
                        }
                    },
                    enabled = currentPhoto != null,
                    colors = ButtonDefaults.buttonColors(containerColor = accentWarm, contentColor = Color.Black),
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "BRING TO EDITOR", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (currentPhoto != null) {
                            viewModel.selectPhoto(currentPhoto)
                            viewModel.commitAndPublishPhoto(isHero = false, replaceExisting = true)
                        }
                    },
                    enabled = currentPhoto != null && !isPublishing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                    modifier = Modifier.weight(1.1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "PUBLISH TO CLIENT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Shutter Release Control Area
            TetherCameraControlsArea(
                cameraState = cameraState,
                viewModel = viewModel,
                accentWarm = accentWarm,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                borderColor = borderColor
            )
        }
    }

    // Fullscreen Image Review Modal
    if (isFullScreen && currentPhoto != null) {
        val initialIdx = currentIndex.coerceIn(0, maxOf(0, sortedPhotos.size - 1))
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = initialIdx
        ) {
            sortedPhotos.size
        }
        val coroutineScope = rememberCoroutineScope()
        var verticalDragAccumulator by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage in sortedPhotos.indices) {
                val photoAtPage = sortedPhotos[pagerState.currentPage]
                viewModel.selectPhoto(photoAtPage)
            }
        }

        var showCloseButton by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showCloseButton = !showCloseButton }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (verticalDragAccumulator < -80f && pagerState.currentPage < sortedPhotos.size - 1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    } else if (verticalDragAccumulator > 80f && pagerState.currentPage > 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                    verticalDragAccumulator = 0f
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    verticalDragAccumulator += dragAmount
                                }
                            )
                        }
                ) {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val photo = sortedPhotos.getOrNull(page)
                        if (photo != null) {
                            RotatablePhotoView(
                                fileUri = photo.fileUri,
                                rotationDegrees = photo.straighten + 90f,
                                contentDescription = photo.fileName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                isFillScreen = true
                            )
                        }
                    }

                    // Floating Clutter-free Close Button (appears on tap or top-right hover)
                    AnimatedVisibility(
                        visible = showCloseButton,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                    ) {
                        IconButton(
                            onClick = { isFullScreen = false },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                .testTag("fullscreen_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Full Screen",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ExportScreen(
    viewModel: PhotoshootViewModel,
    selectedPhotosForExport: Map<String, LocalPhoto>,
    onRemoveFromExport: (LocalPhoto) -> Unit,
    onClearExportList: () -> Unit,
    panelBackground: Color,
    borderColor: Color,
    accentWarm: Color,
    accentDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    isPortrait: Boolean
) {
    val context = LocalContext.current
    var selectedFrameStyle by remember { mutableStateOf("None") }
    var selectedAspectRatio by remember { mutableStateOf("Original") }
    var isDiptych by remember { mutableStateOf(false) }
    var isTriptych by remember { mutableStateOf(false) }

    val isPublishing by viewModel.isPublishing.collectAsStateWithLifecycle()
    val publishStatus by viewModel.publishStatus.collectAsStateWithLifecycle()
    val onStageClient by viewModel.onStageRegistration.collectAsStateWithLifecycle()

    val onNotifyClientLambda = {
        val reg = onStageClient
        val email = reg?.email?.ifEmpty { "customer@example.com" } ?: "customer@example.com"
        val name = reg?.name ?: "Valued Client"
        val pin = reg?.portalPin?.ifEmpty { if ((reg.id).length >= 4) reg.id.takeLast(4).uppercase() else "4829" } ?: "4829"
        val portalUrl = "https://pawtraits-portal.web.app"
        val accessKey16 = "${pin}-9281-4820-1109"
        val photoCount = selectedPhotosForExport.size

        val subject = "Your Photoshoot Gallery is Ready! - $name"
        val body = """
            Hello $name,

            Your photoshoot images ($photoCount photo(s)) have been published to your online portal!

            Client Portal URL: $portalUrl
            Username / Email: $email
            4-Digit Secret PIN: $pin
            16-Digit Direct Access Key: $accessKey16

            Thank you!
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Send Email via Gmail"))
        } catch (e: Exception) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Client Credentials", body)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Portal link & credentials copied to clipboard!", Toast.LENGTH_LONG).show()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = panelBackground,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EXPORT & FRAME CONSOLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentWarm,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Publish Multi-Selected Images with Professional Borders",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                if (selectedPhotosForExport.isNotEmpty()) {
                    TextButton(
                        onClick = onClearExportList,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350))
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Queue", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedPhotosForExport.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
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
                                imageVector = Icons.Default.BurstMode,
                                contentDescription = null,
                                tint = accentWarm,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "NO PHOTOS SELECTED FOR EXPORT",
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 16.sp,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Go to the Library tab, multi-select images by checking the checkboxes on thumbnails, then trigger batch export to apply customizable frame boundaries.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 340.dp)
                        )
                    }
                }
            } else {
                if (!isPortrait) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Section: Selected Thumbnails grid (50% width)
                        Column(
                            modifier = Modifier
                                .weight(0.5f)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = "SELECTED CAPTURES (${selectedPhotosForExport.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 90.dp),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(selectedPhotosForExport.values.toList()) { photo ->
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImage(
                                            model = photo.fileUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Remove Button overlay
                                        IconButton(
                                            onClick = { onRemoveFromExport(photo) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.Red,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Right Section: Export settings (50% width)
                        Column(
                            modifier = Modifier
                                .weight(0.5f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ExportConfigurationControls(
                                selectedFrameStyle = selectedFrameStyle,
                                onFrameStyleChange = { selectedFrameStyle = it },
                                selectedAspectRatio = selectedAspectRatio,
                                onAspectRatioChange = { selectedAspectRatio = it },
                                isDiptych = isDiptych,
                                onDiptychChange = {
                                    isDiptych = it
                                    if (it) isTriptych = false
                                },
                                isTriptych = isTriptych,
                                onTriptychChange = {
                                    isTriptych = it
                                    if (it) isDiptych = false
                                },
                                isExporting = isPublishing,
                                exportStatus = publishStatus ?: "",
                                onExportTrigger = {
                                    val split = if (isDiptych) 2 else if (isTriptych) 3 else 1
                                    viewModel.exportMultiplePhotos(
                                        context = context,
                                        photos = selectedPhotosForExport.values.toList(),
                                        aspectName = selectedAspectRatio,
                                        fitMode = "Crop to Fit",
                                        borderStyle = selectedFrameStyle,
                                        customBorderColor = android.graphics.Color.WHITE,
                                        borderThicknessPct = 0.04f,
                                        splitCount = split
                                    )
                                },
                                onPublishAndClose = {
                                    viewModel.publishAndCloseActiveClient(context, selectedPhotosForExport.values.toList())
                                },
                                onPublishOnly = {
                                    viewModel.publishActiveClientOnly(context, selectedPhotosForExport.values.toList())
                                },
                                accentWarm = accentWarm,
                                borderColor = borderColor,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                    }
                } else {
                    // Portrait stacked layout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top Section: Selected Thumbnails Row
                        Column {
                            Text(
                                text = "SELECTED CAPTURES (${selectedPhotosForExport.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedPhotosForExport.values.forEach { photo ->
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImage(
                                            model = photo.fileUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Remove Button overlay
                                        IconButton(
                                            onClick = { onRemoveFromExport(photo) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.Red,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Section: Export settings
                        ExportConfigurationControls(
                            selectedFrameStyle = selectedFrameStyle,
                            onFrameStyleChange = { selectedFrameStyle = it },
                            selectedAspectRatio = selectedAspectRatio,
                            onAspectRatioChange = { selectedAspectRatio = it },
                            isDiptych = isDiptych,
                            onDiptychChange = {
                                isDiptych = it
                                if (it) isTriptych = false
                            },
                            isTriptych = isTriptych,
                            onTriptychChange = {
                                isTriptych = it
                                if (it) isDiptych = false
                            },
                            isExporting = isPublishing,
                            exportStatus = publishStatus ?: "",
                            onExportTrigger = {
                                val split = if (isDiptych) 2 else if (isTriptych) 3 else 1
                                viewModel.exportMultiplePhotos(
                                    context = context,
                                    photos = selectedPhotosForExport.values.toList(),
                                    aspectName = selectedAspectRatio,
                                    fitMode = "Crop to Fit",
                                    borderStyle = selectedFrameStyle,
                                    customBorderColor = android.graphics.Color.WHITE,
                                    borderThicknessPct = 0.04f,
                                    splitCount = split
                                )
                            },
                            onPublishAndClose = {
                                viewModel.publishAndCloseActiveClient(context, selectedPhotosForExport.values.toList())
                            },
                            onPublishOnly = {
                                viewModel.publishActiveClientOnly(context, selectedPhotosForExport.values.toList())
                            },
                            onNotifyClient = onNotifyClientLambda,
                            accentWarm = accentWarm,
                            borderColor = borderColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TetherCameraControlsArea(
    cameraState: CameraConnectionState,
    viewModel: PhotoshootViewModel,
    accentWarm: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color
) {
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
}

@Composable
fun TetherSettingsCards(
    isFolderPolling: Boolean,
    tetherGpsEnabled: Boolean,
    tetherGpsLatitude: Double,
    tetherGpsLongitude: Double,
    settings: LocalSettings,
    viewModel: PhotoshootViewModel,
    directoryPickerLauncher: ActivityResultLauncher<Uri?>,
    exportDirectoryPickerLauncher: ActivityResultLauncher<Uri?>,
    imagePickerLauncher: ActivityResultLauncher<String>,
    manualImagePickerLauncher: ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest>,
    borderColor: Color,
    accentWarm: Color,
    accentDark: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val haptic = LocalHapticFeedback.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        text = "Active: ${Uri.decode(settings.exportDirectoryUri).split("/").lastOrNull() ?: "Bound"}",
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
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }
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
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
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

@Composable
fun ExportConfigurationControls(
    selectedFrameStyle: String,
    onFrameStyleChange: (String) -> Unit,
    selectedAspectRatio: String,
    onAspectRatioChange: (String) -> Unit,
    isDiptych: Boolean,
    onDiptychChange: (Boolean) -> Unit,
    isTriptych: Boolean,
    onTriptychChange: (Boolean) -> Unit,
    isExporting: Boolean,
    exportStatus: String,
    onExportTrigger: () -> Unit,
    onPublishAndClose: () -> Unit = {},
    onPublishOnly: () -> Unit = {},
    onNotifyClient: () -> Unit = {},
    accentWarm: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Aspect Ratio Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "ASPECT RATIO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val aspects = listOf("Original", "1:1", "4:5", "9:16", "16:9", "1.91:1", "2:3")
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    modifier = Modifier.height(90.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(aspects) { aspect ->
                        val isSel = selectedAspectRatio == aspect
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) accentWarm else Color(0xFF1E1D22))
                                .border(1.dp, if (isSel) accentWarm else borderColor, RoundedCornerShape(6.dp))
                                .clickable { onAspectRatioChange(aspect) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = aspect,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.Black else textPrimary
                            )
                        }
                    }
                }
            }
        }

        // 2. Frame / Border Style Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "FRAME BORDER STYLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val styles = listOf("None", "Classic White", "Gallery Black", "Neon Glow", "Custom Color")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    styles.forEach { style ->
                        val isSel = selectedFrameStyle == style
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) accentWarm else Color(0xFF1E1D22))
                                .border(1.dp, if (isSel) accentWarm else borderColor, RoundedCornerShape(6.dp))
                                .clickable { onFrameStyleChange(style) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = style,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.Black else textPrimary
                            )
                        }
                    }
                }
            }
        }

        // 3. Multi-Post Split Layout Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF24222A)),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "PANORAMA SPLIT MODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Split wider photos into consecutive sliding posts",
                    fontSize = 9.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Single Post
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isDiptych && !isTriptych) accentWarm else Color(0xFF1E1D22))
                            .border(1.dp, if (!isDiptych && !isTriptych) accentWarm else borderColor, RoundedCornerShape(6.dp))
                            .clickable {
                                onDiptychChange(false)
                                onTriptychChange(false)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Standard (1x)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isDiptych && !isTriptych) Color.Black else textPrimary
                        )
                    }

                    // Diptych (2x)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDiptych) accentWarm else Color(0xFF1E1D22))
                            .border(1.dp, if (isDiptych) accentWarm else borderColor, RoundedCornerShape(6.dp))
                            .clickable {
                                onDiptychChange(true)
                                onTriptychChange(false)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Diptych (2x)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDiptych) Color.Black else textPrimary
                        )
                    }

                    // Triptych (3x)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isTriptych) accentWarm else Color(0xFF1E1D22))
                            .border(1.dp, if (isTriptych) accentWarm else borderColor, RoundedCornerShape(6.dp))
                            .clickable {
                                onDiptychChange(false)
                                onTriptychChange(true)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Triptych (3x)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTriptych) Color.Black else textPrimary
                        )
                    }
                }

                if (isDiptych || isTriptych) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isDiptych) 
                            "💡 Splitting will crop/fit wider images into a diptych ratio, saving 2 consecutive seamless posts to create an interactive swipeable photo!"
                            else 
                            "💡 Splitting will crop/fit wider images into a triptych ratio, saving 3 consecutive seamless posts to create an interactive swipeable photo!",
                        fontSize = 10.sp,
                        color = accentWarm,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // 4. Status and Action Area
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1D22)),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "PUBLISH & DELIVERY OPTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (exportStatus.isNotEmpty()) {
                    Text(
                        text = exportStatus,
                        color = accentWarm,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Option 1: Publish and Close Client
                Button(
                    onClick = onPublishAndClose,
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentWarm,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("publish_and_close_client_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("PUBLISH AND CLOSE CLIENT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Uploads photos, emails PIN credential, and closes session", fontSize = 9.sp, color = Color.Black.copy(alpha = 0.8f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Publish Only
                OutlinedButton(
                    onClick = onPublishOnly,
                    enabled = !isExporting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textPrimary
                    ),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("publish_only_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = accentWarm, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("PUBLISH ONLY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        Text("Uploads photos to portal & updates PIN profile without emailing or closing session", fontSize = 9.sp, color = textSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 3: Download / Share Images
                OutlinedButton(
                    onClick = onExportTrigger,
                    enabled = !isExporting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textPrimary
                    ),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_share_images_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = accentWarm, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("DOWNLOAD / SHARE IMAGES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        Text("Renders high-res JPEGs locally and opens system share sheet (Instagram, WhatsApp, etc)", fontSize = 9.sp, color = textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentDialog(
    client: LocalRegistration,
    defaultAmount: Double,
    onDismiss: () -> Unit,
    onCollectPayment: (method: String, amount: Double, ref: String) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("sumup_terminal") }
    var amountText by remember { mutableStateOf(String.format("%.2f", defaultAmount)) }
    var refText by remember { mutableStateOf("POS-${System.currentTimeMillis() % 10000}") }
    var isProcessing by remember { mutableStateOf(false) }

    val accentWarm = Color(0xFFC48D62)
    val darkCard = Color(0xFF1E1D22)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = darkCard,
        title = {
            Column {
                Text(
                    text = "COLLECT PAYMENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentWarm,
                    letterSpacing = 1.sp
                )
                Text(
                    text = client.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Payment Method Selector
                Text("Select Method:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val methods = listOf(
                        "sumup_terminal" to "SumUp",
                        "card" to "Card",
                        "cash" to "Cash",
                        "bacs" to "BACS"
                    )
                    methods.forEach { (key, label) ->
                        val isSelected = selectedMethod == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accentWarm else Color(0xFF2A2830))
                                .border(1.dp, if (isSelected) accentWarm else Color.DarkGray, RoundedCornerShape(8.dp))
                                .clickable { selectedMethod = key }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }

                // Amount Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (£)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentWarm,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Ref Field
                OutlinedTextField(
                    value = refText,
                    onValueChange = { refText = it },
                    label = { Text("Reference / Receipt No", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentWarm,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isProcessing = true
                    val amount = amountText.toDoubleOrNull() ?: defaultAmount
                    onCollectPayment(selectedMethod, amount, refText)
                },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = accentWarm, contentColor = Color.Black)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text("CONFIRM PAYMENT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
