package com.medtimer.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,           // ISO date format: YYYY-MM-DD
    val startTime: String,      // ISO time format: HH:MM:SS
    val elapsedSeconds: Int     // Total meditation time in seconds
) {
    val formattedDate: String
        get() = try {
            val localDate = LocalDate.parse(date)
            localDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        } catch (e: Exception) {
            date
        }

    val formattedStartTime: String
        get() = try {
            val localTime = LocalTime.parse(startTime)
            localTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (e: Exception) {
            startTime
        }

    val formattedDuration: String
        get() {
            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            return if (minutes > 0) {
                "${minutes}m ${seconds}s"
            } else {
                "${seconds}s"
            }
        }

    fun toCsvRow(): String {
        return "$date,$startTime,$elapsedSeconds"
    }

    companion object {
        fun csvHeader(): String = "date,start_time,elapsed_seconds"
    }
}
