package com.cunchidev.escaner.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cunchidev.escaner.data.DocumentRepository
import com.cunchidev.escaner.data.PageList
import com.cunchidev.escaner.data.db.Document
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val uiPrefs: UiPrefs
) : ViewModel() {

    val config: StateFlow<UiConfig> = uiPrefs.config
    val query = MutableStateFlow("")
    /** null = todas as carpetas. */
    val folder = MutableStateFlow<String?>(null)

    /** Todo o que hai, sen filtrar: para os totais e o reconto de cada carpeta. */
    val all: StateFlow<List<Document>> =
        repository.documents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<Document>> =
        combine(query.flatMapLatest { repository.search(it) }, folder, uiPrefs.config) { docs, carpeta, cfg ->
            val filtrados = if (carpeta == null) docs else docs.filter { it.folder == carpeta }
            when (cfg.sort) {
                SortOrder.RECENTES -> filtrados.sortedByDescending { it.updatedAt }
                SortOrder.ANTIGOS -> filtrados.sortedBy { it.updatedAt }
                SortOrder.NOME -> filtrados.sortedBy { it.title.lowercase() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { repository.resumePendingOcr(repository.documents.first()) }
    }

    fun firstPage(doc: Document): File? = repository.pageFiles(doc).firstOrNull()

    /** As páxinas recén escaneadas pasan a ser un documento novo (na carpeta que se está vendo). */
    fun create(pages: List<Uri>, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(repository.create(pages, folder.value))
    }

    fun setAccent(accent: Accent) = uiPrefs.update { it.copy(accent = accent) }
    fun setGrid(grid: Boolean) = uiPrefs.update { it.copy(grid = grid) }
    fun setSort(sort: SortOrder) = uiPrefs.update { it.copy(sort = sort) }
}

private val FMT_DATA = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("gl"))

// O papel é papel tamén de noite: as follas non cambian co tema.
private val Folla = Color(0xFFFFFFFF)
private val FollaDetras = Color(0xFFE6DFD0)
private val FollaFondo = Color(0xFFD6CEBC)
private val FORMA_FOLLA = RoundedCornerShape(6.dp)

private fun paxinasTexto(n: Int) = if (n == 1) "1 páxina" else "$n páxinas"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (Long) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val docs by viewModel.documents.collectAsStateWithLifecycle()
    val todos by viewModel.all.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()

    var aparencia by remember { mutableStateOf(false) }
    val escanear = rememberScanner { paxinas -> viewModel.create(paxinas, onOpen) }

    val carpetas = todos.mapNotNull { it.folder }.groupingBy { it }.eachCount().toSortedMap()
    val totalPaxinas = todos.sumOf { PageList.parse(it.pages).size }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = escanear,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(22.dp),
                icon = { ScanGlyph(Modifier.size(24.dp)) },
                text = { Text("Escanear", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (config.grid) 2 else 1),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Cabeceira(
                    documentos = todos.size,
                    paxinas = totalPaxinas,
                    accent = config.accent,
                    onAparencia = { aparencia = true }
                )
            }

            if (todos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { MesaBaleira(onEscanear = escanear) }
                return@LazyVerticalGrid
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Buscador(texto = query, onChange = { viewModel.query.value = it })
            }

            if (carpetas.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Carpeta(
                                nome = "Todo",
                                cantos = todos.size,
                                cor = MaterialTheme.colorScheme.primary,
                                seleccionada = folder == null,
                                onClick = { viewModel.folder.value = null }
                            )
                        }
                        items(carpetas.entries.toList(), key = { it.key }) { (nome, cantos) ->
                            Carpeta(
                                nome = nome,
                                cantos = cantos,
                                cor = folderColor(nome),
                                seleccionada = folder == nome,
                                onClick = { viewModel.folder.value = if (folder == nome) null else nome }
                            )
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when {
                        query.isNotBlank() -> if (docs.isEmpty()) "Nada coincide con «$query»" else "${docs.size} con «$query»"
                        folder != null -> folder.orEmpty()
                        else -> config.sort.label
                    }.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(docs, key = { it.id }) { doc ->
                if (config.grid) {
                    FollaGrande(doc = doc, miniatura = viewModel.firstPage(doc), onClick = { onOpen(doc.id) })
                } else {
                    FollaFila(doc = doc, miniatura = viewModel.firstPage(doc), onClick = { onOpen(doc.id) })
                }
            }
        }
    }

    if (aparencia) {
        AparenciaSheet(
            config = config,
            onAccent = viewModel::setAccent,
            onGrid = viewModel::setGrid,
            onSort = viewModel::setSort,
            onDismiss = { aparencia = false }
        )
    }
}

