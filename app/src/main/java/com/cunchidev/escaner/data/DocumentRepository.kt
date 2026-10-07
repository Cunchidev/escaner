package com.cunchidev.escaner.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.cunchidev.escaner.data.db.Document
import com.cunchidev.escaner.data.db.DocumentDao
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Documentos: filas en Room + JPG das páxinas en `filesDir/docs/<id>/`. O PDF non se garda:
 * xérase a partir dos JPG cada vez que se exporta, así que engadir, borrar ou reordenar
 * páxinas non deixa nada desfasado.
 */
@Singleton
class DocumentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: DocumentDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ocrMutex = Mutex()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    val documents: Flow<List<Document>> = dao.getAll()
    val folders: Flow<List<String>> = dao.getFolders()

    fun search(text: String): Flow<List<Document>> = FtsQuery.build(text)?.let(dao::search) ?: dao.getAll()

    fun document(id: Long): Flow<Document?> = dao.getByIdFlow(id)

    private fun dir(id: Long) = File(context.filesDir, "docs/$id")

    fun pageFiles(doc: Document): List<File> = PageList.parse(doc.pages).map { File(dir(doc.id), it) }

    /** Documento novo coas páxinas que devolveu o escáner. Devolve o seu id. */
    suspend fun create(pageUris: List<Uri>, folder: String?): Long = withContext(Dispatchers.IO) {
        val agora = System.currentTimeMillis()
        val titulo = "Documento " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm"))
        val id = dao.insert(Document(title = titulo, folder = folder, createdAt = agora, updatedAt = agora))
        val nomes = copyPages(id, pageUris)
        dao.getById(id)?.let { dao.update(it.copy(pages = PageList.join(nomes))) }
        requestOcr(id)
        id
    }

    suspend fun addPages(id: Long, pageUris: List<Uri>) = withContext(Dispatchers.IO) {
        val nomes = copyPages(id, pageUris)
        val doc = dao.getById(id) ?: return@withContext
        save(doc.copy(pages = PageList.join(PageList.parse(doc.pages) + nomes), ocrDone = false))
        requestOcr(id)
    }

    private fun copyPages(id: Long, uris: List<Uri>): List<String> {
        val destino = dir(id).apply { mkdirs() }
        return uris.mapNotNull { uri ->
            val nome = "${UUID.randomUUID()}.jpg"
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { entrada ->
                    File(destino, nome).outputStream().use { entrada.copyTo(it) }
                } ?: error("sen datos")
                nome
            }.getOrNull()
        }
    }

    suspend fun movePage(id: Long, from: Int, to: Int) = withContext(Dispatchers.IO) {
        val doc = dao.getById(id) ?: return@withContext
        save(doc.copy(pages = PageList.join(PageList.move(PageList.parse(doc.pages), from, to)), ocrDone = false))
        requestOcr(id)
    }

    suspend fun deletePage(id: Long, index: Int) = withContext(Dispatchers.IO) {
        val doc = dao.getById(id) ?: return@withContext
        val nomes = PageList.parse(doc.pages)
        nomes.getOrNull(index)?.let { File(dir(id), it).delete() }
        save(doc.copy(pages = PageList.join(PageList.removeAt(nomes, index)), ocrDone = false))
        requestOcr(id)
    }

    suspend fun rename(id: Long, title: String) = withContext(Dispatchers.IO) {
        val limpo = title.trim().ifEmpty { return@withContext }
        dao.getById(id)?.let { save(it.copy(title = limpo)) }
    }

    suspend fun setFolder(id: Long, folder: String?) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { save(it.copy(folder = folder?.trim()?.takeIf { f -> f.isNotEmpty() })) }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.delete(id)
        dir(id).deleteRecursively()
    }

    private suspend fun save(doc: Document) = dao.update(doc.copy(updatedAt = System.currentTimeMillis()))

    // ---------- OCR ----------

    /** Recoñece o texto en segundo plano; sobrevive a saír da pantalla. */
    private fun requestOcr(id: Long) {
        scope.launch { runOcr(id) }
    }

    /** Documentos que quedaron sen OCR (a app pechouse a medias): chámase ao abrir. */
    suspend fun resumePendingOcr(docs: List<Document>) {
        docs.filter { !it.ocrDone && it.pages.isNotEmpty() }.forEach { requestOcr(it.id) }
    }

    private suspend fun runOcr(id: Long) = ocrMutex.withLock {
        val doc = dao.getById(id) ?: return@withLock
        if (doc.ocrDone) return@withLock
        val texto = pageFiles(doc).map { recognize(it) }.filter { it.isNotBlank() }.joinToString("\n\n")
        // Se as páxinas cambiaron mentres tanto, este resultado xa non vale: outro OCR vén detrás.
        val actual = dao.getById(id) ?: return@withLock
        if (actual.pages == doc.pages) dao.update(actual.copy(ocrText = texto, ocrDone = true))
    }

    private suspend fun recognize(file: File): String = suspendCancellableCoroutine { cont ->
        val imaxe = runCatching { InputImage.fromFilePath(context, Uri.fromFile(file)) }.getOrNull()
        if (imaxe == null) {
            cont.resume("")
            return@suspendCancellableCoroutine
        }
        recognizer.process(imaxe)
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resume("") }
    }

    // ---------- exportar ----------

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Xera o PDF na caché e devolve un Uri compartible. */
    suspend fun exportPdf(doc: Document): Uri = withContext(Dispatchers.IO) {
        val carpeta = File(context.cacheDir, "export").apply { mkdirs() }
        // Os PDF vellos xa se compartiron: non hai por que acumulalos.
        carpeta.listFiles()?.forEach { it.delete() }
        val ficheiro = File(carpeta, FileNames.safe(doc.title) + ".pdf")
        ficheiro.outputStream().buffered().use { JpegPdfWriter.write(pageFiles(doc), it) }
        uriFor(ficheiro)
    }

    /** Escribe o PDF nun destino escollido polo usuario ("Gardar en…"). */
    suspend fun writePdfTo(doc: Document, destino: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(destino)?.buffered()?.use { JpegPdfWriter.write(pageFiles(doc), it) }
                ?: error("sen destino")
        }.isSuccess
    }

    fun pageUris(doc: Document): List<Uri> = pageFiles(doc).filter { it.exists() }.map(::uriFor)
}
