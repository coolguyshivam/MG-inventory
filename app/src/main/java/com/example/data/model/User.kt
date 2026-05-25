package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String,
    val passwordHash: String, // Storing plaintext for simplicity in this demo, but named passwordHash
    val role: String // Admin, Manager, Operator, MIS, Sales
)
