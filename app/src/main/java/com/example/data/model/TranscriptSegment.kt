package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class TranscriptSegment(
    val speaker: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val text: String = ""
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("speaker", speaker)
            put("startMs", startMs)
            put("endMs", endMs)
            put("text", text)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): TranscriptSegment {
            return TranscriptSegment(
                speaker = obj.optString("speaker", ""),
                startMs = obj.optLong("startMs", 0L),
                endMs = obj.optLong("endMs", 0L),
                text = obj.optString("text", "")
            )
        }

        fun listToJson(list: List<TranscriptSegment>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJsonObject()) }
            return array.toString()
        }

        fun jsonToList(json: String?): List<TranscriptSegment> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(json)
                val list = mutableListOf<TranscriptSegment>()
                for (i in 0 until array.length()) {
                    list.add(fromJsonObject(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
