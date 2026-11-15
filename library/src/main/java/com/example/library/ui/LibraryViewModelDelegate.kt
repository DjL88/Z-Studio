package com.example.library.ui

import android.net.Uri
import com.example.library.data.LocalProject
import com.example.library.data.LocalPhoto

interface LibraryViewModelDelegate {
    fun importManualImage(uri: Uri)
    fun selectProject(projectId: String?)
    fun deleteProject(project: LocalProject)
    fun selectPhoto(photo: LocalPhoto)
    fun deletePhoto(photo: LocalPhoto)
    fun deletePhotos(photos: List<LocalPhoto>)
    fun publishPhotos(photos: List<LocalPhoto>, onComplete: (() -> Unit)? = null)
    fun unpublishPhoto(photo: LocalPhoto)
    fun unpublishPhotos(photos: List<LocalPhoto>)
    fun editPhotoWithLibraryCheck(photo: LocalPhoto, onNavigateToEdit: () -> Unit)
    fun assignPhotoToProject(photoId: String, projectId: String?)
    fun createProject(name: String, description: String)
    fun syncPublishedPhotos()
    fun syncPublishedPhotos(eventId: String?, userId: String?)
}
