package com.cunchidev.escaner.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/** A cor de acento que escolle o usuario: tinguiu botóns, selección e detalles. */
enum class Accent(val label: String, val color: Color) {
    AMBAR("Ámbar", Color(0xFFE8A200)),
    TINTA("Tinta", Color(0xFF2F5DA8)),
    BOTELLA("Botella", Color(0xFF2E7D5B)),
    GRANATE("Granate", Color(0xFFA63D40)),
    VIOLETA("Violeta", Color(0xFF6B4FA0)),
    LOUSA("Lousa", Color(0xFF45505E))
}

enum class SortOrder(val label: String) {
    RECENTES("Máis recentes"),
    ANTIGOS("Máis antigos"),
    NOME("Por nome")
}

data class UiConfig(
    val accent: Accent = Accent.AMBAR,
    val grid: Boolean = true,
    val sort: SortOrder = SortOrder.RECENTES
)

/** Aparencia escollida polo usuario. SharedPreferences: lese síncrono ao pintar o tema. */
@Singleton
class UiPrefs @Inject constructor(@param:ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(
        UiConfig(
            accent = prefs.getString("accent", null)?.let { n -> Accent.entries.firstOrNull { it.name == n } } ?: Accent.AMBAR,
            grid = prefs.getBoolean("grid", true),
            sort = prefs.getString("sort", null)?.let { n -> SortOrder.entries.firstOrNull { it.name == n } } ?: SortOrder.RECENTES
        )
    )
    val config: StateFlow<UiConfig> = _config.asStateFlow()

    fun update(transform: (UiConfig) -> UiConfig) {
        val nova = transform(_config.value)
        prefs.edit()
            .putString("accent", nova.accent.name)
            .putBoolean("grid", nova.grid)
            .putString("sort", nova.sort.name)
            .apply()
        _config.value = nova
    }
}

// Papel e tinta: a app parece unha mesa con follas, non unha lista de axustes.
private val Papel = Color(0xFFF4EEE2)
private val PapelClaro = Color(0xFFFBF8F1)
private val PapelEscuro = Color(0xFFE7DFCE)
private val Tinta = Color(0xFF1E2430)
private val TintaSuave = Color(0xFF5B6270)

private val Noite = Color(0xFF13161B)
private val NoiteClara = Color(0xFF1B1F26)
private val NoiteAlta = Color(0xFF272C35)
private val Marfil = Color(0xFFECE6DA)
private val MarfilSuave = Color(0xFFA9A396)

/** Negro ou branco, o que mellor se lea enriba de [fondo]. */
fun contrastOn(fondo: Color): Color = if (fondo.luminance() > 0.45f) Color(0xFF1A1A1A) else Color.White

/** Cores apagadas para as carpetas; cada nome cae sempre na mesma. */
private val CoresCarpeta = listOf(
    Color(0xFFD9A441), Color(0xFF5B8DB8), Color(0xFF6FA287), Color(0xFFC0736A), Color(0xFF8C79B5), Color(0xFF8A8F98)
)

fun folderColor(name: String): Color = CoresCarpeta[name.hashCode().absoluteValue % CoresCarpeta.size]

private val Serif = FontFamily.Serif

private val Tipografia = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun EscanerTheme(accent: Accent, content: @Composable () -> Unit) {
    val escuro = isSystemInDarkTheme()
    // Na noite o acento aclárase un pouco para que non quede apagado.
    val cor = if (escuro) Color.White.copy(alpha = 0.22f).compositeOver(accent.color) else accent.color
    val cores = if (escuro) {
        darkColorScheme(
            primary = cor,
            onPrimary = contrastOn(cor),
            primaryContainer = cor.copy(alpha = 0.28f).compositeOver(NoiteClara),
            onPrimaryContainer = Marfil,
            secondary = cor,
            onSecondary = contrastOn(cor),
            secondaryContainer = cor.copy(alpha = 0.22f).compositeOver(NoiteClara),
            onSecondaryContainer = Marfil,
            tertiary = cor,
            background = Noite,
            onBackground = Marfil,
            surface = Noite,
            onSurface = Marfil,
            surfaceVariant = NoiteAlta,
            onSurfaceVariant = MarfilSuave,
            surfaceContainerLowest = Noite,
            surfaceContainerLow = NoiteClara,
            surfaceContainer = NoiteClara,
            surfaceContainerHigh = NoiteAlta,
            surfaceContainerHighest = NoiteAlta,
            outline = Color(0xFF5A606B),
            outlineVariant = Color(0xFF363B45)
        )
    } else {
        lightColorScheme(
            primary = cor,
            onPrimary = contrastOn(cor),
            primaryContainer = cor.copy(alpha = 0.24f).compositeOver(PapelClaro),
            onPrimaryContainer = Tinta,
            secondary = cor,
            onSecondary = contrastOn(cor),
            secondaryContainer = cor.copy(alpha = 0.20f).compositeOver(PapelClaro),
            onSecondaryContainer = Tinta,
            tertiary = cor,
            background = Papel,
            onBackground = Tinta,
            surface = Papel,
            onSurface = Tinta,
            surfaceVariant = PapelEscuro,
            onSurfaceVariant = TintaSuave,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = PapelClaro,
            surfaceContainer = PapelClaro,
            surfaceContainerHigh = PapelEscuro,
            surfaceContainerHighest = PapelEscuro,
            outline = Color(0xFF9A927F),
            outlineVariant = Color(0xFFD6CDB9)
        )
    }
    MaterialTheme(colorScheme = cores, typography = Tipografia, content = content)
}
