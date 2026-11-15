package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testFirestoreRestFetch() {
    println("--- STARTING FIRESTORE REST DIAGNOSTIC TEST ---")
    var saFile = java.io.File("app/src/main/assets/service_account.json")
    if (!saFile.exists()) {
        saFile = java.io.File("src/main/assets/service_account.json")
    }
    if (!saFile.exists()) {
        println("ERROR: service_account.json not found at ${saFile.absolutePath}")
        return
    }
    val configText = saFile.readText()
    val projectId = configText.substringAfter("\"project_id\": \"").substringBefore("\"")
    val clientEmail = configText.substringAfter("\"client_email\": \"").substringBefore("\"")
    val privateKey = configText.substringAfter("\"private_key\": \"").substringBefore("\"")
    println("Project ID: $projectId")
    println("Client Email: $clientEmail")

    // Generate OAuth Token
    val now = System.currentTimeMillis()
    val header = """{"alg":"RS256","typ":"JWT"}"""
    
    val nowSec = now / 1000
    val claims = """{"iss":"$clientEmail","scope":"https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/devstorage.full_control","aud":"https://oauth2.googleapis.com/token","exp":${nowSec + 3600},"iat":$nowSec}"""
    
    val headerBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
    val claimsBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())
    val signatureInput = "$headerBase64.$claimsBase64"
    
    val privateKeyPem = privateKey
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\\n", "")
        .replace("\\s".toRegex(), "")
    val privateKeyBytes = java.util.Base64.getDecoder().decode(privateKeyPem)
    val keySpec = java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
    val keyFactory = java.security.KeyFactory.getInstance("RSA")
    val rsaPrivateKey = keyFactory.generatePrivate(keySpec)
    
    val signer = java.security.Signature.getInstance("SHA256withRSA")
    signer.initSign(rsaPrivateKey)
    signer.update(signatureInput.toByteArray())
    val signatureBytes = signer.sign()
    val signatureBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
    
    val jwt = "$signatureInput.$signatureBase64"
    
    val tokenUrl = java.net.URL("https://oauth2.googleapis.com/token")
    val conn = tokenUrl.openConnection() as java.net.HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    
    val postData = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
    java.io.OutputStreamWriter(conn.outputStream).use { writer ->
        writer.write(postData)
        writer.flush()
    }
    
    val code = conn.responseCode
    println("OAuth exchange response code: $code")
    if (code != 200) {
        val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
        println("OAuth ERROR details: $errText")
        fail("OAuth exchange failed: $errText")
    }

    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
    val token = responseText.substringAfter("\"access_token\": \"").substringBefore("\"")
    println("Successfully fetched OAuth Access Token!")

    // Fetch configs collection from Firestore
    val firestoreUrl = "https://firestore.googleapis.com/v1/projects/\$projectId/databases/(default)/documents/configs"
    val fsConn = java.net.URL(firestoreUrl).openConnection() as java.net.HttpURLConnection
    fsConn.requestMethod = "GET"
    fsConn.setRequestProperty("Authorization", "Bearer \$token")
    fsConn.setRequestProperty("Accept", "application/json")

    val fsCode = fsConn.responseCode
    println("Firestore /configs fetch response code: \$fsCode")
    if (fsCode != 200) {
        val errText = fsConn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
        println("Firestore /configs fetch ERROR details: \$errText")
    } else {
        val fsResponse = fsConn.inputStream.bufferedReader().use { it.readText() }
        println("Firestore /configs RESPONSE: \$fsResponse")
    }

    // Fetch portal_events collection from Firestore
    val eventsUrl = "https://firestore.googleapis.com/v1/projects/\$projectId/databases/(default)/documents/portal_events"
    val evConn = java.net.URL(eventsUrl).openConnection() as java.net.HttpURLConnection
    evConn.requestMethod = "GET"
    evConn.setRequestProperty("Authorization", "Bearer \$token")
    evConn.setRequestProperty("Accept", "application/json")

    val evCode = evConn.responseCode
    println("Firestore /portal_events fetch response code: \$evCode")
    if (evCode != 200) {
        val errText = evConn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
        println("Firestore /portal_events fetch ERROR details: \$errText")
    } else {
        val evResponse = evConn.inputStream.bufferedReader().use { it.readText() }
        println("Firestore /portal_events RESPONSE: \$evResponse")
    }
    println("--- END FIRESTORE REST DIAGNOSTIC TEST ---")
  }
}
