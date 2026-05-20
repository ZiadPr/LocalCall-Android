package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class User(
    val userId: String,
    val username: String,
    val ip: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis()
)
