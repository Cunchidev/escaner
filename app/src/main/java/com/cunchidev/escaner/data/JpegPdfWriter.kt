package com.cunchidev.escaner.data

import java.io.File
import java.io.OutputStream
import java.util.Locale

/**
 * Escribe un PDF cunha páxina por JPG, metendo cada JPG tal cal (filtro DCTDecode): sen
 * recomprimir, sen perder calidade e sen cargar as imaxes en memoria como bitmaps. Por iso
 * non se usa `android.graphics.pdf.PdfDocument`, que garda os píxeles sen comprimir e fai
 * PDFs enormes. Kotlin puro: pódese probar na JVM.
 */
object JpegPdfWriter {

    data class JpegInfo(val width: Int, val height: Int, val components: Int)

    private const val A4_CURTO = 595.0
    private const val A4_LONGO = 842.0

    /** Le ancho, alto e nº de compoñentes da cabeceira SOF do JPG; null se non é un JPG válido. */
    fun readInfo(bytes: ByteArray): JpegInfo? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var i = 2
        while (i + 3 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) { i++; continue }
            val marker = bytes[i + 1].toInt() and 0xFF
            when {
                marker == 0xFF -> i++ // recheo
                marker == 0x01 || marker in 0xD0..0xD9 -> i += 2 // sen lonxitude
                else -> {
                    val len = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                    val sof = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
                    if (sof) {
                        if (i + 9 >= bytes.size) return null
                        val h = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                        val w = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
                        val c = bytes[i + 9].toInt() and 0xFF
                        return if (w > 0 && h > 0) JpegInfo(w, h, c) else null
                    }
                    i += 2 + len
                }
            }
        }
        return null
    }

    /** Escribe o PDF en [out]. Os ficheiros que non sexan JPG válidos sáltanse. Devolve as páxinas escritas. */
    fun write(jpegs: List<File>, out: OutputStream): Int {
        val w = Writer(out)
        w.raw("%PDF-1.4\n%âãÏÓ\n")

        val paxinas = jpegs.mapNotNull { f ->
            val bytes = runCatching { f.readBytes() }.getOrNull() ?: return@mapNotNull null
            readInfo(bytes)?.let { bytes to it }
        }
        // Obxectos: 1 catálogo, 2 árbore de páxinas, e por páxina: páxina, contido, imaxe.
        val idPaxina = { n: Int -> 3 + n * 3 }

        w.obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        w.obj(2, "<< /Type /Pages /Kids [${paxinas.indices.joinToString(" ") { "${idPaxina(it)} 0 R" }}] /Count ${paxinas.size} >>")

        paxinas.forEachIndexed { n, (bytes, info) ->
            val id = idPaxina(n)
            // A4 na orientación da imaxe, coa imaxe axustada dentro e centrada.
            val apaisada = info.width > info.height
            val pw = if (apaisada) A4_LONGO else A4_CURTO
            val ph = if (apaisada) A4_CURTO else A4_LONGO
            val escala = minOf(pw / info.width, ph / info.height)
            val iw = info.width * escala
            val ih = info.height * escala
            val x = (pw - iw) / 2
            val y = (ph - ih) / 2

            w.obj(
                id,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(pw)} ${num(ph)}] " +
                    "/Resources << /XObject << /Im0 ${id + 2} 0 R >> >> /Contents ${id + 1} 0 R >>"
            )
            val contido = "q ${num(iw)} 0 0 ${num(ih)} ${num(x)} ${num(y)} cm /Im0 Do Q".toByteArray(Charsets.US_ASCII)
            w.stream(id + 1, "<< /Length ${contido.size} >>", contido)

            val (cor, decode) = when (info.components) {
                1 -> "/DeviceGray" to ""
                // Os JPG CMYK de Adobe veñen invertidos.
                4 -> "/DeviceCMYK" to " /Decode [1 0 1 0 1 0 1 0]"
                else -> "/DeviceRGB" to ""
            }
            w.stream(
                id + 2,
                "<< /Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} " +
                    "/ColorSpace $cor /BitsPerComponent 8 /Filter /DCTDecode$decode /Length ${bytes.size} >>",
                bytes
            )
        }

        w.finish(rootId = 1)
        return paxinas.size
    }

    private fun num(v: Double): String = String.format(Locale.US, "%.2f", v)

    /** Leva a conta dos bytes escritos: a táboa xref necesita a posición de cada obxecto. */
    private class Writer(private val out: OutputStream) {
        private var pos = 0L
        private val offsets = sortedMapOf<Int, Long>()

        fun raw(text: String) = bytes(text.toByteArray(Charsets.ISO_8859_1))

        fun bytes(b: ByteArray) {
            out.write(b)
            pos += b.size
        }

        fun obj(id: Int, dict: String) {
            offsets[id] = pos
            raw("$id 0 obj\n$dict\nendobj\n")
        }

        fun stream(id: Int, dict: String, data: ByteArray) {
            offsets[id] = pos
            raw("$id 0 obj\n$dict\nstream\n")
            bytes(data)
            raw("\nendstream\nendobj\n")
        }

        fun finish(rootId: Int) {
            val xref = pos
            val total = (offsets.keys.maxOrNull() ?: 0) + 1
            raw("xref\n0 $total\n")
            raw("0000000000 65535 f \n")
            for (id in 1 until total) {
                raw(String.format(Locale.US, "%010d 00000 n \n", offsets[id] ?: 0L))
            }
            raw("trailer\n<< /Size $total /Root $rootId 0 R >>\nstartxref\n$xref\n%%EOF\n")
            out.flush()
        }
    }
}
