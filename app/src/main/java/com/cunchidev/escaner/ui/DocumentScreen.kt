package com.cunchidev.escaner.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cunchidev.escaner.DocumentRoute
import com.cunchidev.escaner.data.DocumentRepository
import com.cunchidev.escaner.data.FileNames
import com.cunchidev.escaner.data.db.Document
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DocumentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DocumentRepository
) : ViewModel() {

    private val id = savedStateHandle.toRoute<DocumentRoute>().id

    val document: StateFlow<Document?> =
        repository.document(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val folders: StateFlow<List<String>> =
        repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pageFiles(doc: Document): List<File> = repository.pageFiles(doc)
    fun pageUris(doc: Document): List<Uri> = repository.pageUris(doc)

    fun addPages(pages: List<Uri>) = viewModelScope.launch { repository.addPages(id, pages) }
    fun movePage(from: Int, to: Int) = viewModelScope.launch { repository.movePage(id, from, to) }
    fun deletePage(index: Int) = viewModelScope.launch { repository.deletePage(id, index) }
    fun rename(title: String) = viewModelScope.launch { repository.rename(id, title) }
    fun setFolder(folder: String?) = viewModelScope.launch { repository.setFolder(id, folder) }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        repository.delete(id)
        onDeleted()
    }

    fun exportPdf(doc: Document, onReady: (Uri) -> Unit) = viewModelScope.launch { onReady(repository.exportPdf(doc)) }

    fun savePdfTo(doc: Document, destino: Uri, onDone: (Boolean) -> Unit) =
        viewModelScope.launch { onDone(repository.writePdfTo(doc, destino)) }
}

