package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "subscription_items")
data class SubscriptionItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String
)
