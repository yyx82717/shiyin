package com.shiyin.player.data.remote

import com.shiyin.player.data.model.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 容错解析。服务器出错时返回 HTML、返回空串、被运营商劫持插一段脚本，
 * 都会让 `parseToJsonElement` 直接抛异常；音源解析不该因此把整个列表页打崩。
 */
fun parseJsonOrNull(text: String): JsonElement? =
    if (text.isBlank()) null else runCatching { AppJson.parseToJsonElement(text) }.getOrNull()

fun JsonElement?.asObjOrNull(): JsonObject? = this as? JsonObject
fun JsonElement?.asArrOrNull(): JsonArray? = this as? JsonArray

/**
 * 容忍「只有一条时服务端返回对象而不是数组」的写法。
 *
 * 这不是理论问题：Subsonic 的 `song` 在部分实现（以及开着 JSONP/XML 兼容层时）
 * 只有一首歌会退化成对象，直接 `asArrOrNull()` 会得到 null，界面就显示曲库为空。
 */
fun JsonElement?.asArrayOrSingle(): JsonArray? = when (this) {
    is JsonArray -> this
    is JsonObject -> JsonArray(listOf(this))
    else -> null
}

/** 取字符串；数组取第一个元素，缺失或 null 返回 null。 */
fun JsonObject.str(key: String): String? {
    return when (val e = this[key]) {
        is JsonPrimitive -> e.content.takeIf { it.isNotEmpty() && it != "null" }
        is JsonArray -> (e.firstOrNull() as? JsonPrimitive)?.content?.takeIf { it != "null" }
        else -> null
    }
}

fun JsonObject.long(key: String): Long? = str(key)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

fun JsonObject.int(key: String): Int? = long(key)?.toInt()

fun JsonObject.bool(key: String): Boolean? = when (val s = str(key)) {
    "true" -> true
    "false" -> false
    else -> null
}

inline fun <T> JsonObject.forEachElement(block: (JsonObject) -> T?): List<T> {
    val arr = (this["data"] as? JsonArray) ?: (this["results"] as? JsonArray) ?: return emptyList()
    return arr.mapNotNull { (it as? JsonObject)?.let(block) }
}
