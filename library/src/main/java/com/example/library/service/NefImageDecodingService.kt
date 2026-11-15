package com.example.library.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File

object NefImageDecodingService {
    private const val TAG = "NefDecodingService"

    fun isRawFile(pathOrUri: String): Boolean {
        val lower = pathOrUri.lowercase()
        return lower.endsWith(".nef") || lower.endsWith(".raw") || lower.endsWith(".cr2") ||
               lower.endsWith(".cr3") || lower.endsWith(".arw") || lower.endsWith(".dng") ||
               lower.endsWith(".raf") || lower.endsWith(".orf") || lower.endsWith(".rw2") ||
               lower.endsWith(".pef")
    }

    /**
     * Decodes a high-resolution embedded preview from a local RAW/NEF file path.
     * Uses MediaMetadataRetriever to extract embedded picture bytes or the primary image,
     * and decodes using native BitmapFactory with optimal performance.
     */
    fun decodeNefPreview(context: Context, filePath: String): Bitmap? {
        if (!isRawFile(filePath)) return null

        Log.d(TAG, "Attempting high-resolution embedded preview extraction for file: $filePath")

        // 1. Try MediaMetadataRetriever to extract high-resolution embedded JPEG bytes
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)

            val embeddedPic = retriever.embeddedPicture
            if (embeddedPic != null) {
                val options = BitmapFactory.Options().apply {
                    inMutable = true
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size, options)
                if (bitmap != null) {
                    Log.i(TAG, "Successfully extracted high-res embedded preview [${bitmap.width}x${bitmap.height}] via MediaMetadataRetriever.embeddedPicture from $filePath")
                    retriever.release()
                    return bitmap
                }
            }

            // 2. Try getPrimaryImage() if available (Android 9+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val primaryImage = retriever.primaryImage
                if (primaryImage != null) {
                    Log.i(TAG, "Successfully retrieved primary image [${primaryImage.width}x${primaryImage.height}] via MediaMetadataRetriever.primaryImage from $filePath")
                    retriever.release()
                    return primaryImage
                }
            }
            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever preview extraction failed for $filePath: ${e.message}")
        }

        // 3. Fallback to ExifInterface thumbnail extraction
        try {
            val exif = android.media.ExifInterface(filePath)
            if (exif.hasThumbnail()) {
                val thumbBytes = exif.thumbnail
                if (thumbBytes != null) {
                    val options = BitmapFactory.Options().apply {
                        inMutable = true
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size, options)
                    if (bitmap != null) {
                        Log.i(TAG, "Successfully extracted ExifInterface thumbnail [${bitmap.width}x${bitmap.height}] from $filePath")
                        return bitmap
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ExifInterface thumbnail fallback failed for $filePath", e)
        }

        // 4. Ultimate slow fallback: ImageDecoder (Android P+) or BitmapFactory.decodeFile
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val source = android.graphics.ImageDecoder.createSource(file)
                    val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                    Log.i(TAG, "Decoded full RAW using ImageDecoder [${bitmap.width}x${bitmap.height}] from $filePath")
                    return bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "ImageDecoder ultimate fallback failed for $filePath", e)
            }
        }

        return try {
            val options = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(filePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "BitmapFactory.decodeFile ultimate fallback failed for $filePath", e)
            null
        }
    }

    /**
     * Decodes a high-resolution embedded preview from a RAW/NEF Content Uri.
     * Uses MediaMetadataRetriever and native BitmapFactory.
     */
    fun decodeNefPreviewFromUri(context: Context, uri: Uri): Bitmap? {
        val path = uri.path ?: ""
        if (!isRawFile(path) && !isRawFile(uri.toString())) return null

        Log.d(TAG, "Attempting high-resolution embedded preview extraction for Uri: $uri")

        // 1. Try MediaMetadataRetriever to extract high-resolution embedded JPEG bytes from Uri
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val embeddedPic = retriever.embeddedPicture
            if (embeddedPic != null) {
                val options = BitmapFactory.Options().apply {
                    inMutable = true
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size, options)
                if (bitmap != null) {
                    Log.i(TAG, "Successfully extracted high-res embedded preview [${bitmap.width}x${bitmap.height}] via MediaMetadataRetriever.embeddedPicture from Uri $uri")
                    retriever.release()
                    return bitmap
                }
            }

            // 2. Try getPrimaryImage() if available (Android 9+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val primaryImage = retriever.primaryImage
                if (primaryImage != null) {
                    Log.i(TAG, "Successfully retrieved primary image [${primaryImage.width}x${primaryImage.height}] via MediaMetadataRetriever.primaryImage from Uri $uri")
                    retriever.release()
                    return primaryImage
                }
            }
            retriever.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever preview extraction failed for Uri $uri: ${e.message}")
        }

        // 3. Fallback to ExifInterface thumbnail extraction
        try {
            val tempFile = File(context.cacheDir, "temp_decoding_exif_${System.currentTimeMillis()}.nef")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val exif = android.media.ExifInterface(tempFile.absolutePath)
            val thumbBytes = exif.thumbnail
            tempFile.delete()

            if (thumbBytes != null) {
                val options = BitmapFactory.Options().apply {
                    inMutable = true
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size, options)
                if (bitmap != null) {
                    Log.i(TAG, "Successfully extracted ExifInterface thumbnail [${bitmap.width}x${bitmap.height}] from Uri $uri")
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ExifInterface thumbnail fallback failed for Uri $uri", e)
        }

        // 4. Ultimate slow fallback: ImageDecoder (Android P+) or BitmapFactory decoding
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
                Log.i(TAG, "Decoded full RAW using ImageDecoder [${bitmap.width}x${bitmap.height}] from Uri $uri")
                return bitmap
            } catch (e: Exception) {
                Log.e(TAG, "ImageDecoder ultimate fallback failed for Uri $uri", e)
            }
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "BitmapFactory.decodeStream ultimate fallback failed for Uri $uri", e)
            null
        }
    }
}