private fun compartir(ctx: Context, uris: List<Uri>, mime: String) {
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    intent.type = mime
    // O ClipData é o que lle dá permiso de lectura á app de destino para todos os Uri.
    intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(intent, "Compartir"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(onBack: () -> Unit, viewModel: DocumentViewModel = hiltViewModel()) {
    val doc = viewModel.document.collectAsStateWithLifecycle().value
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var menu by remember { mutableStateOf(false) }
    var renomeando by remember { mutableStateOf(false) }
    var movendo by remember { mutableStateOf(false) }
    var vendoTexto by remember { mutableStateOf(false) }
    var borrando by remember { mutableStateOf(false) }
    var paxinaABorrar by remember { mutableStateOf<Int?>(null) }

    val escanear = rememberScanner { viewModel.addPages(it) }
    val gardarEn = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destino ->
        if (destino != null && doc != null) {
            viewModel.savePdfTo(doc, destino) { ok ->
                Toast.makeText(ctx, if (ok) "PDF gardado" else "Non se puido gardar o PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val paxinas = doc?.let { viewModel.pageFiles(it) }.orEmpty()
    val pager = rememberPagerState(pageCount = { paxinas.size })
    val scope = rememberCoroutineScope()
    val actual = pager.currentPage.coerceIn(0, (paxinas.size - 1).coerceAtLeast(0))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                title = {
                    Column(Modifier.clickable { renomeando = true }) {
                        Text(doc?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        doc?.folder?.let { carpeta ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).background(folderColor(carpeta), RoundedCornerShape(3.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    carpeta,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Máis") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Gardar PDF en…") }, onClick = {
                            menu = false
                            doc?.let { gardarEn.launch(FileNames.safe(it.title) + ".pdf") }
                        })
                        DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; renomeando = true })
                        DropdownMenuItem(text = { Text("Mover a carpeta") }, onClick = { menu = false; movendo = true })
                        DropdownMenuItem(text = { Text("Borrar documento") }, onClick = { menu = false; borrando = true })
                    }
                }
            )
        },
        bottomBar = {
            // As tres saídas do documento, sempre á man.
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        enabled = paxinas.isNotEmpty(),
                        onClick = { doc?.let { d -> viewModel.exportPdf(d) { compartir(ctx, listOf(it), "application/pdf") } } },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1.4f).height(52.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enviar PDF", fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        enabled = paxinas.isNotEmpty(),
                        onClick = { doc?.let { compartir(ctx, viewModel.pageUris(it), "image/jpeg") } },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("JPG") }
                    FilledTonalButton(
                        onClick = { vendoTexto = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Texto") }
                }
            }
        }
    ) { padding ->
        if (doc == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (paxinas.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Este documento quedou sen páxinas", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = escanear, shape = RoundedCornerShape(16.dp)) {
                        ScanGlyph(Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Engadir páxinas")
                    }
                }
            } else {
                // A folla, grande, sobre a mesa. Pásase de páxina co dedo.
                HorizontalPager(
                    state = pager,
                    key = { paxinas.getOrNull(it)?.name ?: it },
                    contentPadding = PaddingValues(horizontal = 28.dp),
                    pageSpacing = 14.dp,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { i ->
                    Box(Modifier.fillMaxSize().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        PageImage(
                            file = paxinas.getOrNull(i),
                            maxPx = 1600,
                            modifier = Modifier
                                .aspectRatio(0.707f)
                                .shadow(10.dp, RoundedCornerShape(4.dp))
                                .background(Color.White, RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // O que se lle pode facer á páxina que se está vendo.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(enabled = actual > 0, onClick = {
                        viewModel.movePage(actual, actual - 1)
                        scope.launch { pager.animateScrollToPage(actual - 1) }
                    }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mover a páxina cara atrás") }
                    Text(
                        "Páxina ${actual + 1} de ${paxinas.size}",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { paxinaABorrar = actual }) {
                        Icon(Icons.Default.Delete, contentDescription = "Borrar esta páxina")
                    }
                    IconButton(enabled = actual < paxinas.lastIndex, onClick = {
                        viewModel.movePage(actual, actual + 1)
                        scope.launch { pager.animateScrollToPage(actual + 1) }
                    }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mover a páxina cara adiante") }
                }

                // Tira de miniaturas: toca para saltar; ao final, engadir máis.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(paxinas, key = { _, f -> f.name }) { i, ficheiro ->
                        val escollida = i == actual
                        PageImage(
                            file = ficheiro,
                            maxPx = 200,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 50.dp, height = 68.dp)
                                .then(
                                    if (escollida) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    else Modifier
                                )
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                                .clickable { scope.launch { pager.animateScrollToPage(i) } }
                        )
                    }
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = 50.dp, height = 68.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = escanear)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Engadir páxinas", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }

    if (renomeando && doc != null) {
        TextoDialog(
            titulo = "Renomear",
            inicial = doc.title,
            etiqueta = "Título",
            onDismiss = { renomeando = false },
            onConfirm = { viewModel.rename(it); renomeando = false }
        )
    }

    if (movendo && doc != null) {
        TextoDialog(
            titulo = "Mover a carpeta",
            inicial = doc.folder.orEmpty(),
            etiqueta = "Carpeta (baleiro = sen carpeta)",
            suxestions = folders,
            onDismiss = { movendo = false },
            onConfirm = { viewModel.setFolder(it); movendo = false }
        )
    }

    if (borrando) {
        AlertDialog(
            onDismissRequest = { borrando = false },
            title = { Text("Borrar documento?") },
            text = { Text("Bórranse todas as súas páxinas. Non se pode desfacer.") },
            confirmButton = {
                TextButton(onClick = { borrando = false; viewModel.delete(onBack) }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { borrando = false }) { Text("Cancelar") } }
        )
    }

    paxinaABorrar?.let { indice ->
        AlertDialog(
            onDismissRequest = { paxinaABorrar = null },
            title = { Text("Borrar a páxina ${indice + 1}?") },
            text = { Text("Non se pode desfacer.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePage(indice); paxinaABorrar = null }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { paxinaABorrar = null }) { Text("Cancelar") } }
        )
    }

    if (vendoTexto && doc != null) {
        ModalBottomSheet(onDismissRequest = { vendoTexto = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Texto recoñecido", style = MaterialTheme.typography.titleLarge)
                when {
                    !doc.ocrDone -> Text("Recoñecendo o texto…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    doc.ocrText.isBlank() -> Text("Non se atopou texto nas páxinas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        TextButton(onClick = {
                            val portapapeis = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            portapapeis?.setPrimaryClip(ClipData.newPlainText(doc.title, doc.ocrText))
                            Toast.makeText(ctx, "Texto copiado", Toast.LENGTH_SHORT).show()
                        }) { Text("Copiar todo") }
                        SelectionContainer { Text(doc.ocrText, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextoDialog(
    titulo: String,
    inicial: String,
    etiqueta: String,
    suxestions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var texto by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(80) },
                    label = { Text(etiqueta) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (suxestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suxestions.forEach { s ->
                            FilterChip(selected = texto == s, onClick = { texto = s }, label = { Text(s) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(texto) }) { Text("Gardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
