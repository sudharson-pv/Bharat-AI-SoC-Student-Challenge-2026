package com.learnmate.ai.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room type converters for JSON-backed complex structures.
 */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromIntList(value: List<Int>?): String {
        return gson.toJson(value ?: emptyList<Int>())
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Int>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

