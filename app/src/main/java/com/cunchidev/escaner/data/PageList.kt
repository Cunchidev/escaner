package com.cunchidev.escaner.data

/**
 * A orde das páxinas dun documento, gardada en `Document.pages` como nomes de ficheiro
 * separados por "|". Reordenar ou borrar unha páxina é só cambiar este texto: os JPG non se renomean.
 */
object PageList {
    private const val SEP = "|"

    fun parse(pages: String): List<String> = pages.split(SEP).filter { it.isNotBlank() }

    fun join(names: List<String>): String = names.joinToString(SEP)

    /** Move a páxina de [from] a [to]; fóra de rango non fai nada. */
    fun move(names: List<String>, from: Int, to: Int): List<String> {
        if (from !in names.indices || to !in names.indices || from == to) return names
        return names.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun removeAt(names: List<String>, index: Int): List<String> =
        if (index in names.indices) names.filterIndexed { i, _ -> i != index } else names
}

/** Texto do buscador → consulta FTS4: todas as palabras, cada unha como prefixo. */
object FtsQuery {
    fun build(text: String): String? {
        val palabras = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        if (palabras.isEmpty()) return null
        return palabras.joinToString(" ") { "\"$it\"*" }
    }
}

/** Nome de ficheiro seguro a partir do título dun documento. */
object FileNames {
    fun safe(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').take(80).ifBlank { "documento" }
}
