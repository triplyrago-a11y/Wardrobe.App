package com.example.wardrobe.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.data.model.Outfit
import com.example.wardrobe.data.model.PlannedOutfit
import kotlinx.coroutines.flow.Flow

@Dao
interface ClothingItemDao {
    @Query("SELECT * FROM clothing_items ORDER BY createdAt DESC")
    fun getAllItems(): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE id = :id LIMIT 1")
    fun getItemById(id: Long): Flow<ClothingItem?>

    @Query("SELECT * FROM clothing_items WHERE id IN (:ids)")
    fun getItemsByIds(ids: List<Long>): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE category = :category ORDER BY createdAt DESC")
    fun getItemsByCategory(category: String): Flow<List<ClothingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClothingItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClothingItem>)

    @Update
    suspend fun update(item: ClothingItem)

    @Delete
    suspend fun delete(item: ClothingItem)

    @Query("DELETE FROM clothing_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE clothing_items SET wearCount = wearCount + 1, lastWorn = :timestamp WHERE id = :id")
    suspend fun incrementWear(id: Long, timestamp: Long)

    @Query("UPDATE clothing_items SET favorite = NOT favorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("DELETE FROM clothing_items")
    suspend fun clearAll()
}

@Dao
interface OutfitDao {
    @Query("SELECT * FROM outfits ORDER BY createdAt DESC")
    fun getAllOutfits(): Flow<List<Outfit>>

    @Query("SELECT * FROM outfits WHERE id = :id LIMIT 1")
    fun getOutfitById(id: Long): Flow<Outfit?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outfit: Outfit): Long

    @Update
    suspend fun update(outfit: Outfit)

    @Delete
    suspend fun delete(outfit: Outfit)

    @Query("DELETE FROM outfits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE outfits SET favorite = NOT favorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("DELETE FROM outfits")
    suspend fun clearAll()
}

@Dao
interface PlannedOutfitDao {
    @Query("SELECT * FROM planned_outfits ORDER BY date ASC")
    fun getAllPlanned(): Flow<List<PlannedOutfit>>

    @Query("SELECT * FROM planned_outfits WHERE date = :date")
    fun getPlannedForDate(date: String): Flow<List<PlannedOutfit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(planned: PlannedOutfit): Long

    @Query("DELETE FROM planned_outfits WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Delete
    suspend fun delete(planned: PlannedOutfit)

    @Query("DELETE FROM planned_outfits")
    suspend fun clearAll()
}
