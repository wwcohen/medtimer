package com.medtimer.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY date DESC, startTime DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions ORDER BY date DESC, startTime DESC")
    suspend fun getAllSessionsList(): List<Session>

    @Insert
    suspend fun insert(session: Session): Long

    @Delete
    suspend fun delete(session: Session)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("SELECT SUM(elapsedSeconds) FROM sessions")
    fun getTotalMeditationTime(): Flow<Int?>

    @Query("SELECT COUNT(*) FROM sessions")
    fun getSessionCount(): Flow<Int>
}
