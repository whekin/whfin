package dev.whekin.whfin.data.statement

import java.io.ByteArrayInputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class StatementFileTest {
    @Test
    fun `rejects an input larger than the statement buffer limit`() {
        val oversized = ByteArray(32 * 1024 * 1024 + 1)

        assertThrows(MalformedStatementException::class.java) {
            StatementFile.read(ByteArrayInputStream(oversized), "statement.xlsx")
        }
    }
}
