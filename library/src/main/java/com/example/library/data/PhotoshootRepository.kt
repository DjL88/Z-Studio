package com.example.library.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.storage
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import android.provider.DocumentsContract
import java.text.SimpleDateFormat
import java.util.*

class PhotoshootRepository(
    private val context: Context,
    private val photoDao: PhotoDao,
    private val settingsDao: SettingsDao,
    private val registrationDao: RegistrationDao,
    private val configDao: ConfigDao,
    private val projectDao: ProjectDao
) {
    private val tag = "PhotoshootRepository"
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Direct REST API client using service account
    private val restClient = FirestoreRestClient(context)
    private var pollingJob: kotlinx.coroutines.Job? = null
    private var registrationListener: ListenerRegistration? = null

    // Backwards compatibility flag for views
    val isFirebaseAvailable: Boolean = true

    init {
        initializeFirebaseIfNeeded(context)
    }

    private fun initializeFirebaseIfNeeded(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            try {
                // Try initializing using the standard google-services.json generated resources first
                FirebaseApp.initializeApp(context.applicationContext)
                Log.d(tag, "FirebaseApp initialized via google-services.json resources.")
            } catch (resEx: Exception) {
                Log.w(tag, "Failed to initialize via resources, trying programmatic fallback", resEx)
                try {
                    val apiKey = try {
                        val clazz = Class.forName("com.example.BuildConfig")
                        clazz.getField("FIREBASE_API_KEY").get(null) as? String ?: ""
                    } catch (e: Throwable) {
                        ""
                    }
                    
                    val options = FirebaseOptions.Builder()
                        .setProjectId("hi-domino-d0abb")
                        .setApiKey(if (apiKey.isNotEmpty()) apiKey else "AIzaSyA3ku1GeDOD28YJDUzMgyou6aEeWb7ANn8")
                        .setApplicationId("1:7304736818:android:95090721c88e46967ecc2b")
                        .setStorageBucket("hi-domino-d0abb.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(context.applicationContext, options)
                    Log.d(tag, "FirebaseApp programmatically initialized successfully with explicit options!")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to programmatically initialize FirebaseApp", e)
                }
            }
        } else {
            Log.d(tag, "FirebaseApp is already initialized.")
        }
    }

    // --- Local Room DAO exposure ---
    val allPhotos: Flow<List<LocalPhoto>> = photoDao.getAllPhotos()
    val settingsFlow: Flow<LocalSettings?> = settingsDao.getSettingsFlow()
    val allConfigs: Flow<List<LocalConfig>> = configDao.getAllConfigs()
    val allProjects: Flow<List<LocalProject>> = projectDao.getAllProjects()

    suspend fun getProjectById(projectId: String): LocalProject? {
        return projectDao.getProjectById(projectId)
    }

    suspend fun insertOrUpdateProject(project: LocalProject) {
        projectDao.insertOrUpdateProject(project)
    }

    suspend fun deleteProject(project: LocalProject) {
        projectDao.deleteProject(project)
    }

    fun getRegistrationsForEvent(eventId: String): Flow<List<LocalRegistration>> {
        return registrationDao.getRegistrationsForEvent(eventId)
    }

    fun getAllRegistrations(): Flow<List<LocalRegistration>> {
        return registrationDao.getAllRegistrations()
    }

    suspend fun getSettings(): LocalSettings {
        return settingsDao.getSettings() ?: LocalSettings().also {
            settingsDao.insertOrUpdateSettings(it)
        }
    }

    suspend fun saveSettings(settings: LocalSettings) {
        settingsDao.insertOrUpdateSettings(settings)
    }

    suspend fun getConfigById(eventId: String): LocalConfig? {
        return configDao.getConfigById(eventId)
    }

    suspend fun getFirstConfig(): LocalConfig? {
        return configDao.getFirstConfig()
    }

    suspend fun insertLocalPhoto(photo: LocalPhoto) {
        photoDao.insertOrUpdatePhoto(photo)
    }

    suspend fun getPhotoById(photoId: String): LocalPhoto? {
        return photoDao.getPhotoById(photoId)
    }

    suspend fun insertLocalConfig(config: LocalConfig) {
        configDao.insertConfigs(listOf(config))
    }

    suspend fun unpublishPhotoFromFirebase(photo: LocalPhoto) = withContext(Dispatchers.IO) {
        try {
            initializeFirebaseIfNeeded(context)
            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")
            val photoId = if (photo.id.startsWith("photo-")) photo.id else "photo-${photo.id}"
            com.google.android.gms.tasks.Tasks.await(db.collection("portal_photos").document(photoId).delete())
            Log.d(tag, "Unpublished photo from Firestore: portal_photos/$photoId")
        } catch (e: Exception) {
            Log.e(tag, "Error unpublishing photo from Firestore", e)
        }
    }

    private fun matchesPhotoNumber(photoNum: String, photoFiles: String, imageNumber: String): Boolean {
        val cleanNum = photoNum.trim().removeSuffix(".jpg").removeSuffix(".JPG").removeSuffix(".png").removeSuffix(".PNG")
        if (cleanNum.isBlank()) return false

        if (photoFiles.contains(cleanNum, ignoreCase = true) || imageNumber.contains(cleanNum, ignoreCase = true)) {
            return true
        }

        val digits = cleanNum.filter { it.isDigit() }
        val prefix = cleanNum.takeWhile { !it.isDigit() }.uppercase()
        if (digits.isNotBlank()) {
            val numVal = digits.toIntOrNull() ?: return false
            val rangeRegex = Regex("""([A-Za-z]*)(\d+)\s*[-–—]\s*([A-Za-z]*)(\d+)""")
            val textToCheck = "$imageNumber $photoFiles"
            for (match in rangeRegex.findAll(textToCheck)) {
                val rangePrefix1 = match.groupValues[1].uppercase()
                val startNum = match.groupValues[2].toIntOrNull()
                val rangePrefix2 = match.groupValues[3].uppercase()
                val endNum = match.groupValues[4].toIntOrNull()

                val effectivePrefix = if (rangePrefix1.isNotBlank()) rangePrefix1 else rangePrefix2
                if (effectivePrefix.isBlank() || effectivePrefix == prefix) {
                    if (startNum != null && endNum != null && numVal in startNum..endNum) {
                        return true
                    }
                }
            }
        }
        return false
    }

    suspend fun syncPublishedPhotosFromFirestore(sessionId: String? = null, userId: String? = null) = withContext(Dispatchers.IO) {
        try {
            initializeFirebaseIfNeeded(context)
            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")

            // If sessionId is provided, ensure we fetch registrations for this event from Firestore
            if (!sessionId.isNullOrBlank()) {
                try {
                    val regSnap = com.google.android.gms.tasks.Tasks.await(
                        db.collection("configs").document(sessionId).collection("registrations").get()
                    )
                    val fetchedRegs = ArrayList<LocalRegistration>()
                    for (doc in regSnap.documents) {
                        val rId = doc.id
                        val rName = doc.getString("name") ?: ""
                        val rEmail = doc.getString("email") ?: ""
                        val rPhone = doc.getString("phone") ?: ""
                        val rPets = doc.getString("dogNames") ?: doc.getString("pets") ?: ""
                        val rBreed = doc.getString("breed") ?: ""
                        val rImgNum = doc.getString("imageNumber") ?: ""
                        val rPhotoFiles = doc.getString("photoFiles") ?: ""
                        val rPaid = doc.getBoolean("isBookingPaid") ?: false
                        val rAmt = doc.getDouble("paymentAmount") ?: 0.0
                        fetchedRegs.add(
                            LocalRegistration(
                                id = rId,
                                eventId = sessionId,
                                name = rName,
                                email = rEmail,
                                pets = rPets,
                                notes = "Phone: $rPhone, Breed: $rBreed, Range: $rImgNum".trim(),
                                photoFiles = rPhotoFiles,
                                isBookingPaid = rPaid,
                                paymentAmount = rAmt,
                                timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                            )
                        )
                    }
                    if (fetchedRegs.isNotEmpty()) {
                        registrationDao.insertRegistrations(fetchedRegs)
                    }
                } catch (regEx: Exception) {
                    Log.w(tag, "Failed fetching event registrations before syncing published photos", regEx)
                }
            }

            val allRegs = registrationDao.getAllRegistrationsList()

            data class PubPhotoDTO(
                val id: String,
                val url: String,
                val eventId: String,
                val userId: String,
                val photoNum: String
            )

            val publishedList = ArrayList<PubPhotoDTO>()

            // 1. Try Firebase Firestore SDK first
            try {
                val snapshot = com.google.android.gms.tasks.Tasks.await(db.collection("portal_photos").get())
                for (doc in snapshot.documents) {
                    val pId = doc.id
                    val pUrl = doc.getString("photoUrl") ?: doc.getString("url") ?: doc.getString("watermarkUrl") ?: ""
                    val eId = doc.getString("eventId") ?: doc.getString("sessionId") ?: ""
                    val uId = doc.getString("userId") ?: doc.getString("pUserId") ?: doc.getString("customerId") ?: ""
                    val pNum = doc.getString("photoNumber") ?: doc.getString("name") ?: pId
                    if (pId.isNotBlank() && pUrl.isNotBlank()) {
                        publishedList.add(PubPhotoDTO(pId, pUrl, eId, uId, pNum))
                    }
                }
            } catch (sdkEx: Exception) {
                Log.w(tag, "SDK fetch portal_photos failed, falling back to REST", sdkEx)
            }

            // 2. Fallback to REST client if SDK returned empty or threw
            if (publishedList.isEmpty()) {
                try {
                    val docs = restClient.getDocuments("portal_photos")
                    for (doc in docs) {
                        val pId = doc.id
                        val pUrl = doc.fields["photoUrl"] as? String
                            ?: doc.fields["url"] as? String
                            ?: doc.fields["watermarkUrl"] as? String
                            ?: ""
                        val eId = doc.fields["eventId"] as? String ?: doc.fields["sessionId"] as? String ?: ""
                        val uId = doc.fields["userId"] as? String ?: doc.fields["pUserId"] as? String ?: ""
                        val pNum = doc.fields["photoNumber"] as? String ?: doc.fields["name"] as? String ?: pId
                        if (pId.isNotBlank() && pUrl.isNotBlank()) {
                            publishedList.add(PubPhotoDTO(pId, pUrl, eId, uId, pNum))
                        }
                    }
                } catch (restEx: Exception) {
                    Log.e(tag, "REST fetch portal_photos failed", restEx)
                }
            }

            for (dto in publishedList) {
                val cleanPhotoId = if (dto.id.startsWith("photo-")) dto.id.substringAfter("photo-") else dto.id
                val cleanEventId = dto.eventId.removePrefix("configs/").removePrefix("portal_events/").removePrefix("archives/").trim()
                val extractedUserId = if (dto.userId.startsWith("user-")) dto.userId.substringAfter("user-") else dto.userId

                // Find matching client registration strictly within the target event if sessionId is set
                val matchingReg = allRegs.find { reg ->
                    val regEventMatch = if (!sessionId.isNullOrBlank()) {
                        reg.eventId == sessionId && (cleanEventId.isEmpty() || cleanEventId == sessionId)
                    } else if (cleanEventId.isNotBlank()) {
                        reg.eventId == cleanEventId
                    } else true

                    if (!regEventMatch) return@find false

                    val idMatch = (userId != null && (reg.id == userId || reg.id == "cust_$userId" || reg.id == "user-$userId" || reg.id.removePrefix("cust_").removePrefix("user-") == userId.removePrefix("cust_").removePrefix("user-"))) ||
                                  (extractedUserId.isNotBlank() && (reg.id == extractedUserId || reg.id == "cust_$extractedUserId" || reg.id == "user-$extractedUserId" || reg.id.removePrefix("cust_").removePrefix("user-") == extractedUserId.removePrefix("cust_").removePrefix("user-")))
                    if (idMatch) return@find true

                    if (dto.photoNum.isNotBlank()) {
                        val cleanNum = dto.photoNum.trim().removeSuffix(".jpg").removeSuffix(".JPG")
                        // Exact match on photoFiles list
                        if (reg.photoFiles.isNotBlank()) {
                            val items = reg.photoFiles.split(",").map { it.trim().removeSuffix(".jpg").removeSuffix(".JPG") }
                            if (items.any { it.equals(cleanNum, ignoreCase = true) }) return@find true
                        }
                        // Range match on notes / imageNumber
                        if (reg.notes.isNotBlank()) {
                            val rangeMatch = Regex("""(?:Range:\s*|imageNumber:\s*|^)([A-Za-z]+)?\s*(\d+)\s*[-–—]\s*(?:[A-Za-z]+)?\s*(\d+)""").find(reg.notes)
                            if (rangeMatch != null) {
                                val sNum = rangeMatch.groupValues[2].toIntOrNull()
                                val eNum = rangeMatch.groupValues[3].toIntOrNull()
                                val pNum = Regex("""\d+""").find(cleanNum)?.value?.toIntOrNull()
                                if (sNum != null && eNum != null && pNum != null && pNum in sNum..eNum) {
                                    return@find true
                                }
                            }
                        }
                    }
                    false
                }

                val finalUserId = matchingReg?.id ?: extractedUserId.ifBlank { null }
                val finalUserName = matchingReg?.name
                val finalEventId = if (cleanEventId.isNotBlank()) cleanEventId else (matchingReg?.eventId ?: sessionId ?: "")

                val matchesEvent = sessionId.isNullOrBlank() || finalEventId == sessionId
                val matchesUser = userId.isNullOrBlank() || finalUserId == userId || finalUserId == "cust_$userId" || finalUserId == "user-$userId"
                if (!matchesEvent || !matchesUser) continue

                val existing = photoDao.getPhotoById(cleanPhotoId)
                val timestamp = System.currentTimeMillis()
                val photo = existing?.copy(
                    isPublished = true,
                    publishedUrl = dto.url,
                    assignedUserId = finalUserId ?: existing.assignedUserId,
                    assignedUserName = finalUserName ?: existing.assignedUserName,
                    projectId = finalEventId.ifBlank { existing.projectId },
                    isPromotedToLibrary = true
                ) ?: LocalPhoto(
                    id = cleanPhotoId,
                    fileUri = dto.url,
                    fileName = if (dto.photoNum.endsWith(".jpg", ignoreCase = true)) dto.photoNum else "${dto.photoNum}.jpg",
                    timestamp = timestamp,
                    isPublished = true,
                    publishedUrl = dto.url,
                    assignedUserId = finalUserId,
                    assignedUserName = finalUserName,
                    projectId = finalEventId,
                    isPromotedToLibrary = true
                )
                photoDao.insertOrUpdatePhoto(photo)
            }
            Log.d(tag, "Synced ${publishedList.size} published photos from Firestore portal_photos successfully.")
        } catch (e: Exception) {
            Log.e(tag, "Error syncing published photos from Firestore", e)
        }
    }

    suspend fun deleteLocalPhoto(photo: LocalPhoto) = withContext(Dispatchers.IO) {
        if (photo.isPublished) {
            try {
                unpublishPhotoFromFirebase(photo)
            } catch (e: Exception) {
                Log.e(tag, "Failed to unpublish photo from Firebase during delete", e)
            }
        }
        try {
            val uri = Uri.parse(photo.fileUri)
            if (uri.scheme == "file" || uri.scheme == null) {
                val file = File(uri.path ?: photo.fileUri)
                // Only delete if it's in tether_cache (local temp cache), NEVER if it's on physical SD card / external camera folder
                if (file.exists() && file.parentFile?.name == "tether_cache") {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete physical cache file", e)
        }
        try {
            photoDao.deletePhoto(photo)
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete photo from Room database", e)
        }
    }

    // --- Local file caching helper (for imported/tethered photos) ---
    suspend fun saveImageToCache(bitmap: Bitmap, prefix: String): String = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "tether_cache").apply { mkdirs() }
        val filename = "${prefix}_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        file.absolutePath
    }

    suspend fun saveBytesToCache(data: ByteArray, prefix: String): String = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "tether_cache").apply { mkdirs() }
        val filename = "${prefix}_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, filename)
        FileOutputStream(file).use { out ->
            out.write(data)
        }
        file.absolutePath
    }

    suspend fun saveBytesToStructuredStorage(
        data: ByteArray,
        rawFileName: String,
        eventName: String? = null,
        eventId: String? = null,
        clientName: String? = null,
        clientId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val appName = "Pawtrait Kiosk"
        val safeEventName = (eventName ?: "Shoot Event").take(25).replace(Regex("[^a-zA-Z0-9 ._-]"), "_")
        val safeEventId = (eventId ?: "session_active").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeClientName = (clientName ?: "Unassigned Client").ifEmpty { "Unassigned Client" }.take(25).replace(Regex("[^a-zA-Z0-9 ._-]"), "_")
        val safeClientId = (clientId ?: "no_client").ifEmpty { "no_client" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")

        val folderStructure = "$appName/$safeEventName ($safeEventId)/$safeClientName ($safeClientId)"
        val storageDir = File(context.getExternalFilesDir(null) ?: context.filesDir, folderStructure).apply { mkdirs() }

        val safeFileName = if (rawFileName.startsWith("DSC") || rawFileName.contains(".")) rawFileName else "DSC_${rawFileName}.jpg"
        val targetFile = File(storageDir, safeFileName)
        FileOutputStream(targetFile).use { out ->
            out.write(data)
        }
        targetFile.absolutePath
    }

    // --- Firestore SDK / REST Synchronizer ---
    suspend fun fetchConfigsFromFirebase(): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Starting fetchConfigsFromFirebase via Firebase SDK")
            initializeFirebaseIfNeeded(context)

            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")

            // 1. Fetch active configuration document from 'configs/active'
            val activeDocRef = db.collection("configs").document("active")
            val activeSnapshot = com.google.android.gms.tasks.Tasks.await(activeDocRef.get())

            val activeEventName = activeSnapshot.getString("eventName")
            val activeBrand = activeSnapshot.getString("photographerBrand") ?: "David Leitch Photography"
            val activeLogo = activeSnapshot.getString("logoDataUrl") ?: activeSnapshot.getString("portalLogoDataUrl")
            val activeTheme = activeSnapshot.getString("portalTheme") ?: "dark"

            // 2. Fetch photoshoot events from all three Firestore collections:
            // - configs (LIVE SHOOTS)
            // - portal_events (NON-LIVE / PORTAL)
            // - archives (ARCHIVED)
            val portalEventsList = ArrayList<LocalConfig>()
            val seenIds = HashSet<String>()

            // 2a. Fetch Live Shoots from 'configs'
            try {
                val configsSnapshot = com.google.android.gms.tasks.Tasks.await(db.collection("configs").get())
                for (doc in configsSnapshot.documents) {
                    if (doc.id == "active") continue
                    val id = doc.id
                    val name = doc.getString("eventName") ?: doc.getString("name") ?: id
                    val date = doc.getString("eventDate") ?: doc.getString("createdAt") ?: ""
                    val loc = doc.getString("eventLocation") ?: doc.getString("venueName") ?: "Studio"
                    val brand = doc.getString("photographerBrand") ?: activeBrand
                    val logo = doc.getString("logoDataUrl") ?: doc.getString("portalLogoDataUrl") ?: activeLogo
                    val theme = doc.getString("portalTheme") ?: activeTheme
                    val priceOnDay = doc.getDouble("priceOnDay") ?: 15.0
                    val pricePerPhoto = doc.getDouble("pricePerPhoto") ?: 15.0
                    val includedFreeDownloads = doc.getLong("includedFreeDownloads")?.toInt() ?: 1

                    if (seenIds.add(id)) {
                        portalEventsList.add(LocalConfig(id, name, date, loc, brand, logo, theme, priceOnDay, pricePerPhoto, includedFreeDownloads, sourceCollection = "configs"))
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch configs collection", e)
            }

            // 2b. Fetch Non-Live Portal Folders from 'portal_events'
            try {
                val eventsSnapshot = com.google.android.gms.tasks.Tasks.await(db.collection("portal_events").get())
                for (doc in eventsSnapshot.documents) {
                    val id = doc.id
                    val name = doc.getString("eventName") ?: doc.getString("name") ?: continue
                    val venue = doc.getString("venueName") ?: doc.getString("eventLocation") ?: "Studio"
                    val date = doc.getString("createdAt") ?: doc.getString("eventDate") ?: ""
                    val brand = doc.getString("photographerBrand") ?: activeBrand
                    val logo = doc.getString("logoDataUrl") ?: doc.getString("portalLogoDataUrl") ?: activeLogo
                    val theme = doc.getString("portalTheme") ?: activeTheme
                    val priceOnDay = doc.getDouble("priceOnDay") ?: 15.0
                    val pricePerPhoto = doc.getDouble("pricePerPhoto") ?: 15.0
                    val includedFreeDownloads = doc.getLong("includedFreeDownloads")?.toInt() ?: 1

                    if (seenIds.add(id)) {
                        portalEventsList.add(LocalConfig(id, name, date, venue, brand, logo, theme, priceOnDay, pricePerPhoto, includedFreeDownloads, sourceCollection = "portal_events"))
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch portal_events collection", e)
            }

            // 2c. Fetch Archived Events from 'archives'
            try {
                val archivesSnapshot = com.google.android.gms.tasks.Tasks.await(db.collection("archives").get())
                for (doc in archivesSnapshot.documents) {
                    val id = doc.id
                    val name = doc.getString("eventName") ?: doc.getString("name") ?: "Archived Event"
                    val date = doc.getString("eventDate") ?: doc.getString("createdAt") ?: ""
                    val loc = doc.getString("eventLocation") ?: doc.getString("venueName") ?: "Studio"
                    val brand = doc.getString("photographerBrand") ?: activeBrand
                    val logo = doc.getString("logoDataUrl") ?: doc.getString("portalLogoDataUrl") ?: activeLogo
                    val theme = doc.getString("portalTheme") ?: activeTheme
                    val priceOnDay = doc.getDouble("priceOnDay") ?: 15.0
                    val pricePerPhoto = doc.getDouble("pricePerPhoto") ?: 15.0
                    val includedFreeDownloads = doc.getLong("includedFreeDownloads")?.toInt() ?: 1

                    if (seenIds.add(id)) {
                        portalEventsList.add(LocalConfig(id, name, date, loc, brand, logo, theme, priceOnDay, pricePerPhoto, includedFreeDownloads, sourceCollection = "archives"))
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch archives collection", e)
            }

            // If we still have nothing and activeDoc is present, we map activeDoc itself as an event
            if (portalEventsList.isEmpty() && activeSnapshot.exists() && activeEventName != null) {
                portalEventsList.add(
                    LocalConfig(
                        id = "active_event",
                        eventName = activeEventName,
                        eventDate = "",
                        eventLocation = activeSnapshot.getString("venueName") ?: "Studio",
                        photographerBrand = activeBrand,
                        logoUrl = activeLogo,
                        portalTheme = activeTheme
                    )
                )
            }

            // Save to local database
            configDao.clearAllConfigs()
            configDao.insertConfigs(portalEventsList)
            Log.d(tag, "Successfully synced ${portalEventsList.size} event configurations from Firestore SDK")

            // 4. Try to match the active event ID
            var matchedEventId = activeSnapshot.getString("activeEventId") ?: activeSnapshot.getString("eventId")
            if (matchedEventId == null && activeEventName != null) {
                matchedEventId = portalEventsList.find { it.eventName.equals(activeEventName, ignoreCase = true) }?.id
            }
            if (matchedEventId == null && portalEventsList.size == 1) {
                matchedEventId = portalEventsList.first().id
            }

            return@withContext matchedEventId
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch configurations from Firestore via SDK. Trying REST fallback...", e)
            try {
                val fallbackEventId = fetchConfigsFromRESTFallback()
                Log.d(tag, "Successfully fetched configurations using REST fallback: $fallbackEventId")
                return@withContext fallbackEventId
            } catch (fallbackEx: Exception) {
                Log.e(tag, "REST Fallback also failed", fallbackEx)
                throw e
            }
        }
    }

    private suspend fun fetchConfigsFromRESTFallback(): String? = withContext(Dispatchers.IO) {
        // 1. Fetch active configuration document from 'configs/active'
        val activeDoc = try {
            restClient.getDocument("configs/active")
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch active config document configs/active via REST", e)
            null
        }

        val activeEventName = activeDoc?.fields?.get("eventName") as? String
        val activeBrand = (activeDoc?.fields?.get("photographerBrand") as? String) ?: "David Leitch Photography"
        val activeLogo = (activeDoc?.fields?.get("logoDataUrl") as? String) ?: (activeDoc?.fields?.get("portalLogoDataUrl") as? String)
        val activeTheme = (activeDoc?.fields?.get("portalTheme") as? String) ?: "dark"

        // 2. Fetch photoshoot events from all three collections:
        // - configs (LIVE SHOOTS)
        // - portal_events (NON-LIVE / PORTAL)
        // - archives (ARCHIVED)
        val portalEventsList = ArrayList<LocalConfig>()
        val seenIds = HashSet<String>()

        // 2a. Fetch configs (LIVE SHOOTS)
        try {
            val configs = restClient.getDocuments("configs")
            for (doc in configs) {
                if (doc.id == "active") continue
                val id = doc.id
                val name = doc.fields["eventName"] as? String ?: doc.fields["name"] as? String ?: id
                val date = doc.fields["eventDate"] as? String ?: doc.fields["createdAt"] as? String ?: ""
                val loc = doc.fields["eventLocation"] as? String ?: doc.fields["venueName"] as? String ?: "Studio"
                val brand = (doc.fields["photographerBrand"] as? String) ?: activeBrand
                val logo = (doc.fields["logoDataUrl"] as? String) ?: (doc.fields["portalLogoDataUrl"] as? String) ?: activeLogo
                val theme = (doc.fields["portalTheme"] as? String) ?: activeTheme
                val priceOnDay = (doc.fields["priceOnDay"] as? Number)?.toDouble() ?: 15.0
                val pricePerPhoto = (doc.fields["pricePerPhoto"] as? Number)?.toDouble() ?: 15.0
                val includedFreeDownloads = (doc.fields["includedFreeDownloads"] as? Number)?.toInt() ?: 1

                if (seenIds.add(id)) {
                    portalEventsList.add(LocalConfig(id, name, date, loc, brand, logo, theme, priceOnDay, pricePerPhoto, includedFreeDownloads, sourceCollection = "configs"))
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "REST fetch configs failed", e)
        }

        // 2b. Fetch portal_events (NON-LIVE / PORTAL)
        try {
            val events = restClient.getDocuments("portal_events")
            for (doc in events) {
                val id = doc.id
                val name = doc.fields["eventName"] as? String ?: doc.fields["name"] as? String ?: continue
                val venue = doc.fields["venueName"] as? String ?: doc.fields["eventLocation"] as? String ?: "Studio"
                val date = doc.fields["createdAt"] as? String ?: doc.fields["eventDate"] as? String ?: ""
                val brand = (doc.fields["photographerBrand"] as? String) ?: activeBrand
                val logo = (doc.fields["logoDataUrl"] as? String) ?: (doc.fields["portalLogoDataUrl"] as? String) ?: activeLogo
                val theme = (doc.fields["portalTheme"] as? String) ?: activeTheme
                val priceOnDay = (doc.fields["priceOnDay"] as? Number)?.toDouble() ?: 15.0
                val pricePerPhoto = (doc.fields["pricePerPhoto"] as? Number)?.toDouble() ?: 15.0
                val includedFreeDownloads = (doc.fields["includedFreeDownloads"] as? Number)?.toInt() ?: 1

                if (seenIds.add(id)) {
                    portalEventsList.add(LocalConfig(id, name, date, venue, brand, logo, theme, priceOnDay, pricePerPhoto, includedFreeDownloads, sourceCollection = "portal_events"))
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "REST fetch portal_events failed", e)
        }

        // 2c. Fetch archives (ARCHIVED)
        try {
            val archives = restClient.getDocuments("archives")
            for (doc in archives) {
                val id = doc.id
                val name = doc.fields["eventName"] as? String ?: doc.fields["name"] as? String ?: "Archived Event"
                val date = doc.fields["eventDate"] as? String ?: doc.fields["createdAt"] as? String ?: ""
                val loc = doc.fields["eventLocation"] as? String ?: doc.fields["venueName"] as? String ?: "Studio"
                val brand = (doc.fields["photographerBrand"] as? String) ?: activeBrand
                val logo = (doc.fields["logoDataUrl"] as? String) ?: (doc.fields["portalLogoDataUrl"] as? String) ?: activeLogo
                val theme = (doc.fields["portalTheme"] as? String) ?: activeTheme
                val priceOnDay = (doc.fields["priceOnDay"] as? Number)?.toDouble() ?: 15.0
                val pricePerPhoto = (doc.fields["pricePerPhoto"] as? Number)?.toDouble() ?: 15.0
                val includedFreeDownloads = (doc.fields["includedFreeDownloads"] as? Number)?.toInt() ?: 1

                if (seenIds.add(id)) {
                    portalEventsList.add(LocalConfig(id, name, date, loc, brand, logo, theme, priceOnDay, pricePerPhoto, includedFreeDownloads, sourceCollection = "archives"))
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "REST fetch archives failed", e)
        }

        // If we still have nothing and activeDoc is present, we map activeDoc itself as an event
        if (portalEventsList.isEmpty() && activeDoc != null && activeEventName != null) {
            portalEventsList.add(
                LocalConfig(
                    id = "active_event",
                    eventName = activeEventName,
                    eventDate = "",
                    eventLocation = activeDoc.fields["venueName"] as? String ?: "Studio",
                    photographerBrand = activeBrand,
                    logoUrl = activeLogo,
                    portalTheme = activeTheme
                )
            )
        }

        // Save to local database
        configDao.clearAllConfigs()
        configDao.insertConfigs(portalEventsList)
        Log.d(tag, "Successfully synced ${portalEventsList.size} event configurations from Firestore (REST Fallback)")

        // 4. Try to match the active event ID
        var matchedEventId = (activeDoc?.fields?.get("activeEventId") as? String) ?: (activeDoc?.fields?.get("eventId") as? String)
        if (matchedEventId == null && activeEventName != null) {
            matchedEventId = portalEventsList.find { it.eventName.equals(activeEventName, ignoreCase = true) }?.id
        }
        if (matchedEventId == null && portalEventsList.size == 1) {
            matchedEventId = portalEventsList.first().id
        }

        matchedEventId
    }

    fun startListeningToRegistrations(eventId: String) {
        // Clear any existing active listener or job
        stopListeningToRegistrations()

        Log.d(tag, "Starting Firestore Listener for configs/$eventId/registrations")
        try {
            initializeFirebaseIfNeeded(context)
            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")
            val collectionRef = db.collection("configs").document(eventId).collection("registrations")

            registrationListener = collectionRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Firestore snapshot listener failed for registrations. Falling back to HTTP polling...", error)
                    startPollingRegistrationsFallback(eventId)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = ArrayList<LocalRegistration>()
                    for (doc in snapshot.documents) {
                        val id = doc.id
                        val name = doc.getString("name") ?: ""
                        val email = doc.getString("email") ?: ""

                        val petsVal = doc.get("pets")
                        val petsStr = when (petsVal) {
                            is List<*> -> petsVal.filterIsInstance<String>().joinToString(", ")
                            is String -> petsVal
                            else -> ""
                        }

                        val notes = doc.getString("notes") ?: ""
                        val timestamp = doc.getString("timestamp") ?: ""
                        val count = doc.getLong("photosUploaded")?.toInt() ?: 0
                        val photoFiles = doc.getString("photoFiles") ?: doc.getString("imageNumber") ?: ""
                        val isBookingPaid = doc.getBoolean("isBookingPaid") ?: false
                        val paymentMethod = doc.getString("paymentMethod") ?: ""
                        val paymentAmount = doc.getDouble("paymentAmount") ?: 0.0
                        val paymentRef = doc.getString("paymentRef") ?: ""
                        val paymentTimestamp = doc.getString("paymentTimestamp") ?: ""
                        val portalPin = doc.getString("portalPin") ?: ""

                        list.add(
                            LocalRegistration(
                                id = id,
                                eventId = eventId,
                                name = name,
                                email = email,
                                pets = petsStr,
                                notes = notes,
                                timestamp = timestamp,
                                photosUploaded = count,
                                photoFiles = photoFiles,
                                isBookingPaid = isBookingPaid,
                                paymentMethod = paymentMethod,
                                paymentAmount = paymentAmount,
                                paymentRef = paymentRef,
                                paymentTimestamp = paymentTimestamp,
                                portalPin = portalPin
                            )
                        )
                    }

                    repositoryScope.launch {
                        registrationDao.clearRegistrationsForEvent(eventId)
                        registrationDao.insertRegistrations(list)
                        Log.d(tag, "Successfully synced ${list.size} registrations via Firestore SDK Listener for event $eventId")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start snapshot listener. Falling back to HTTP polling...", e)
            startPollingRegistrationsFallback(eventId)
        }
    }

    private fun startPollingRegistrationsFallback(eventId: String) {
        pollingJob?.cancel()
        Log.d(tag, "Starting Firestore HTTP polling fallback for configs/$eventId/registrations (every 8 seconds)")
        pollingJob = repositoryScope.launch {
            while (true) {
                try {
                    val registrations = fetchRegistrationsFromREST(eventId)
                    registrationDao.clearRegistrationsForEvent(eventId)
                    registrationDao.insertRegistrations(registrations)
                    Log.d(tag, "Updated Room (fallback) with ${registrations.size} registrations for event $eventId")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to poll registrations fallback for event $eventId", e)
                }
                kotlinx.coroutines.delay(8000)
            }
        }
    }

    private fun fetchRegistrationsFromREST(eventId: String): List<LocalRegistration> {
        val path = "configs/$eventId/registrations"
        val docs = restClient.getDocuments(path)
        return docs.map { doc ->
            val id = doc.id
            val name = doc.fields["name"] as? String ?: ""
            val email = doc.fields["email"] as? String ?: ""

            val petsVal = doc.fields["pets"]
            val petsStr = when (petsVal) {
                is List<*> -> petsVal.filterIsInstance<String>().joinToString(", ")
                is String -> petsVal
                else -> ""
            }

            val notes = doc.fields["notes"] as? String ?: ""
            val timestamp = doc.fields["timestamp"] as? String ?: ""
            val count = (doc.fields["photosUploaded"] as? Number)?.toInt() ?: 0
            val photoFiles = (doc.fields["photoFiles"] as? String) ?: (doc.fields["imageNumber"] as? String) ?: ""
            val isBookingPaid = (doc.fields["isBookingPaid"] as? Boolean) ?: false
            val paymentMethod = (doc.fields["paymentMethod"] as? String) ?: ""
            val paymentAmount = (doc.fields["paymentAmount"] as? Number)?.toDouble() ?: 0.0
            val paymentRef = (doc.fields["paymentRef"] as? String) ?: ""
            val paymentTimestamp = (doc.fields["paymentTimestamp"] as? String) ?: ""
            val portalPin = (doc.fields["portalPin"] as? String) ?: ""

            LocalRegistration(
                id = id,
                eventId = eventId,
                name = name,
                email = email,
                pets = petsStr,
                notes = notes,
                timestamp = timestamp,
                photosUploaded = count,
                photoFiles = photoFiles,
                isBookingPaid = isBookingPaid,
                paymentMethod = paymentMethod,
                paymentAmount = paymentAmount,
                paymentRef = paymentRef,
                paymentTimestamp = paymentTimestamp,
                portalPin = portalPin
            )
        }
    }

    fun stopListeningToRegistrations() {
        registrationListener?.remove()
        registrationListener = null
        pollingJob?.cancel()
        pollingJob = null
    }

    // --- Publish Photo Workflow ---
    suspend fun publishPhoto(
        localPhoto: LocalPhoto,
        editedBitmap: Bitmap,
        isHero: Boolean = false,
        replaceExisting: Boolean = false
    ): LocalPhoto = withContext(Dispatchers.IO) {
        val eventId = localPhoto.assignedUserId?.let { userId ->
            registrationDao.getRegistrationById(userId)?.eventId
        } ?: "default_event"

        val eventConfig = configDao.getConfigById(eventId)
        val eventName = eventConfig?.eventName ?: "Photoshoot Event"

        val fileName = "edited_${localPhoto.fileName}"
        val uniqueFileName = "${UUID.randomUUID()}_$fileName"
        val storagePath = "portals/photos/$uniqueFileName"

        var downloadUrl = ""

        // 1. Export locally using Storage Access Framework (SAF)
        val settings = settingsDao.getSettings()
        val exportDirUriStr = settings?.exportDirectoryUri
        
        val baos = ByteArrayOutputStream()
        editedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
        val jpegBytes = baos.toByteArray()

        var firebaseStorageUrl = ""
        try {
            firebaseStorageUrl = restClient.uploadToStorage(storagePath, jpegBytes)
            Log.d(tag, "Successfully uploaded photo to Firebase Storage via REST: $firebaseStorageUrl")
        } catch (storageEx: Exception) {
            Log.w(tag, "Firebase Storage REST upload failed, trying SDK...", storageEx)
            try {
                initializeFirebaseIfNeeded(context)
                val storageRef = Firebase.storage("gs://hi-domino-d0abb.firebasestorage.app").reference.child(storagePath)
                val uploadTask = storageRef.putBytes(jpegBytes)
                com.google.android.gms.tasks.Tasks.await(uploadTask)
                val downloadUri = com.google.android.gms.tasks.Tasks.await(storageRef.downloadUrl)
                firebaseStorageUrl = downloadUri.toString()
                Log.d(tag, "Successfully uploaded photo to Firebase Storage via SDK: $firebaseStorageUrl")
            } catch (sdkEx: Exception) {
                Log.e(tag, "Firebase Storage upload via SDK also failed", sdkEx)
            }
        }

        if (!exportDirUriStr.isNullOrEmpty()) {
            try {
                val treeUri = Uri.parse(exportDirUriStr)
                val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
                
                val fileUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocumentUri,
                    "image/jpeg",
                    fileName
                )
                
                if (fileUri != null) {
                    context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(jpegBytes)
                    }
                    downloadUrl = fileUri.toString()
                    Log.d(tag, "Successfully published and exported photo locally via SAF to: $downloadUrl")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to write to SAF export folder", e)
            }
        }

        if (downloadUrl.isEmpty()) {
            try {
                val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val folder = File(imagesDir, "TetherStudio").apply { mkdirs() }
                val imageFile = File(folder, "${System.currentTimeMillis()}_$fileName")
                val fos = FileOutputStream(imageFile)
                fos.write(jpegBytes)
                fos.flush()
                fos.close()
                writeGpsToExif(imageFile.absolutePath, localPhoto.gpsLatitude, localPhoto.gpsLongitude)
                downloadUrl = Uri.fromFile(imageFile).toString()
                Log.d(tag, "Successfully published and exported photo to fallback pictures gallery: $downloadUrl")
            } catch (e: Exception) {
                Log.e(tag, "Failed to save to fallback pictures gallery", e)
                downloadUrl = "local_sandbox_link://${System.currentTimeMillis()}"
            }
        }

        val publicUrl = if (firebaseStorageUrl.isNotBlank()) firebaseStorageUrl else downloadUrl

        // 2. Try updating Firebase Firestore metadata, with fallback to REST metadata creation
        try {
            initializeFirebaseIfNeeded(context)
            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")
            val photoId = if (replaceExisting) {
                "photo-${localPhoto.id}"
            } else {
                "photo-${localPhoto.id}-${System.currentTimeMillis().toString().takeLast(5)}"
            }
            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val rawUserId = localPhoto.assignedUserId.orEmpty()
            val mappedUserId = when {
                rawUserId.isEmpty() -> ""
                rawUserId.startsWith("user-") -> rawUserId
                else -> "user-$rawUserId"
            }

            val photoNumberClean = localPhoto.fileName.substringBeforeLast('.')
            val photoMetadata = mapOf(
                "id" to photoId,
                "name" to fileName,
                "photoNumber" to photoNumberClean,
                "photoUrl" to publicUrl,
                "url" to publicUrl,
                "watermarkUrl" to publicUrl,
                "thumbnailLink" to publicUrl,
                "webContentLink" to publicUrl,
                "webViewLink" to publicUrl,
                "eventId" to eventId,
                "eventName" to eventName,
                "userId" to mappedUserId,
                "isHero" to isHero,
                "createdAt" to isoDate,
                "uploadedAt" to isoDate,
                "updatedAt" to isoDate
            )

            com.google.android.gms.tasks.Tasks.await(db.collection("portal_photos").document(photoId).set(photoMetadata))
            Log.d(tag, "Successfully wrote photo metadata via Firebase Firestore SDK to: portal_photos/$photoId")

            if (!replaceExisting) {
                localPhoto.assignedUserId?.let { userId ->
                    try {
                        val regDocRef = db.collection("configs").document(eventId).collection("registrations").document(userId)
                        val regDoc = com.google.android.gms.tasks.Tasks.await(regDocRef.get())
                        if (regDoc.exists()) {
                            val currentCount = regDoc.getLong("photosUploaded") ?: 0L
                            com.google.android.gms.tasks.Tasks.await(regDocRef.update("photosUploaded", currentCount + 1L))
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to increment subcollection photosUploaded count via SDK", e)
                    }

                    try {
                        val userDocRef = db.collection("portal_users").document(userId)
                        val userDoc = com.google.android.gms.tasks.Tasks.await(userDocRef.get())
                        if (userDoc.exists()) {
                            val currentCount = userDoc.getLong("photosUploaded") ?: 0L
                            com.google.android.gms.tasks.Tasks.await(userDocRef.update("photosUploaded", currentCount + 1L))
                        } else {
                            val isoZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(Date())
                            val userProfile = mapOf(
                                "id" to userId,
                                "name" to localPhoto.assignedUserName.orEmpty(),
                                "createdAt" to isoZ,
                                "photosUploaded" to 1L
                            )
                            com.google.android.gms.tasks.Tasks.await(userDocRef.set(userProfile))
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to increment/create portal_user profile via SDK", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error publishing photo metadata via Firebase SDK, falling back to REST...", e)
            try {
                val photoId = if (replaceExisting) {
                    "photo-${localPhoto.id}"
                } else {
                    "photo-${localPhoto.id}-${System.currentTimeMillis().toString().takeLast(5)}"
                }
                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val rawUserIdFallback = localPhoto.assignedUserId.orEmpty()
                val mappedUserIdFallback = when {
                    rawUserIdFallback.isEmpty() -> ""
                    rawUserIdFallback.startsWith("user-") -> rawUserIdFallback
                    else -> "user-$rawUserIdFallback"
                }

                val photoNumberCleanFallback = localPhoto.fileName.substringBeforeLast('.')
                val photoMetadata = mapOf(
                    "id" to photoId,
                    "name" to fileName,
                    "photoNumber" to photoNumberCleanFallback,
                    "photoUrl" to publicUrl,
                    "url" to publicUrl,
                    "watermarkUrl" to publicUrl,
                    "thumbnailLink" to publicUrl,
                    "webContentLink" to publicUrl,
                    "webViewLink" to publicUrl,
                    "eventId" to eventId,
                    "eventName" to eventName,
                    "userId" to mappedUserIdFallback,
                    "isHero" to isHero,
                    "createdAt" to isoDate,
                    "uploadedAt" to isoDate,
                    "updatedAt" to isoDate
                )
                restClient.createDocument("portal_photos", photoId, photoMetadata)
                Log.d(tag, "Successfully published photo metadata via REST fallback")

                if (!replaceExisting) {
                    localPhoto.assignedUserId?.let { userId ->
                        try {
                            val regPath = "configs/$eventId/registrations/$userId"
                            val regDoc = restClient.getDocument(regPath)
                            if (regDoc != null) {
                                val currentCount = (regDoc.fields["photosUploaded"] as? Number)?.toLong() ?: 0L
                                restClient.updateDocument(regPath, mapOf("photosUploaded" to currentCount + 1L))
                            }
                        } catch (restEx: Exception) {
                            Log.e(tag, "REST increment registration failed", restEx)
                        }

                        try {
                            val userPath = "portal_users/$userId"
                            val userDoc = restClient.getDocument(userPath)
                            if (userDoc != null) {
                                val currentCount = (userDoc.fields["photosUploaded"] as? Number)?.toLong() ?: 0L
                                restClient.updateDocument(userPath, mapOf("photosUploaded" to currentCount + 1L))
                            } else {
                                val isoZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }.format(Date())
                                val userProfile = mapOf(
                                    "id" to userId,
                                    "name" to localPhoto.assignedUserName.orEmpty(),
                                    "createdAt" to isoZ,
                                    "photosUploaded" to 1L
                                )
                                restClient.createDocument("portal_users", documentId = userId, fields = userProfile)
                            }
                        } catch (restEx: Exception) {
                            Log.e(tag, "REST increment portal_user failed", restEx)
                        }
                    }
                }
            } catch (fallbackEx: Exception) {
                Log.e(tag, "REST Fallback also failed for metadata", fallbackEx)
            }
        }

        // 5. Update local Room database status (ONLY if NOT replacing)
        if (!replaceExisting) {
            localPhoto.assignedUserId?.let { userId ->
                registrationDao.incrementPhotoCount(userId)
            }
        }
        val updatedPhoto = localPhoto.copy(
            isPublished = true,
            publishedUrl = publicUrl,
            brightness = localPhoto.brightness,
            contrast = localPhoto.contrast,
            saturation = localPhoto.saturation,
            shadows = localPhoto.shadows,
            highlights = localPhoto.highlights,
            filterName = localPhoto.filterName,
            blurIntensity = localPhoto.blurIntensity,
            maskCenterX = localPhoto.maskCenterX,
            maskCenterY = localPhoto.maskCenterY,
            maskRadius = localPhoto.maskRadius,
            cropLeft = localPhoto.cropLeft,
            cropTop = localPhoto.cropTop,
            cropRight = localPhoto.cropRight,
            cropBottom = localPhoto.cropBottom
        )
        photoDao.insertOrUpdatePhoto(updatedPhoto)
        updatedPhoto
    }

    suspend fun ensurePortalUserAndGetPin(
        userId: String,
        clientName: String,
        email: String,
        phone: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext "0000"
        val isoZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        try {
            initializeFirebaseIfNeeded(context)
            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")
            val userDocRef = db.collection("portal_users").document(userId)
            val userDoc = com.google.android.gms.tasks.Tasks.await(userDocRef.get())

            if (userDoc.exists()) {
                val existingPin = userDoc.getString("pin")
                if (!existingPin.isNullOrEmpty() && existingPin.isNotBlank()) {
                    Log.d(tag, "Existing portal_user PIN found: $existingPin for $userId")
                    return@withContext existingPin
                }
            }

            val newPin = String.format("%04d", (1000..9999).random())
            val username = if (email.contains("@")) email.substringBefore("@") else clientName.lowercase().replace(" ", ".")

            val userMap = mutableMapOf<String, Any>(
                "id" to userId,
                "name" to clientName,
                "email" to email,
                "phone" to phone,
                "username" to username,
                "pin" to newPin,
                "createdAt" to isoZ
            )

            if (userDoc.exists()) {
                com.google.android.gms.tasks.Tasks.await(userDocRef.update(userMap))
            } else {
                com.google.android.gms.tasks.Tasks.await(userDocRef.set(userMap))
            }
            Log.d(tag, "Created/Updated portal_users profile for $userId with PIN: $newPin")
            newPin
        } catch (e: Exception) {
            Log.e(tag, "Firebase SDK error in ensurePortalUserAndGetPin, falling back to REST", e)
            try {
                val userDoc = restClient.getDocument("portal_users/$userId")
                if (userDoc != null) {
                    val existingPin = userDoc.fields["pin"] as? String
                    if (!existingPin.isNullOrEmpty() && existingPin.isNotBlank()) {
                        return@withContext existingPin
                    }
                }
                val newPin = String.format("%04d", (1000..9999).random())
                val username = if (email.contains("@")) email.substringBefore("@") else clientName.lowercase().replace(" ", ".")
                val userMap = mapOf(
                    "id" to userId,
                    "name" to clientName,
                    "email" to email,
                    "phone" to phone,
                    "username" to username,
                    "pin" to newPin,
                    "createdAt" to isoZ
                )
                restClient.createDocument("portal_users", documentId = userId, fields = userMap)
                newPin
            } catch (restEx: Exception) {
                Log.e(tag, "REST fallback error in ensurePortalUserAndGetPin", restEx)
                String.format("%04d", (1000..9999).random())
            }
        }
    }

    suspend fun notifyClientPhotosUploaded(
        email: String,
        clientName: String,
        eventName: String,
        pin: String,
        identifier: String,
        photoCount: Int,
        brandName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = "https://shoots.djleitch.com/api/notify/photos-uploaded"
            val url = java.net.URL(targetUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val json = org.json.JSONObject().apply {
                put("email", email)
                put("clientName", clientName)
                put("eventName", eventName)
                put("pin", pin)
                put("identifier", if (identifier.isNotBlank()) identifier else email)
                put("photoCount", photoCount)
                put("brandName", if (brandName.isNotBlank()) brandName else "Photos by David Leitch")
                put("smtpEmail", "photos@djleitch.co.uk")
                put("smtpPassword", "gkcmtwffryirxenm")
                put("senderName", "Photos by David Leitch")
            }.toString()

            conn.outputStream.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.d(tag, "Notify API HTTP Response ($code): $responseText")
            if (code in 200..299) {
                Result.success(responseText)
            } else {
                Result.failure(Exception("HTTP $code: $responseText"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to trigger notify API endpoint", e)
            Result.failure(e)
        }
    }

    suspend fun manuallyCreateClient(
        eventId: String,
        name: String,
        email: String,
        pets: String,
        notes: String
    ): Boolean = withContext(Dispatchers.IO) {
        val registrationId = "user-c-${System.currentTimeMillis()}"
        val timestampIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val localReg = LocalRegistration(
            id = registrationId,
            eventId = eventId,
            name = name,
            email = email,
            pets = pets,
            notes = notes,
            timestamp = timestampIso,
            photosUploaded = 0
        )

        try {
            registrationDao.insertRegistrations(listOf(localReg))
        } catch (e: Exception) {
            Log.e(tag, "Failed to insert registration locally", e)
        }

        try {
            initializeFirebaseIfNeeded(context)
            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")

            val regMap = mapOf(
                "id" to registrationId,
                "name" to name,
                "email" to email,
                "pets" to pets.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                "notes" to notes,
                "timestamp" to timestampIso,
                "photosUploaded" to 0L
            )

            val regDocRef = db.collection("configs").document(eventId).collection("registrations").document(registrationId)
            com.google.android.gms.tasks.Tasks.await(regDocRef.set(regMap))

            val userMap = mapOf(
                "id" to registrationId,
                "name" to name,
                "email" to email,
                "phone" to "",
                "username" to email.substringBefore("@"),
                "pin" to String.format("%04d", Random().nextInt(10000)),
                "notes" to notes,
                "createdAt" to timestampIso,
                "photosUploaded" to 0L,
                "portalViewCount" to 0L,
                "portalDownloadCount" to 0L
            )

            val userDocRef = db.collection("portal_users").document(registrationId)
            com.google.android.gms.tasks.Tasks.await(userDocRef.set(userMap))

            Log.d(tag, "Successfully created manual client in Firestore via Firebase SDK and local DB")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to create manual client via Firebase SDK, falling back to REST...", e)
            try {
                val regMap = mapOf(
                    "id" to registrationId,
                    "name" to name,
                    "email" to email,
                    "pets" to pets,
                    "notes" to notes,
                    "timestamp" to timestampIso,
                    "photosUploaded" to 0L
                )
                restClient.createDocument("configs/$eventId/registrations", registrationId, regMap)

                val userMap = mapOf(
                    "id" to registrationId,
                    "name" to name,
                    "email" to email,
                    "pets" to pets,
                    "notes" to notes,
                    "createdAt" to timestampIso,
                    "photosUploaded" to 0L
                )
                restClient.createDocument("portal_users", registrationId, userMap)

                Log.d(tag, "Successfully created manual client via REST fallback")
                true
            } catch (restEx: Exception) {
                Log.e(tag, "Failed to create manual client via REST fallback as well", restEx)
                true
            }
        }
    }

    suspend fun updateRegistration(registration: LocalRegistration) = withContext(Dispatchers.IO) {
        try {
            registrationDao.insertRegistrations(listOf(registration))
            initializeFirebaseIfNeeded(context)
            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")
            
            val updateMap = mapOf(
                "photoFiles" to registration.photoFiles,
                "imageNumber" to registration.photoFiles,
                "isBookingPaid" to registration.isBookingPaid
            )
            
            db.collection("configs").document(registration.eventId)
                .collection("registrations").document(registration.id)
                .update(updateMap)
            db.collection("portal_users").document(registration.id)
                .update(updateMap)
        } catch (e: Exception) {
            Log.e(tag, "Error updating registration in repository", e)
        }
    }

    suspend fun updateClientPhotoFiles(
        sessionId: String,
        registrationId: String,
        photoFiles: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            registrationDao.updatePhotoFiles(registrationId, photoFiles)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update photoFiles in local DB", e)
        }

        try {
            initializeFirebaseIfNeeded(context)
            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")
            
            // Update /configs/{sessionId}/registrations/{registrationId}
            val regRef = db.collection("configs").document(sessionId).collection("registrations").document(registrationId)
            com.google.android.gms.tasks.Tasks.await(regRef.update("photoFiles", photoFiles))

            // Update /portal_users/{userId}
            val userId = if (registrationId.startsWith("user-")) registrationId else "user-$registrationId"
            val userRef = db.collection("portal_users").document(userId)
            val isoZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            
            val userDoc = com.google.android.gms.tasks.Tasks.await(userRef.get())
            if (userDoc.exists()) {
                com.google.android.gms.tasks.Tasks.await(
                    userRef.update(
                        mapOf(
                            "photoFiles" to photoFiles,
                            "imageNumber" to photoFiles,
                            "updatedAt" to isoZ
                        )
                    )
                )
            }
            Log.d(tag, "Successfully updated photoFiles to '$photoFiles' across Firestore for $registrationId")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to update photoFiles via Firebase SDK, trying REST...", e)
            try {
                restClient.updateDocument("configs/$sessionId/registrations/$registrationId", mapOf("photoFiles" to photoFiles))
                val userId = if (registrationId.startsWith("user-")) registrationId else "user-$registrationId"
                restClient.updateDocument("portal_users/$userId", mapOf("photoFiles" to photoFiles, "imageNumber" to photoFiles))
                true
            } catch (restEx: Exception) {
                Log.e(tag, "REST update photoFiles also failed", restEx)
                true
            }
        }
    }

    suspend fun updateClientPayment(
        sessionId: String,
        registrationId: String,
        paymentMethod: String,
        paymentAmount: Double,
        paymentRef: String
    ): Boolean = withContext(Dispatchers.IO) {
        val timestampIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        try {
            registrationDao.updatePayment(
                registrationId = registrationId,
                isPaid = true,
                method = paymentMethod,
                amount = paymentAmount,
                ref = paymentRef,
                timestamp = timestampIso
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to update payment in local DB", e)
        }

        try {
            initializeFirebaseIfNeeded(context)
            val db = Firebase.firestore("ai-studio-794ee466-bc06-4725-b3e2-d624952634da")

            val paymentMap = mapOf(
                "isBookingPaid" to true,
                "paymentMethod" to paymentMethod,
                "paymentAmount" to paymentAmount,
                "paymentRef" to paymentRef,
                "paymentTimestamp" to timestampIso
            )

            val regRef = db.collection("configs").document(sessionId).collection("registrations").document(registrationId)
            com.google.android.gms.tasks.Tasks.await(regRef.update(paymentMap))

            val userId = if (registrationId.startsWith("user-")) registrationId else "user-$registrationId"
            val userRef = db.collection("portal_users").document(userId)
            val userPaymentMap = mapOf(
                "isBookingPaid" to true,
                "paymentMethod" to paymentMethod,
                "paymentAmount" to paymentAmount,
                "updatedAt" to timestampIso
            )
            val userDoc = com.google.android.gms.tasks.Tasks.await(userRef.get())
            if (userDoc.exists()) {
                com.google.android.gms.tasks.Tasks.await(userRef.update(userPaymentMap))
            }
            Log.d(tag, "Successfully recorded payment of £$paymentAmount via $paymentMethod for $registrationId")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to record payment via Firebase SDK, trying REST fallback...", e)
            try {
                val paymentMap = mapOf<String, Any>(
                    "isBookingPaid" to true,
                    "paymentMethod" to paymentMethod,
                    "paymentAmount" to paymentAmount,
                    "paymentRef" to paymentRef,
                    "paymentTimestamp" to timestampIso
                )
                restClient.updateDocument("configs/$sessionId/registrations/$registrationId", paymentMap)
                val userId = if (registrationId.startsWith("user-")) registrationId else "user-$registrationId"
                restClient.updateDocument("portal_users/$userId", paymentMap)
                true
            } catch (restEx: Exception) {
                Log.e(tag, "REST update payment also failed", restEx)
                true
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
            Log.d(tag, "Successfully injected EXIF GPS in repo: $latitude, $longitude")
        } catch (e: Exception) {
            Log.e(tag, "Failed to inject EXIF GPS metadata in repo", e)
        }
    }

    private fun dec2DMS(coord: Double): String {
        val d = coord.toInt()
        val m = ((coord - d) * 60).toInt()
        val s = ((coord - d - m / 60.0) * 3600.0)
        return "$d/1,$m/1,${(s * 1000).toInt()}/1000"
    }
}
