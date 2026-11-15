package com.example.publishing.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ExportManager(private val context: Context) {
    private val tag = "ExportManager"

    /**
     * Exports a photo to a user-selected SAF directory. If folderUriString is null or empty,
     * falls back to saving to the public Pictures gallery directory in a subfolder.
     * Returns the Uri of the saved file or null if failed.
     */
    fun exportPhoto(
        bitmap: Bitmap,
        fileName: String,
        folderUriString: String?,
        gpsLatitude: Double = 0.0,
        gpsLongitude: Double = 0.0
    ): Uri? {
        val finalFileName = if (fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true)) {
            fileName
        } else {
            "$fileName.jpg"
        }

        if (!folderUriString.isNullOrEmpty()) {
            try {
                val treeUri = Uri.parse(folderUriString)
                val parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId)
                
                val fileUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocumentUri,
                    "image/jpeg",
                    finalFileName
                )
                
                if (fileUri != null) {
                    val tempFile = File(context.cacheDir, "temp_export_${UUID.randomUUID()}.jpg")
                    FileOutputStream(tempFile).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                        fos.flush()
                    }

                    writeGpsToExif(tempFile.absolutePath, gpsLatitude, gpsLongitude)

                    context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    tempFile.delete()
                    Log.d(tag, "Successfully exported photo locally via SAF to: $fileUri")
                    return fileUri
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to write to SAF export folder", e)
            }
        }

        // Fallback to pictures gallery using MediaStore (scoped storage safe)
        return saveToGalleryUsingMediaStore(bitmap, finalFileName, gpsLatitude, gpsLongitude)
    }

    /**
     * Saves an image directly to the system gallery using the modern MediaStore API.
     */
    private fun saveToGalleryUsingMediaStore(
        bitmap: Bitmap,
        finalFileName: String,
        gpsLatitude: Double,
        gpsLongitude: Double
    ): Uri? {
        val resolver = context.contentResolver
        val imageCollection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, finalFileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TetherStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(imageCollection, contentValues) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            if (gpsLatitude != 0.0 || gpsLongitude != 0.0) {
                try {
                    resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        val latRef = if (gpsLatitude >= 0) "N" else "S"
                        val latDMS = dec2DMS(Math.abs(gpsLatitude))
                        val lonRef = if (gpsLongitude >= 0) "E" else "W"
                        val lonDMS = dec2DMS(Math.abs(gpsLongitude))
                        
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latDMS)
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lonDMS)
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)
                        exif.saveAttributes()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to write EXIF on MediaStore Uri", e)
                }
            }

            Log.d(tag, "Successfully exported photo via MediaStore to: $uri")
            return uri
        } catch (e: Exception) {
            Log.e(tag, "Failed to save via MediaStore", e)
            resolver.delete(uri, null, null)
        }
        return null
    }

    /**
     * Shares single or multiple images using the share sheet intent via FileProvider.
     */
    fun sharePhotos(uris: List<Uri>, chooserTitle: String = "Share Exported Photo") {
        if (uris.isEmpty()) return
        
        try {
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/jpeg"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            
            val chooserIntent = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch share sheet", e)
        }
    }

    /**
     * Helper to write GPS metadata to EXIF of a file path.
     */
    private fun writeGpsToExif(filePath: String, latitude: Double, longitude: Double) {
        if (latitude == 0.0 && longitude == 0.0) return
        try {
            val exif = ExifInterface(filePath)
            
            val latRef = if (latitude >= 0) "N" else "S"
            val latDMS = dec2DMS(Math.abs(latitude))
            
            val lonRef = if (longitude >= 0) "E" else "W"
            val lonDMS = dec2DMS(Math.abs(longitude))
            
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latDMS)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lonDMS)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)
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
}
