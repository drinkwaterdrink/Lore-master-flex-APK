package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.UniverseProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "universe_projects")
data class UniverseProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val genre: String,
    val updatedAt: Long,
    val serializedPayload: String
)

@Dao
interface UniverseDao {
    @Query("SELECT * FROM universe_projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<UniverseProjectEntity>>

    @Query("SELECT COUNT(*) FROM universe_projects")
    suspend fun getProjectCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(entity: UniverseProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(entities: List<UniverseProjectEntity>)

    @Query("DELETE FROM universe_projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Database(
    entities = [UniverseProjectEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UniverseDatabase : RoomDatabase() {
    abstract fun universeDao(): UniverseDao

    companion object {
        @Volatile
        private var INSTANCE: UniverseDatabase? = null

        fun getInstance(context: Context): UniverseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    UniverseDatabase::class.java,
                    "loremaster_flex_universe.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}

class UniverseRepository(private val dao: UniverseDao) {
    val allProjectsFlow: Flow<List<UniverseProject>> = dao.observeAllProjects().map { entities ->
        entities.mapNotNull { entity ->
            runCatching { LorebookAndCardCodec.deserializeProject(entity.serializedPayload) }.getOrNull()
        }
    }

    suspend fun ensureSeededIfEmpty() {
        if (dao.getProjectCount() == 0) {
            val seeds = StarterUniverseSeeds.createDefaultUniverses().map { it.toEntity() }
            dao.upsertProjects(seeds)
        }
    }

    suspend fun saveProject(project: UniverseProject) {
        val updated = project.copy(
            updatedAt = System.currentTimeMillis(),
            lorebook = project.lorebook.copy(name = project.name, description = project.description)
        )
        dao.upsertProject(updated.toEntity())
    }

    suspend fun deleteProject(id: String) {
        dao.deleteProjectById(id)
    }

    private fun UniverseProject.toEntity(): UniverseProjectEntity = UniverseProjectEntity(
        id = id,
        name = name,
        genre = genre,
        updatedAt = updatedAt,
        serializedPayload = LorebookAndCardCodec.serializeProject(this)
    )
}
