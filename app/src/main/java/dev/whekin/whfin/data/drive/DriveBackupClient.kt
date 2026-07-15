package dev.whekin.whfin.data.drive

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

data class DriveBackupFile(
    val id: String,
    val name: String,
    val createdAt: Instant?,
    val sizeBytes: Long?,
)

class DriveBackupException(message: String, val statusCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause) {
    val isAuthError: Boolean get() = statusCode == 401 || statusCode == 403
}

/**
 * Тонкий REST-клиент Drive v3 поверх HttpURLConnection: только скрытая appDataFolder,
 * никакого Drive SDK. Токен короткоживущий и приходит от Identity AuthorizationClient.
 */
class DriveBackupClient(
    private val endpoint: String = "https://www.googleapis.com",
) {
    fun list(accessToken: String): List<DriveBackupFile> {
        val query = "spaces=appDataFolder" +
            "&fields=" + URLEncoder.encode("files(id,name,createdTime,size)", "UTF-8") +
            "&orderBy=" + URLEncoder.encode("createdTime desc", "UTF-8") +
            "&pageSize=100"
        val response = request("GET", "$endpoint/drive/v3/files?$query", accessToken)
        val files = JSONObject(response).optJSONArray("files") ?: JSONArray()
        return (0 until files.length()).map { index ->
            val file = files.getJSONObject(index)
            DriveBackupFile(
                id = file.getString("id"),
                name = file.optString("name", ""),
                createdAt = file.optString("createdTime", "")
                    .takeIf(String::isNotEmpty)
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                sizeBytes = file.optString("size", "").toLongOrNull(),
            )
        }
    }

    fun upload(accessToken: String, name: String, content: ByteArray): String {
        val boundary = "whfin-" + System.nanoTime()
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put("appDataFolder"))
            .toString()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\n".toByteArray())
            write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            write(metadata.toByteArray(Charsets.UTF_8))
            write("\r\n--$boundary\r\n".toByteArray())
            write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            write(content)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val response = request(
            "POST",
            "$endpoint/upload/drive/v3/files?uploadType=multipart&fields=id",
            accessToken,
            body = body,
            contentType = "multipart/related; boundary=$boundary",
        )
        return JSONObject(response).optString("id", "")
            .takeIf(String::isNotEmpty)
            ?: throw DriveBackupException("Drive did not return an id for the uploaded backup.")
    }

    fun download(accessToken: String, fileId: String): ByteArray =
        requestBytes("GET", "$endpoint/drive/v3/files/$fileId?alt=media", accessToken)

    fun delete(accessToken: String, fileId: String) {
        request("DELETE", "$endpoint/drive/v3/files/$fileId", accessToken)
    }

    private fun request(
        method: String,
        url: String,
        accessToken: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): String = requestBytes(method, url, accessToken, body, contentType).toString(Charsets.UTF_8)

    private fun requestBytes(
        method: String,
        url: String,
        accessToken: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                doOutput = true
                contentType?.let { setRequestProperty("Content-Type", it) }
            }
        }
        try {
            body?.let { connection.outputStream.use { output -> output.write(it) } }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Тело ошибки не включается в сообщение: там может быть чувствительный контекст.
                connection.errorStream?.use(InputStream::readBytes)
                throw DriveBackupException("Google Drive request failed (HTTP $status).", statusCode = status)
            }
            return connection.inputStream.use(InputStream::readBytes)
        } catch (error: DriveBackupException) {
            throw error
        } catch (error: Exception) {
            throw DriveBackupException("Could not reach Google Drive.", cause = error)
        } finally {
            connection.disconnect()
        }
    }
}
