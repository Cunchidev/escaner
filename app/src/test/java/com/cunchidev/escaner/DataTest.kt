package com.cunchidev.escaner

import com.cunchidev.escaner.data.FileNames
import com.cunchidev.escaner.data.FtsQuery
import com.cunchidev.escaner.data.JpegPdfWriter
import com.cunchidev.escaner.data.PageList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class DataTest {

    @Test
    fun `a lista de páxinas conserva a orde e ignora ocos`() {
        assertEquals(listOf("a.jpg", "b.jpg"), PageList.parse("a.jpg|b.jpg"))
        assertEquals(emptyList<String>(), PageList.parse(""))
        assertEquals("a.jpg|b.jpg", PageList.join(listOf("a.jpg", "b.jpg")))
    }

    @Test
    fun `mover e borrar páxinas`() {
        val p = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), PageList.move(p, 0, 1))
        assertEquals(listOf("c", "a", "b"), PageList.move(p, 2, 0))
        assertEquals(p, PageList.move(p, 0, 3))
        assertEquals(listOf("a", "c"), PageList.removeAt(p, 1))
        assertEquals(p, PageList.removeAt(p, 7))
    }

    @Test
    fun `a busca son prefixos de todas as palabras e non deixa pasar sintaxe FTS`() {
        assertEquals("\"factura\"* \"luz\"*", FtsQuery.build("factura luz"))
        assertEquals("\"a\"* \"OR\"* \"b\"*", FtsQuery.build("a\" OR (b*"))
        assertEquals("\"señor\"*", FtsQuery.build("  señor  "))
        assertNull(FtsQuery.build("  \"* "))
    }

    @Test
    fun `os nomes de ficheiro quedan sen caracteres prohibidos`() {
        assertEquals("Factura 10_2026", FileNames.safe("Factura 10/2026"))
        assertEquals("documento", FileNames.safe("  "))
        assertEquals("a_b", FileNames.safe("a:b."))
    }

    /** Un JPG mínimo: só as cabeceiras que le o escritor (SOI, APP0, SOF0, EOI). */
    private fun jpegFalso(ancho: Int, alto: Int, compoñentes: Int = 3): ByteArray {
        val b = ByteArrayOutputStream()
        fun w(vararg v: Int) = v.forEach { b.write(it) }
        w(0xFF, 0xD8)
        w(0xFF, 0xE0, 0x00, 0x04, 0x4A, 0x46) // APP0 de 4 bytes
        w(0xFF, 0xC0, 0x00, 0x0B, 0x08, alto shr 8, alto and 0xFF, ancho shr 8, ancho and 0xFF, compoñentes, 0, 0, 0)
        w(0xFF, 0xD9)
        return b.toByteArray()
    }

    @Test
    fun `le o tamaño do JPG saltando os outros segmentos`() {
        assertEquals(JpegPdfWriter.JpegInfo(1240, 1754, 3), JpegPdfWriter.readInfo(jpegFalso(1240, 1754)))
        assertEquals(1, JpegPdfWriter.readInfo(jpegFalso(10, 20, 1))?.components)
        assertNull(JpegPdfWriter.readInfo("non son un jpg".toByteArray()))
    }

    @Test
    fun `o PDF ten unha páxina por JPG e a táboa xref apunta a cada obxecto`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "escaner-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val vertical = File(dir, "1.jpg").apply { writeBytes(jpegFalso(1240, 1754)) }
            val apaisada = File(dir, "2.jpg").apply { writeBytes(jpegFalso(2000, 1000)) }
            val rota = File(dir, "3.jpg").apply { writeText("lixo") }

            val saida = ByteArrayOutputStream()
            assertEquals(2, JpegPdfWriter.write(listOf(vertical, apaisada, rota), saida))
            val bytes = saida.toByteArray()
            val pdf = String(bytes, Charsets.ISO_8859_1)

            assertTrue(pdf.startsWith("%PDF-1.4"))
            assertTrue(pdf.trimEnd().endsWith("%%EOF"))
            assertTrue("/Count 2" in pdf)
            assertTrue("/MediaBox [0 0 595.00 842.00]" in pdf)
            assertTrue("/MediaBox [0 0 842.00 595.00]" in pdf)
            assertTrue("/Filter /DCTDecode" in pdf)

            // Cada entrada da xref ten que caer xusto no "N 0 obj" que di.
            val xref = pdf.substringAfterLast("startxref\n").substringBefore("\n").toInt()
            val linas = pdf.substring(xref).lines()
            assertEquals("xref", linas[0])
            val total = linas[1].substringAfter("0 ").toInt()
            assertEquals(9, total) // libre + catálogo + árbore + 2 × (páxina, contido, imaxe)
            for (id in 1 until total) {
                val offset = linas[2 + id].substring(0, 10).toInt()
                assertTrue("obxecto $id", pdf.startsWith("$id 0 obj", offset))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
