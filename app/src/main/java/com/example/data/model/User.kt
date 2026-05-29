package com.example.data.model

data class User(
    val username: String = "",
    val passwordHash: String = "",
    val role: String = "Operator"
)
