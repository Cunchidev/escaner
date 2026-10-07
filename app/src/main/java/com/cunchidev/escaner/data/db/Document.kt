package com.cunchidev.escaner.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * Un documento escaneado. As páxinas son JPG en `filesDir/docs/<id>/`; [pages] garda os seus
 * nomes na orde do documento (ver [com.cunchidev.escaner.data.PageList]).
 */
@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val folder: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: String = "",
    /** Texto recoñecido de todas as páxinas, para buscar e copiar. */
    val ocrText: String = "",
    /** false mentres o OCR aínda non pasou polas páxinas actuais. */
    val ocrDone: Boolean = false
)

/** Índice de busca por título e texto. unicode61 ignora maiúsculas e acentos. */
@Fts4(contentEntity = Document::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "documents_fts")
data class DocumentFts(
    val title: String,
    val ocrText: String
)
