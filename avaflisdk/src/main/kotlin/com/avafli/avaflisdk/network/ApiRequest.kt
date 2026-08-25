package com.avafli.avaflisdk.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wraps request data in Firebase callable convention: {"data": {...}}
 */
@Serializable
internal data class ApiRequest(
    val data: Map<String, JsonElement>
)

/**
 * Wraps response data from Firebase callable convention.
 */
@Serializable
internal data class ApiResponse(
    val result: kotlinx.serialization.json.JsonObject? = null
)
