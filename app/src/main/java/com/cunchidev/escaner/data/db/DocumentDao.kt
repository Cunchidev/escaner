package com.cunchidev.escaner.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Document>>

    /** [query] xa en sintaxe FTS (ver [com.cunchidev.escaner.data.FtsQuery]). */
    @Query(
        "SELECT documents.* FROM documents JOIN documents_fts ON documents.rowid = documents_fts.rowid " +
            "WHERE documents_fts MATCH :query ORDER BY documents.updatedAt DESC"
    )
    fun search(query: String): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Document?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): Document?

    @Query("SELECT DISTINCT folder FROM documents WHERE folder IS NOT NULL ORDER BY folder")
    fun getFolders(): Flow<List<String>>

    @Insert
    suspend fun insert(document: Document): Long

    @Update
    suspend fun update(document: Document)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Document::class, DocumentFts::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
