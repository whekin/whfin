package dev.whekin.whfin.data.drive

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriveBackupClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: DriveBackupClient
    private val requests = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        client = DriveBackupClient(endpoint = "http://127.0.0.1:${server.address.port}")
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        requests += exchange.requestMethod to (exchange.requestURI.toString())
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun listParsesFilesAndSendsBearerToken() {
        var authHeader: String? = null
        server.createContext("/drive/v3/files") { exchange ->
            authHeader = exchange.requestHeaders.getFirst("Authorization")
            respond(
                exchange, 200,
                """{"files":[
                    {"id":"a","name":"whfin-backup-1.whfinbackup","createdTime":"2026-07-15T10:00:00Z","size":"123"},
                    {"id":"b","name":"whfin-backup-0.whfinbackup"}
                ]}""",
            )
        }

        val files = client.list("token-1")

        assertEquals("Bearer token-1", authHeader)
        assertEquals(listOf("a", "b"), files.map { it.id })
        assertEquals(123L, files.first().sizeBytes)
        assertEquals("2026-07-15T10:00:00Z", files.first().createdAt.toString())
    }

    @Test
    fun uploadSendsMultipartAndReturnsId() {
        var contentType: String? = null
        var body: ByteArray? = null
        server.createContext("/upload/drive/v3/files") { exchange ->
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            body = exchange.requestBody.readBytes()
            respond(exchange, 200, """{"id":"new-file"}""")
        }

        val id = client.upload("token", "backup.whfinbackup", byteArrayOf(1, 2, 3))

        assertEquals("new-file", id)
        assertTrue(contentType!!.startsWith("multipart/related; boundary="))
        val text = String(body!!, Charsets.ISO_8859_1)
        assertTrue(text.contains("\"appDataFolder\""))
        assertTrue(text.contains("\"backup.whfinbackup\""))
    }

    @Test
    fun httpErrorSurfacesStatusWithoutBody() {
        server.createContext("/drive/v3/files") { exchange ->
            respond(exchange, 403, """{"error":{"message":"secret detail"}}""")
        }

        val error = assertThrows(DriveBackupException::class.java) { client.list("token") }
        assertEquals(403, error.statusCode)
        assertTrue(error.isAuthError)
        assertEquals(false, error.message!!.contains("secret"))
    }

    @Test
    fun downloadReturnsRawBytes() {
        server.createContext("/drive/v3/files/abc") { exchange ->
            respond(exchange, 200, "ENVELOPE")
        }

        assertEquals("ENVELOPE", String(client.download("token", "abc")))
    }
}
