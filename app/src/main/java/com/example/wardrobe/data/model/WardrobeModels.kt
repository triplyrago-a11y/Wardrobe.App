package com.example.wardrobe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val imageUri: String,
    val category: String, // Tops, Bottoms, Dresses, Outerwear, Shoes, Accessories
    val colors: List<String> = emptyList(),
    val pattern: String = "Solid",
    val material: String = "Cotton",
    val brand: String = "",
    val size: String = "M",
    val seasons: List<String> = listOf("All-Season"),
    val occasions: List<String> = listOf("Casual"),
    val price: Double = 0.0,
    val notes: String = "",
    val favorite: Boolean = false,
    val wearCount: Int = 0,
    val lastWorn: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "outfits")
data class Outfit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val itemIds: List<Long>,
    val layoutJson: String = "", // Canvas layout positioning
    val occasion: String = "Casual",
    val season: String = "All-Season",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "planned_outfits")
data class PlannedOutfit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // "YYYY-MM-DD"
    val outfitId: Long
)

data class CanvasItemPosition(
    val itemId: Long,
    val xPercent: Float, // 0.0 to 1.0 relative to canvas
    val yPercent: Float,
    val scale: Float = 1.0f,
    val zIndex: Int = 0
)

class Converters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val longListType = Types.newParameterizedType(List::class.java, java.lang.Long::class.java)

    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)
    private val longListAdapter = moshi.adapter<List<Long>>(longListType)

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return stringListAdapter.toJson(value ?: emptyList())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            stringListAdapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    @TypeConverter
    fun fromLongList(value: List<Long>?): String {
        return longListAdapter.toJson(value ?: emptyList())
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            longListAdapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            value.split(",").mapNotNull { it.trim().toLongOrNull() }
        }
    }
}
