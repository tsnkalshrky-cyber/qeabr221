package com.secretgrave.privacy.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.secretgrave.privacy.core.database.entities.AuthConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthConfigDao {

    @Insert
    suspend fun insert(config: AuthConfig): Long

    @Update
    suspend fun update(config: AuthConfig)

    @Delete
    suspend fun delete(config: AuthConfig)

    @Query("SELECT * FROM auth_config WHERE id = :id")
    suspend fun getById(id: Long): AuthConfig?

    @Query("SELECT * FROM auth_config ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): AuthConfig?

    @Query("SELECT * FROM auth_config ORDER BY id DESC LIMIT 1")
    fun getLatestFlow(): Flow<AuthConfig?>

    @Query("UPDATE auth_config SET pinHash = :pinHash WHERE id = :id")
    suspend fun updatePin(id: Long, pinHash: String)

    @Query("UPDATE auth_config SET fakePin = :fakePin WHERE id = :id")
    suspend fun updateFakePin(id: Long, fakePin: String)

    @Query("UPDATE auth_config SET biometricEnabled = :enabled WHERE id = :id")
    suspend fun setBiometricEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM auth_config WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM auth_config")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM auth_config")
    suspend fun getCount(): Int
}
