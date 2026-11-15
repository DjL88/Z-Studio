package com.example.library.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.library.data.LocalConfig
import com.example.library.data.LocalPhoto
import com.example.library.data.LocalProject
import com.example.library.data.LocalRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModelDelegate,
    allPhotos: List<LocalPhoto>,
    allProjects: List<LocalProject>,
    allRegistrations: List<LocalRegistration> = emptyList(),
    activeConfig: LocalConfig? = null,
    onStageRegistration: LocalRegistration? = null,
    selectedProjectId: String?,
    studioBackground: Color,
    panelBackground: Color,
    accentWarm: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentDark: Color,
    borderColor: Color,
    selectedPhotosForExport: Map<String, LocalPhoto> = emptyMap(),
    onToggleSelectForExport: (LocalPhoto) -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateToEdit: () -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var projectDesc by remember { mutableStateOf("") }

    val libraryPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importManualImage(uri)
            }
        }
    )

    val activeProject = allProjects.find { it.id == selectedProjectId }
    var selectedLibraryTab by remember { androidx.compose.runtime.mutableIntStateOf(0) } // 0 = Ported Local Drafts, 1 = Published to Firebase Storage

    LaunchedEffect(selectedLibraryTab, activeConfig?.id, onStageRegistration?.id) {
        if (selectedLibraryTab == 1 && activeConfig != null && onStageRegistration != null) {
            viewModel.syncPublishedPhotos(activeConfig.id, onStageRegistration.id)
        }
    }

    val basePhotos = remember(allPhotos, selectedProjectId) {
        if (selectedProjectId == null) {
            allPhotos
        } else {
            allPhotos.filter { it.projectId == selectedProjectId || it.assignedUserId == selectedProjectId }
        }
    }

    val filteredDraftPhotos = remember(basePhotos) {
        basePhotos.filter { !it.isPublished }
    }

    val filteredPublishedPhotos = remember(allPhotos, activeConfig, onStageRegistration) {
        if (activeConfig == null || onStageRegistration == null) {
            emptyList()
        } else {
            allPhotos.filter { isPhotoMatchingEventAndClient(it, activeConfig, onStageRegistration) }
        }
    }

    val displayPhotos = remember(selectedLibraryTab, filteredDraftPhotos, filteredPublishedPhotos, basePhotos) {
        when (selectedLibraryTab) {
            0 -> filteredDraftPhotos
            1 -> filteredPublishedPhotos
            else -> basePhotos
        }
    }

    val draftsCount = filteredDraftPhotos.size
    val publishedCount = filteredPublishedPhotos.size

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    var isAlbumsExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
    if (isPortrait) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(studioBackground)
        ) {
            // --- TOP PANEL: PROJECTS LIST IN PORTRAIT ---
            if (!isAlbumsExpanded) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { isAlbumsExpanded = true },
                    color = panelBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (selectedProjectId == null) Icons.Default.Collections else Icons.Default.Folder,
                                contentDescription = null,
                                tint = accentWarm,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ALBUM FILTER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (selectedProjectId == null) "All Photos / General" else activeProject?.name ?: "Unknown Project",
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        Button(
                            onClick = { isAlbumsExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentDark),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.ExpandMore, contentDescription = "Show Albums", tint = textPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Show Albums", fontSize = 11.sp, color = textPrimary)
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
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
                            Text(
                                text = "PROJECTS / ALBUMS",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentWarm,
                                letterSpacing = 1.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        projectName = ""
                                        projectDesc = ""
                                        showCreateDialog = true
                                    },
                                    modifier = Modifier.testTag("create_project_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Create Project", tint = accentWarm)
                                }
                                IconButton(
                                    onClick = { isAlbumsExpanded = false }
                                ) {
                                    Icon(Icons.Default.ExpandLess, contentDescription = "Collapse Albums", tint = textSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                val isAllSelected = selectedProjectId == null
                                Card(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .fillMaxHeight()
                                        .clickable { 
                                            viewModel.selectProject(null)
                                            isAlbumsExpanded = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAllSelected) accentDark else Color(0xFF232127)
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isAllSelected) accentWarm else borderColor
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Collections,
                                            contentDescription = null,
                                            tint = if (isAllSelected) accentWarm else textSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "All Photos",
                                                fontWeight = FontWeight.Bold,
                                                color = textPrimary,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${allPhotos.size} shots",
                                                color = textSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            items(allProjects) { project ->
                                val isSelected = selectedProjectId == project.id
                                val projectPhotoCount = allPhotos.count { it.projectId == project.id }
                                val formattedDate = remember(project.createdAt) {
                                    try {
                                        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(project.createdAt))
                                    } catch (e: Exception) {
                                        ""
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .fillMaxHeight()
                                        .clickable { 
                                            viewModel.selectProject(project.id)
                                            isAlbumsExpanded = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) accentDark else Color(0xFF232127)
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) accentWarm else borderColor
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp).fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = if (isSelected) accentWarm else textSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = project.name,
                                                fontWeight = FontWeight.Bold,
                                                color = textPrimary,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            IconButton(
                                                onClick = { viewModel.deleteProject(project) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Project",
                                                    tint = Color(0xFFEF5350),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        if (project.description.isNotEmpty()) {
                                            Text(
                                                text = project.description,
                                                color = textSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formattedDate,
                                                color = textSecondary,
                                                fontSize = 9.sp
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(borderColor, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$projectPhotoCount shots",
                                                    color = textPrimary,
                                                    fontSize = 9.sp,
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
            }

            // --- MAIN PHOTOS GRID IN PORTRAIT ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (isAlbumsExpanded) 0.58f else 1.0f)
                    .padding(8.dp),
                color = panelBackground,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (activeProject == null) "GENERAL LIBRARY" else "SESSION: ${activeProject.name.uppercase()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentWarm,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activeProject == null) {
                                    "All photos"
                                } else {
                                    activeProject.description.ifEmpty { "Offline local session" }
                                },
                                color = textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        libraryPhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentWarm),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp).testTag("library_import_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                     Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                if (selectedProjectId != null) {
                                    Button(
                                        onClick = { viewModel.selectProject(null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentDark),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Clear", fontSize = 11.sp, color = textPrimary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2-Section Library Tab Selector (Ported Drafts vs Published to Firebase)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF141318))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { selectedLibraryTab = 0 },
                                modifier = Modifier.weight(1f),
                                color = if (selectedLibraryTab == 0) accentWarm else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = if (selectedLibraryTab == 0) Color.Black else textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PORTED DRAFTS ($draftsCount)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedLibraryTab == 0) Color.Black else textSecondary
                                    )
                                }
                            }

                            Surface(
                                onClick = { selectedLibraryTab = 1 },
                                modifier = Modifier.weight(1f),
                                color = if (selectedLibraryTab == 1) Color(0xFF2E7D32) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = if (selectedLibraryTab == 1) Color.White else textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PUBLISHED ($publishedCount)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedLibraryTab == 1) Color.White else textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (displayPhotos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedLibraryTab == 1 && (activeConfig == null || onStageRegistration == null)) Icons.Default.CloudQueue else Icons.Default.Collections,
                                    contentDescription = null,
                                    tint = if (selectedLibraryTab == 1 && (activeConfig == null || onStageRegistration == null)) accentWarm else borderColor,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (selectedLibraryTab == 1 && (activeConfig == null || onStageRegistration == null)) "Select Event & On-Stage Client" else "No Photos In This Session",
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (selectedLibraryTab == 1 && (activeConfig == null || onStageRegistration == null))
                                        "Published gallery photos are filtered by event and client. Please select both a Shoot Event and an On-Stage Client from the top bar dropdowns to load published photos."
                                    else
                                        "To add photos to this project, capture tethered images, use Folder Import, or click 'Assign to...' on photos in the General library.",
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(max = 380.dp)
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayPhotos, key = { it.id }) { photo ->
                                PhotoLibraryGridItem(
                                    photo = photo,
                                    allProjects = allProjects,
                                    allRegistrations = allRegistrations,
                                    onSelect = { viewModel.selectPhoto(photo) },
                                    onAssignToProject = { projId ->
                                        viewModel.assignPhotoToProject(photo.id, projId)
                                    },
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    borderColor = borderColor,
                                    accentWarm = accentWarm,
                                    accentDark = accentDark,
                                    isSelectedForExport = selectedPhotosForExport.containsKey(photo.id),
                                    onToggleSelectForExport = { onToggleSelectForExport(photo) },
                                    onEdit = {
                                        viewModel.editPhotoWithLibraryCheck(photo) {
                                            onNavigateToEdit()
                                        }
                                    },
                                    onPublish = {
                                        viewModel.publishPhotos(listOf(photo))
                                    },
                                    onUnpublish = {
                                        viewModel.unpublishPhoto(photo)
                                    },
                                    onDelete = {
                                        viewModel.deletePhoto(photo)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- LANDSCAPE MODE ---
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(studioBackground)
        ) {
            // --- LEFT COLUMN: PROJECTS LIST (38% Width) ---
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.38f)
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
                        Text(
                            text = "PROJECTS / ALBUMS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentWarm,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = {
                                projectName = ""
                                projectDesc = ""
                                showCreateDialog = true
                            },
                            modifier = Modifier.testTag("create_project_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Create Project", tint = accentWarm)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // "All Photos" row
                        item {
                            val isAllSelected = selectedProjectId == null
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectProject(null) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAllSelected) accentDark else Color(0xFF232127)
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isAllSelected) accentWarm else borderColor
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Collections,
                                        contentDescription = null,
                                        tint = if (isAllSelected) accentWarm else textSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "All Photos / General",
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Showing every imported shoot",
                                            color = textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(borderColor, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${allPhotos.size}",
                                            color = textPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        items(allProjects) { project ->
                            val isSelected = selectedProjectId == project.id
                            val projectPhotoCount = allPhotos.count { it.projectId == project.id }
                            val formattedDate = remember(project.createdAt) {
                                try {
                                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(project.createdAt))
                                } catch (e: Exception) {
                                    ""
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectProject(project.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) accentDark else Color(0xFF232127)
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) accentWarm else borderColor
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isSelected) accentWarm else textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = project.name,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(
                                            onClick = { viewModel.deleteProject(project) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Project",
                                                tint = Color(0xFFEF5350),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    if (project.description.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = project.description,
                                            color = textSecondary,
                                            fontSize = 11.sp,
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
                                            text = "Created $formattedDate",
                                            color = textSecondary,
                                            fontSize = 10.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(borderColor, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$projectPhotoCount shots",
                                                color = textPrimary,
                                                fontSize = 10.sp,
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

            // --- RIGHT COLUMN: ACTIVE PHOTOS GRID (67% Width) ---
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.67f)
                    .padding(8.dp),
                color = panelBackground,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header indicating current project context
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (activeProject == null) "GENERAL LIBRARY (ALL PHOTOS)" else "SESSION: ${activeProject.name.uppercase()}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentWarm,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = if (activeProject == null) {
                                    "Displaying every session. Select a project on the left to filter."
                                } else {
                                    activeProject.description.ifEmpty { "Offline local session" }
                                },
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    libraryPhotoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentWarm),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("library_import_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Photo", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            if (selectedProjectId != null) {
                                Button(
                                    onClick = { viewModel.selectProject(null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentDark),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Clear Filter", fontSize = 11.sp, color = textPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2-Section Library Tab Selector in Landscape
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF141318))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { selectedLibraryTab = 0 },
                                modifier = Modifier.weight(1f),
                                color = if (selectedLibraryTab == 0) accentWarm else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = if (selectedLibraryTab == 0) Color.Black else textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PORTED DRAFTS ($draftsCount)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedLibraryTab == 0) Color.Black else textSecondary
                                    )
                                }
                            }

                            Surface(
                                onClick = { selectedLibraryTab = 1 },
                                modifier = Modifier.weight(1f),
                                color = if (selectedLibraryTab == 1) Color(0xFF2E7D32) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = if (selectedLibraryTab == 1) Color.White else textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PUBLISHED ($publishedCount)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedLibraryTab == 1) Color.White else textSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (displayPhotos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedLibraryTab == 1 && (activeConfig == null || onStageRegistration == null)) Icons.Default.CloudQueue else Icons.Default.Collections,
                                    contentDescription = null,
                                    tint = if (selectedLibraryTab == 1 && (activeConfig == null || onStageRegistration == null)) accentWarm else borderColor,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (selectedLibraryTab == 1 && (activeConfig == null || onStageRegistration == null)) "Select Event & On-Stage Client" else "No Photos In This Session",
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (selectedLibraryTab == 1 && (activeConfig == null || onStageRegistration == null))
                                        "Published gallery photos are filtered by event and client. Please select both a Shoot Event and an On-Stage Client from the top bar dropdowns to load published photos."
                                    else
                                        "To add photos to this project, capture tethered images, use Folder Import, or click 'Assign to...' on photos in the General library.",
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(max = 380.dp)
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayPhotos, key = { it.id }) { photo ->
                                PhotoLibraryGridItem(
                                    photo = photo,
                                    allProjects = allProjects,
                                    allRegistrations = allRegistrations,
                                    onSelect = { viewModel.selectPhoto(photo) },
                                    onAssignToProject = { projId ->
                                        viewModel.assignPhotoToProject(photo.id, projId)
                                    },
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    borderColor = borderColor,
                                    accentWarm = accentWarm,
                                    accentDark = accentDark,
                                    isSelectedForExport = selectedPhotosForExport.containsKey(photo.id),
                                    onToggleSelectForExport = { onToggleSelectForExport(photo) },
                                    onEdit = {
                                        viewModel.editPhotoWithLibraryCheck(photo) {
                                            onNavigateToEdit()
                                        }
                                    },
                                    onPublish = {
                                        viewModel.publishPhotos(listOf(photo))
                                    },
                                    onUnpublish = {
                                        viewModel.unpublishPhoto(photo)
                                    },
                                    onDelete = {
                                        viewModel.deletePhoto(photo)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE PROJECT DIALOG
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text("New Shoot Project", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Project Name") },
                        modifier = Modifier.fillMaxWidth().testTag("project_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = accentWarm,
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
                        value = projectDesc,
                        onValueChange = { projectDesc = it },
                        label = { Text("Description / Shooting Session Details") },
                        modifier = Modifier.fillMaxWidth().testTag("project_description_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = accentWarm,
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
                                if (projectName.isNotBlank()) {
                                    viewModel.createProject(projectName, projectDesc)
                                    showCreateDialog = false
                                }
                            }
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (projectName.isNotBlank()) {
                            viewModel.createProject(projectName, projectDesc)
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentWarm),
                    modifier = Modifier.testTag("submit_create_project_button")
                ) {
                    Text("Create", color = studioBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            },
            containerColor = panelBackground
        )
    }

    if (selectedPhotosForExport.isNotEmpty()) {
        var showBatchDeleteDialog by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(0.95f),
            color = Color(0xFF25232A),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 10.dp,
            border = BorderStroke(1.dp, accentWarm)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${selectedPhotosForExport.size} Selected",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                displayPhotos.forEach { photo ->
                                    if (!selectedPhotosForExport.containsKey(photo.id)) {
                                        onToggleSelectForExport(photo)
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Select All", color = accentWarm, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                selectedPhotosForExport.keys.toList().forEach { photoId ->
                                    selectedPhotosForExport[photoId]?.let { onToggleSelectForExport(it) }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("Deselect", color = textSecondary, fontSize = 11.sp)
                        }
                    }

                    IconButton(
                        onClick = {
                            selectedPhotosForExport.keys.toList().forEach { photoId ->
                                selectedPhotosForExport[photoId]?.let { onToggleSelectForExport(it) }
                            }
                        },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Bar", tint = textSecondary, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onNavigateToExport,
                        colors = ButtonDefaults.buttonColors(containerColor = accentWarm),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Export", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val firstPhoto = selectedPhotosForExport.values.firstOrNull()
                            if (firstPhoto != null) {
                                viewModel.editPhotoWithLibraryCheck(firstPhoto) {
                                    onNavigateToEdit()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B3842)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = textPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Edit", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val list = selectedPhotosForExport.values.toList()
                            viewModel.publishPhotos(list) {
                                list.forEach { onToggleSelectForExport(it) }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Publish", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = { showBatchDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        if (showBatchDeleteDialog) {
            val selectedList = selectedPhotosForExport.values.toList()
            AlertDialog(
                onDismissRequest = { showBatchDeleteDialog = false },
                title = {
                    Text("Delete ${selectedList.size} Photos?", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                },
                text = {
                    Text(
                        "Removes selected photo(s) from local Library and unpublishes from Firebase.\n\nNote: Camera SD card files will NOT be deleted.",
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deletePhotos(selectedList)
                            selectedList.forEach { onToggleSelectForExport(it) }
                            showBatchDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteDialog = false }) {
                        Text("Cancel", color = textSecondary, fontSize = 12.sp)
                    }
                },
                containerColor = panelBackground
            )
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoLibraryGridItem(
    photo: LocalPhoto,
    allProjects: List<LocalProject>,
    allRegistrations: List<LocalRegistration> = emptyList(),
    onSelect: () -> Unit,
    onAssignToProject: (String?) -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentWarm: Color,
    accentDark: Color = Color(0xFF332D2D),
    isSelectedForExport: Boolean = false,
    onToggleSelectForExport: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onPublish: (() -> Unit)? = null,
    onUnpublish: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val assignedProj = allProjects.find { it.id == photo.projectId }
    val assignedClient = allRegistrations.find { 
        it.id == photo.assignedUserId || 
        it.id == "cust_${photo.assignedUserId}" || 
        it.id == "user-${photo.assignedUserId}" ||
        (photo.assignedUserId != null && it.id.removePrefix("cust_").removePrefix("user-") == photo.assignedUserId.removePrefix("cust_").removePrefix("user-"))
    }
    val badgeLabel = assignedProj?.name ?: assignedClient?.name ?: photo.assignedUserName?.ifBlank { null } ?: "Unassigned"
    val isAssigned = assignedProj != null || assignedClient != null || !photo.assignedUserName.isNullOrBlank()
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onSelect() },
                onLongClick = { onToggleSelectForExport?.invoke() }
            ),
        border = BorderStroke(1.dp, if (isSelectedForExport) accentWarm else borderColor),
        colors = CardDefaults.cardColors(containerColor = if (isSelectedForExport) accentDark else Color(0xFF232127))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photo.fileUri)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = photo.fileName,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = photo.straighten },
                        contentScale = ContentScale.Crop
                    )

                    // Checkbox overlay for multi-select
                    if (onToggleSelectForExport != null) {
                        IconButton(
                            onClick = { onToggleSelectForExport() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isSelectedForExport) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = "Select for Export",
                                tint = if (isSelectedForExport) accentWarm else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Project / Client indicator badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAssigned) accentWarm else Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = photo.fileName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Display rating stars
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(5) { starIndex ->
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (starIndex < photo.rating) accentWarm else borderColor,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Photo Menu",
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF2B2930))
                            ) {
                                if (onEdit != null) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Photo", color = textPrimary, fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = accentWarm, modifier = Modifier.size(14.dp)) },
                                        onClick = {
                                            showMenu = false
                                            onEdit()
                                        }
                                    )
                                }
                                if (onPublish != null && !photo.isPublished) {
                                    DropdownMenuItem(
                                        text = { Text("Publish to Firebase", color = textPrimary, fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp)) },
                                        onClick = {
                                            showMenu = false
                                            onPublish()
                                        }
                                    )
                                }
                                if (onUnpublish != null && photo.isPublished) {
                                    DropdownMenuItem(
                                        text = { Text("Unpublish from Portal", color = Color(0xFFFFB74D), fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(14.dp)) },
                                        onClick = {
                                            showMenu = false
                                            onUnpublish()
                                        }
                                    )
                                }
                                if (onDelete != null) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Photo", color = Color(0xFFEF5350), fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(14.dp)) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                                Divider(color = borderColor)
                                DropdownMenuItem(
                                    text = { Text("Remove from Project", color = textPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        onAssignToProject(null)
                                        showMenu = false
                                    }
                                )
                                if (onToggleSelectForExport != null) {
                                    DropdownMenuItem(
                                        text = { Text(if (isSelectedForExport) "Deselect for Export" else "Select for Export", color = textPrimary, fontSize = 12.sp) },
                                        onClick = {
                                            onToggleSelectForExport()
                                            showMenu = false
                                        }
                                    )
                                }
                                allProjects.forEach { proj ->
                                    if (proj.id != photo.projectId) {
                                        DropdownMenuItem(
                                            text = { Text("Move to: ${proj.name}", color = textPrimary, fontSize = 12.sp) },
                                            onClick = {
                                                onAssignToProject(proj.id)
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text("Delete ${photo.fileName}?", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Text(
                    "Removes photo from local Library and unpublishes from Firebase.\n\nNote: Camera SD card file will NOT be deleted.",
                    color = textSecondary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete?.invoke()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = textSecondary, fontSize = 12.sp)
                }
            },
            containerColor = Color(0xFF25232A)
        )
    }
}

private fun isPhotoMatchingEventAndClient(
    photo: LocalPhoto,
    config: LocalConfig?,
    reg: LocalRegistration?
): Boolean {
    if (!photo.isPublished) return false
    if (config == null || reg == null) return false

    val eventId = config.id
    val regEventId = reg.eventId

    // 1. Strict Event Check
    val photoProjectId = photo.projectId
    val matchesEvent = photoProjectId == eventId || photoProjectId == regEventId
    if (!matchesEvent) return false

    // 2. Strict Client Check
    val clientId = reg.id
    val photoUserId = photo.assignedUserId

    if (!photoUserId.isNullOrBlank()) {
        val cleanPhotoUserId = photoUserId.removePrefix("cust_").removePrefix("user-")
        val cleanClientId = clientId.removePrefix("cust_").removePrefix("user-")
        if (photoUserId == clientId || photoUserId == "cust_$clientId" || photoUserId == "user-$clientId" || cleanPhotoUserId == cleanClientId) {
            return true
        }
    }

    // 3. Exact Frame / Filename Matching if unassigned/empty
    val photoName = photo.fileName.trim().removeSuffix(".jpg").removeSuffix(".JPG")

    if (reg.photoFiles.isNotBlank()) {
        val filesList = reg.photoFiles.split(",").map { it.trim().removeSuffix(".jpg").removeSuffix(".JPG") }
        if (filesList.any { it.equals(photoName, ignoreCase = true) }) {
            return true
        }
    }

    if (reg.notes.isNotBlank() && photoName.isNotBlank()) {
        val rangeMatch = Regex("""(?:Range:\s*|imageNumber:\s*|^)([A-Za-z]+)?\s*(\d+)\s*[-–—]\s*(?:[A-Za-z]+)?\s*(\d+)""").find(reg.notes)
        if (rangeMatch != null) {
            val startNum = rangeMatch.groupValues[2].toIntOrNull()
            val endNum = rangeMatch.groupValues[3].toIntOrNull()
            val photoNumVal = Regex("""\d+""").find(photoName)?.value?.toIntOrNull()
            if (startNum != null && endNum != null && photoNumVal != null) {
                if (photoNumVal in startNum..endNum) {
                    return true
                }
            }
        }
    }

    return false
}
