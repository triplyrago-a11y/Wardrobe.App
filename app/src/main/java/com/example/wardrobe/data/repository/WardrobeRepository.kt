package com.example.wardrobe.data.repository

import android.content.Context
import com.example.wardrobe.data.demo.DemoData
import com.example.wardrobe.data.local.AppDatabase
import com.example.wardrobe.data.local.ClothingItemDao
import com.example.wardrobe.data.local.OutfitDao
import com.example.wardrobe.data.local.PlannedOutfitDao
import com.example.wardrobe.data.local.PreferencesManager
import com.example.wardrobe.data.model.ClothingItem
import com.example.wardrobe.data.model.Outfit
import com.example.wardrobe.data.model.PlannedOutfit
import com.example.wardrobe.data.remote.GeminiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WardrobeRepository(
    private val clothingItemDao: ClothingItemDao,
    private val outfitDao: OutfitDao,
    private val plannedOutfitDao: PlannedOutfitDao,
    val preferencesManager: PreferencesManager,
    val geminiService: GeminiService
) {
    val allItems: Flow<List<ClothingItem>> = clothingItemDao.getAllItems()
    val allOutfits: Flow<List<Outfit>> = outfitDao.getAllOutfits()
    val allPlannedOutfits: Flow<List<PlannedOutfit>> = plannedOutfitDao.getAllPlanned()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    suspend fun checkAndSeedInitialData() {
        val currentItems = allItems.first()
        if (currentItems.isEmpty()) {
            clothingItemDao.insertAll(DemoData.items)

            // Also create 2 initial sample outfits with the demo items
            val insertedItems = allItems.first()
            if (insertedItems.size >= 4) {
                val outfit1 = Outfit(
                    name = "Effortless Minimalist Work",
                    itemIds = listOf(insertedItems[0].id, insertedItems[3].id, insertedItems[8].id),
                    occasion = "Work",
                    season = "All-Season",
                    favorite = true
                )
                val outfit2 = Outfit(
                    name = "Weekend City Stride",
                    itemIds = listOf(insertedItems[1].id, insertedItems[4].id, insertedItems[6].id, insertedItems[9].id),
                    occasion = "Casual",
                    season = "Fall",
                    favorite = true
                )
                outfitDao.insert(outfit1)
                outfitDao.insert(outfit2)
            }
        }
    }

    suspend fun insertItem(item: ClothingItem): Long = clothingItemDao.insert(item)

    suspend fun updateItem(item: ClothingItem) = clothingItemDao.update(item)

    suspend fun deleteItem(item: ClothingItem) = clothingItemDao.delete(item)

    suspend fun deleteItems(ids: List<Long>) = clothingItemDao.deleteByIds(ids)

    suspend fun toggleFavoriteItem(id: Long) = clothingItemDao.toggleFavorite(id)

    suspend fun recordItemWear(id: Long) {
        clothingItemDao.incrementWear(id, System.currentTimeMillis())
    }

    suspend fun recordOutfitWear(outfit: Outfit) {
        val now = System.currentTimeMillis()
        for (itemId in outfit.itemIds) {
            clothingItemDao.incrementWear(itemId, now)
        }
    }

    suspend fun insertOutfit(outfit: Outfit): Long = outfitDao.insert(outfit)

    suspend fun updateOutfit(outfit: Outfit) = outfitDao.update(outfit)

    suspend fun deleteOutfit(outfit: Outfit) = outfitDao.delete(outfit)

    suspend fun deleteOutfitById(id: Long) = outfitDao.deleteById(id)

    suspend fun toggleFavoriteOutfit(id: Long) = outfitDao.toggleFavorite(id)

    suspend fun planOutfitForDate(date: String, outfitId: Long) {
        plannedOutfitDao.deleteForDate(date)
        plannedOutfitDao.insert(PlannedOutfit(date = date, outfitId = outfitId))
    }

    suspend fun removePlannedOutfitForDate(date: String) {
        plannedOutfitDao.deleteForDate(date)
    }

    suspend fun clearAllData() {
        clothingItemDao.clearAll()
        outfitDao.clearAll()
        plannedOutfitDao.clearAll()
    }

    suspend fun reloadDemoData() {
        clearAllData()
        clothingItemDao.insertAll(DemoData.items)
        val insertedItems = allItems.first()
        if (insertedItems.size >= 4) {
            outfitDao.insert(
                Outfit(
                    name = "Monochrome Tailored Uniform",
                    itemIds = listOf(insertedItems[0].id, insertedItems[3].id, insertedItems[8].id),
                    occasion = "Work",
                    season = "All-Season",
                    favorite = true
                )
            )
            outfitDao.insert(
                Outfit(
                    name = "Autumn Coffee Run",
                    itemIds = listOf(insertedItems[1].id, insertedItems[4].id, insertedItems[6].id, insertedItems[9].id),
                    occasion = "Casual",
                    season = "Fall",
                    favorite = true
                )
            )
        }
    }

    suspend fun exportWardrobeJson(): String {
        val items = allItems.first()
        val type = Types.newParameterizedType(List::class.java, ClothingItem::class.java)
        val adapter = moshi.adapter<List<ClothingItem>>(type)
        return adapter.indent("  ").toJson(items)
    }

    suspend fun importWardrobeJson(json: String): Boolean {
        return try {
            val type = Types.newParameterizedType(List::class.java, ClothingItem::class.java)
            val adapter = moshi.adapter<List<ClothingItem>>(type)
            val imported = adapter.fromJson(json) ?: return false
            clothingItemDao.insertAll(imported)
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun create(context: Context): WardrobeRepository {
            val db = AppDatabase.getDatabase(context)
            val prefs = PreferencesManager(context)
            val gemini = GeminiService()
            return WardrobeRepository(
                db.clothingItemDao(),
                db.outfitDao(),
                db.plannedOutfitDao(),
                prefs,
                gemini
            )
        }
    }
}
