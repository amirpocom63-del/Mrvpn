package com.example.db

import androidx.room.*
import com.example.model.SubscriptionItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscription_items ORDER BY name ASC")
    fun getAllSubscriptions(): Flow<List<SubscriptionItem>>

    @Query("SELECT * FROM subscription_items ORDER BY name ASC")
    suspend fun getAllSubscriptionsSync(): List<SubscriptionItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(item: SubscriptionItem)

    @Update
    suspend fun updateSubscription(item: SubscriptionItem)

    @Delete
    suspend fun deleteSubscription(item: SubscriptionItem)

    @Query("DELETE FROM subscription_items")
    suspend fun clearAllSubscriptions()
}
