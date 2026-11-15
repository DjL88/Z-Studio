package com.example.library.data

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.*

data class FirestoreDocument(
    val id: String,
    val path: String,
    val fields: Map<String, Any?>
)

class FirestoreRestClient(private val context: Context) {
    private val tag = "FirestoreRestClient"
    
    private var projectId = "hi-domino-d0abb"
    private var clientEmail = ""
    private var privateKey = ""
    private var cachedToken: String? = null
    private var tokenExpiryTimeMs: Long = 0

    init {
        try {
            val configText = context.assets.open("service_account.json").bufferedReader().use { it.readText() }
            val json = JSONObject(configText)
            projectId = json.optString("project_id", "hi-domino-d0abb")
            clientEmail = json.optString("client_email", "")
            privateKey = json.optString("private_key", "")
            Log.d(tag, "Successfully loaded service account for project: $projectId")
        } catch (e: Exception) {
            Log.e(tag, "Failed to load service account credentials from assets", e)
        }
    }

    fun getProjectId(): String = projectId

    @Synchronized
    private fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryTimeMs - 60000) {
            return cachedToken!!
        }

        Log.d(tag, "Generating fresh Google OAuth2 access token...")
        
        val header = JSONObject().apply {
            put("alg", "RS256")
            put("typ", "JWT")
        }.toString()
        
        val nowSec = now / 1000
        val claims = JSONObject().apply {
            put("iss", clientEmail)
            put("scope", "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/devstorage.full_control")
            put("aud", "https://oauth2.googleapis.com/token")
            put("exp", nowSec + 3600)
            put("iat", nowSec)
        }.toString()
        
        val headerBase64 = Base64.encodeToString(header.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        val claimsBase64 = Base64.encodeToString(claims.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        val signatureInput = "$headerBase64.$claimsBase64"
        
        val privateKeyPem = privateKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val privateKeyBytes = Base64.decode(privateKeyPem, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val rsaPrivateKey = keyFactory.generatePrivate(keySpec)
        
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(rsaPrivateKey)
        signer.update(signatureInput.toByteArray())
        val signatureBytes = signer.sign()
        val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        
        val jwt = "$signatureInput.$signatureBase64"
        
        val url = URL("https://oauth2.googleapis.com/token")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        
        val postData = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(postData)
            writer.flush()
        }
        
        if (conn.responseCode != 200) {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            throw Exception("OAuth token exchange failed with code ${conn.responseCode}: $errText")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val responseJson = JSONObject(responseText)
        val token = responseJson.getString("access_token")
        val expiresIn = responseJson.optLong("expires_in", 3600)
        
        cachedToken = token
        tokenExpiryTimeMs = System.currentTimeMillis() + (expiresIn * 1000)
        Log.d(tag, "Successfully fetched fresh OAuth access token. Expires in $expiresIn seconds.")
        return token
    }

    private fun makeRequest(
        method: String,
        urlString: String,
        body: JSONObject? = null
    ): String {
        val token = getAccessToken()
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(body.toString())
                writer.flush()
            }
        }
        
        val code = conn.responseCode
        if (code in 200..299) {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details available"
            Log.e(tag, "HTTP request failed ($method $urlString) with code $code: $errText")
            throw Exception("HTTP $code: $errText")
        }
    }

    // --- Firestore Operations ---

    fun getDocument(documentPath: String): FirestoreDocument? {
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$documentPath"
        return try {
            val response = makeRequest("GET", url)
            parseDocumentJson(JSONObject(response))
        } catch (e: Exception) {
            if (e.message?.contains("HTTP 404") == true) {
                null
            } else {
                throw e
            }
        }
    }

    fun getDocuments(collectionPath: String): List<FirestoreDocument> {
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$collectionPath"
        return try {
            val response = makeRequest("GET", url)
            val json = JSONObject(response)
            val docsArray = json.optJSONArray("documents") ?: return emptyList()
            val list = mutableListOf<FirestoreDocument>()
            for (i in 0 until docsArray.length()) {
                val docObj = docsArray.getJSONObject(i)
                parseDocumentJson(docObj)?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            Log.e(tag, "Failed to get documents for collection $collectionPath", e)
            emptyList()
        }
    }

    fun createDocument(
        collectionPath: String,
        documentId: String,
        fields: Map<String, Any?>
    ): FirestoreDocument {
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$collectionPath?documentId=$documentId"
        val body = toFirestoreFields(fields)
        val response = makeRequest("POST", url, body)
        return parseDocumentJson(JSONObject(response)) ?: throw Exception("Failed to parse created document")
    }

    fun updateDocument(
        documentPath: String,
        fields: Map<String, Any?>
    ): FirestoreDocument {
        // Construct the update mask to only update passed fields
        val fieldPaths = fields.keys.joinToString("&") { "updateMask.fieldPaths=$it" }
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$documentPath?$fieldPaths"
        val body = toFirestoreFields(fields)
        val response = makeRequest("PATCH", url, body)
        return parseDocumentJson(JSONObject(response)) ?: throw Exception("Failed to parse updated document")
    }

    // --- Helper Parsing Utilities ---

    private fun parseDocumentJson(docObj: JSONObject): FirestoreDocument? {
        val name = docObj.optString("name") ?: return null
        val id = name.substringAfterLast("/")
        val path = name.substringAfter("documents/")
        val fieldsObj = docObj.optJSONObject("fields") ?: JSONObject()
        val fields = fromFirestoreFields(fieldsObj)
        return FirestoreDocument(id, path, fields)
    }

    private fun toFirestoreFields(map: Map<String, Any?>): JSONObject {
        val fields = JSONObject()
        for ((key, value) in map) {
            val valObj = JSONObject()
            when (value) {
                null -> continue
                is String -> valObj.put("stringValue", value)
                is Boolean -> valObj.put("booleanValue", value)
                is Long, is Int, is Short, is Byte -> valObj.put("integerValue", value.toString())
                is Number -> valObj.put("doubleValue", value.toDouble())
                is List<*> -> {
                    val arrayValue = JSONObject()
                    val valuesArray = JSONArray()
                    for (item in value) {
                        if (item == null) continue
                        val itemObj = JSONObject()
                        when (item) {
                            is String -> itemObj.put("stringValue", item)
                            is Boolean -> itemObj.put("booleanValue", item)
                            is Number -> itemObj.put("integerValue", item.toString())
                        }
                        valuesArray.put(itemObj)
                    }
                    arrayValue.put("values", valuesArray)
                    valObj.put("arrayValue", arrayValue)
                }
                is Map<*, *> -> {
                    val mapValue = JSONObject()
                    val subFields = toFirestoreFields(value as Map<String, Any?>)
                    mapValue.put("fields", subFields.optJSONObject("fields") ?: JSONObject())
                    valObj.put("mapValue", mapValue)
                }
            }
            fields.put(key, valObj)
        }
        return JSONObject().apply { put("fields", fields) }
    }

    private fun fromFirestoreFields(fieldsJson: JSONObject): Map<String, Any?> {
        val resMap = mutableMapOf<String, Any?>()
        val keys = fieldsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            resMap[key] = fromFirestoreValue(fieldsJson.getJSONObject(key))
        }
        return resMap
    }

    private fun fromFirestoreValue(valObj: JSONObject): Any? {
        return when {
            valObj.has("stringValue") -> valObj.getString("stringValue")
            valObj.has("booleanValue") -> valObj.getBoolean("booleanValue")
            valObj.has("integerValue") -> valObj.getString("integerValue").toLongOrNull() ?: valObj.getString("integerValue")
            valObj.has("doubleValue") -> valObj.getDouble("doubleValue")
            valObj.has("arrayValue") -> {
                val arr = valObj.getJSONObject("arrayValue")
                val vals = arr.optJSONArray("values") ?: JSONArray()
                val list = mutableListOf<Any?>()
                for (i in 0 until vals.length()) {
                    list.add(fromFirestoreValue(vals.getJSONObject(i)))
                }
                list
            }
            valObj.has("mapValue") -> {
                val mapObj = valObj.getJSONObject("mapValue")
                val fields = mapObj.optJSONObject("fields") ?: JSONObject()
                val resMap = mutableMapOf<String, Any?>()
                val keys = fields.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    resMap[k] = fromFirestoreValue(fields.getJSONObject(k))
                }
                resMap
            }
            else -> null
        }
    }

    // --- Storage Operations (Direct REST Upload) ---

    fun uploadToStorage(storagePath: String, data: ByteArray): String {
        val token = getAccessToken()
        val encodedPath = java.net.URLEncoder.encode(storagePath, "UTF-8")
        val bucketName = if (projectId == "hi-domino-d0abb") "hi-domino-d0abb.firebasestorage.app" else "$projectId.appspot.com"
        val urlString = "https://firebasestorage.googleapis.com/v0/b/$bucketName/o?uploadType=media&name=$encodedPath"
        
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "image/jpeg")
        conn.setRequestProperty("Accept", "application/json")
        
        conn.outputStream.use { out ->
            out.write(data)
            out.flush()
        }
        
        val code = conn.responseCode
        if (code in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val responseJson = JSONObject(responseText)
            val rawToken = responseJson.optString("downloadTokens", "")
            val downloadToken = if (rawToken.contains(",")) rawToken.split(",").firstOrNull() ?: "" else rawToken
            val tokenParam = if (downloadToken.isNotBlank()) "&token=$downloadToken" else ""
            
            return "https://firebasestorage.googleapis.com/v0/b/$bucketName/o/$encodedPath?alt=media$tokenParam"
        } else {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Storage upload error"
            Log.e(tag, "Storage upload failed with code $code: $errText")
            throw Exception("Storage upload HTTP $code: $errText")
        }
    }
}