@Composable
private fun Cabeceira(documentos: Int, paxinas: Int, accent: Accent, onAparencia: () -> Unit) {
    val saudo = when (LocalTime.now().hour) {
        in 6..13 -> "Bos días"
        in 14..20 -> "Boas tardes"
        else -> "Boas noites"
    }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(saudo.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("A túa mesa", style = MaterialTheme.typography.displaySmall)
            Text(
                if (documentos == 0) "Sen papeis polo de agora"
                else "${if (documentos == 1) "1 documento" else "$documentos documentos"} · ${paxinasTexto(paxinas)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // A bóla coa cor escollida abre a aparencia.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onAparencia)
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Box(Modifier.size(18.dp).background(accent.color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Aparencia", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Buscador(texto: String, onChange: (String) -> Unit) {
    TextField(
        value = texto,
        onValueChange = onChange,
        placeholder = { Text("Busca un título ou algo que diga o papel") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (texto.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, contentDescription = "Borrar busca") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Unha carpeta con forma de carpeta: a lingüeta arriba e o corpo debaixo. */
@Composable
private fun Carpeta(nome: String, cantos: Int, cor: Color, seleccionada: Boolean, onClick: () -> Unit) {
    val fondo = if (seleccionada) cor else cor.copy(alpha = 0.30f).compositeOver(MaterialTheme.colorScheme.surface)
    val lingueta = Color.Black.copy(alpha = 0.12f).compositeOver(fondo)
    val texto = contrastOn(fondo)
    Box(
        modifier = Modifier
            .size(width = 128.dp, height = 78.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                val r = CornerRadius(10.dp.toPx())
                val alto = 12.dp.toPx()
                drawRoundRect(lingueta, topLeft = Offset.Zero, size = Size(size.width * 0.46f, alto * 2), cornerRadius = r)
                drawRoundRect(fondo, topLeft = Offset(0f, alto), size = Size(size.width, size.height - alto), cornerRadius = r)
            }
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 24.dp)) {
            Text(nome, style = MaterialTheme.typography.titleSmall, color = texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (cantos == 1) "1 documento" else "$cantos documentos",
                style = MaterialTheme.typography.labelSmall,
                color = texto.copy(alpha = 0.75f)
            )
        }
    }
}

/** A primeira páxina como folla de papel; se o documento ten máis, asoman outras por detrás. */
@Composable
private fun Pila(miniatura: File?, paxinas: Int, carpeta: String?, maxPx: Int, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (paxinas > 2) {
            Box(Modifier.matchParentSize().rotate(5f).shadow(1.dp, FORMA_FOLLA).background(FollaFondo, FORMA_FOLLA))
        }
        if (paxinas > 1) {
            Box(Modifier.matchParentSize().rotate(-3f).shadow(2.dp, FORMA_FOLLA).background(FollaDetras, FORMA_FOLLA))
        }
        Box(Modifier.matchParentSize().shadow(6.dp, FORMA_FOLLA).clip(FORMA_FOLLA).background(Folla)) {
            PageImage(file = miniatura, maxPx = maxPx, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (carpeta != null) {
                // Marcapáxinas coa cor da carpeta.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp)
                        .size(width = 12.dp, height = 20.dp)
                        .background(folderColor(carpeta), RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                )
            }
        }
    }
}

@Composable
private fun FollaGrande(doc: Document, miniatura: File?, onClick: () -> Unit) {
    val paxinas = PageList.parse(doc.pages).size
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 10.dp)) {
            Pila(
                miniatura = miniatura,
                paxinas = paxinas,
                carpeta = doc.folder,
                maxPx = 512,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.74f)
            )
            Text(
                "$paxinas",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            )
        }
        Text(
            doc.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Text(
            Instant.ofEpochMilli(doc.updatedAt).atZone(ZoneId.systemDefault()).format(FMT_DATA),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun FollaFila(doc: Document, miniatura: File?, onClick: () -> Unit) {
    val paxinas = PageList.parse(doc.pages).size
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Pila(miniatura = miniatura, paxinas = paxinas, carpeta = null, maxPx = 256, modifier = Modifier.size(width = 58.dp, height = 78.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(
                        Instant.ofEpochMilli(doc.updatedAt).atZone(ZoneId.systemDefault()).format(FMT_DATA),
                        paxinasTexto(paxinas)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                doc.folder?.let { carpeta ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(folderColor(carpeta), RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(carpeta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** O marco do escáner: catro esquinas e a liña de lectura. Usa a cor do contido. */
@Composable
fun ScanGlyph(modifier: Modifier = Modifier, cor: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val g = size.minDimension * 0.11f
        val l = size.minDimension * 0.28f
        val m = g / 2
        val w = size.width - m
        val h = size.height - m
        val trazo = Stroke(width = g, cap = StrokeCap.Round)
        fun esquina(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(cor, Offset(x, y), Offset(x + dx, y), strokeWidth = trazo.width, cap = StrokeCap.Round)
            drawLine(cor, Offset(x, y), Offset(x, y + dy), strokeWidth = trazo.width, cap = StrokeCap.Round)
        }
        esquina(m, m, l, l)
        esquina(w, m, -l, l)
        esquina(m, h, l, -l)
        esquina(w, h, -l, -l)
        drawLine(cor, Offset(m + g, size.height / 2), Offset(w - g, size.height / 2), strokeWidth = g, cap = StrokeCap.Round)
    }
}

@Composable
private fun MesaBaleira(onEscanear: () -> Unit) {
    val acento = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(width = 150.dp, height = 190.dp)) {
            Box(Modifier.matchParentSize().rotate(6f).background(FollaFondo, FORMA_FOLLA))
            Box(Modifier.matchParentSize().rotate(-4f).shadow(2.dp, FORMA_FOLLA).background(FollaDetras, FORMA_FOLLA))
            Canvas(Modifier.matchParentSize().shadow(8.dp, FORMA_FOLLA).background(Folla, FORMA_FOLLA)) {
                // Unhas liñas de texto e a liña do escáner cruzándoas.
                val marxe = size.width * 0.16f
                for (i in 0 until 7) {
                    val y = size.height * (0.16f + i * 0.1f)
                    val longo = if (i % 3 == 2) 0.45f else 0.68f
                    drawLine(
                        Color(0xFFCFC8B8), Offset(marxe, y), Offset(marxe + size.width * longo, y),
                        strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round
                    )
                }
                drawLine(
                    acento, Offset(-10.dp.toPx(), size.height * 0.52f), Offset(size.width + 10.dp.toPx(), size.height * 0.52f),
                    strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("A mesa está limpa", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Escanea un papel coa cámara ou trae unha foto da galería.\nQueda gardado aquí, sen anuncios.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onEscanear, shape = RoundedCornerShape(18.dp)) {
            ScanGlyph(Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Escanear o primeiro")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AparenciaSheet(
    config: UiConfig,
    onAccent: (Accent) -> Unit,
    onGrid: (Boolean) -> Unit,
    onSort: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Aparencia", style = MaterialTheme.typography.headlineSmall)

            Text("COR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accent.entries.forEach { a ->
                    val escollida = a == config.accent
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .then(
                                if (escollida) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                            )
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(a.color)
                            .clickable { onAccent(a) }
                    ) {
                        if (escollida) Icon(Icons.Default.Check, contentDescription = a.label, tint = contrastOn(a.color), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(config.accent.label, style = MaterialTheme.typography.bodyMedium)

            Text("VISTA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = config.grid, onClick = { onGrid(true) }, label = { Text("Follas na mesa") })
                FilterChip(selected = !config.grid, onClick = { onGrid(false) }, label = { Text("Lista") })
            }

            Text("ORDE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOrder.entries.forEach { s ->
                    FilterChip(selected = config.sort == s, onClick = { onSort(s) }, label = { Text(s.label) })
                }
            }
        }
    }
}
