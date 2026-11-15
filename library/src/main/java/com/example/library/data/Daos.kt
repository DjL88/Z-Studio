package com.example.library.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM local_photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<LocalPhoto>>

    @Query("SELECT * FROM local_photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: String): LocalPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePhoto(photo: LocalPhoto)

    @Delete
    suspend fun deletePhoto(photo: LocalPhoto)

    @Query("DELETE FROM local_photos")
    suspend fun clearAllPhotos()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM local_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<LocalSettings?>

    @Query("SELECT * FROM local_settings WHERE id = 1")
    suspend fun getSettings(): LocalSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: LocalSettings)
}

@Dao
interface RegistrationDao {
    @Query("SELECT * FROM local_registrations WHERE eventId = :eventId ORDER BY timestamp DESC")
    fun getRegistrationsForEvent(eventId: String): Flow<List<LocalRegistration>>

    @Query("SELECT * FROM local_registrations ORDER BY timestamp DESC")
    fun getAllRegistrations(): Flow<List<LocalRegistration>>

    @Query("SELECT * FROM local_registrations ORDER BY timestamp DESC")
    suspend fun getAllRegistrationsList(): List<LocalRegistration>

    @Query("SELECT * FROM local_registrations WHERE id = :registrationId")
    suspend fun getRegistrationById(registrationId: String): LocalRegistration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegistrations(registrations: List<LocalRegistration>)

    @Query("UPDATE local_registrations SET photosUploaded = photosUploaded + 1 WHERE id = :registrationId")
    suspend fun incrementPhotoCount(registrationId: String)

    @Query("UPDATE local_registrations SET photoFiles = :photoFiles WHERE id = :registrationId")
    suspend fun updatePhotoFiles(registrationId: String, photoFiles: String)

    @Query("UPDATE local_registrations SET isBookingPaid = :isPaid, paymentMethod = :method, paymentAmount = :amount, paymentRef = :ref, paymentTimestamp = :timestamp WHERE id = :registrationId")
    suspend fun updatePayment(
        registrationId: String,
        isPaid: Boolean,
        method: String,
        amount: Double,
        ref: String,
        timestamp: String
    )

    @Query("DELETE FROM local_registrations WHERE eventId = :eventId")
    suspend fun clearRegistrationsForEvent(eventId: String)
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM local_configs")
    fun getAllConfigs(): Flow<List<LocalConfig>>

    @Query("SELECT * FROM local_configs WHERE id = :eventId")
    suspend fun getConfigById(eventId: String): LocalConfig?

    @Query("SELECT * FROM local_configs LIMIT 1")
    suspend fun getFirstConfig(): LocalConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<LocalConfig>)

    @Query("DELETE FROM local_configs")
    suspend fun clearAllConfigs()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM local_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<LocalProject>>

    @Query("SELECT * FROM local_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): LocalProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProject(project: LocalProject)

    @Delete
    suspend fun deleteProject(project: LocalProject)
}
