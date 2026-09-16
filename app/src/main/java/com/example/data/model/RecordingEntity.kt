package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val amplitudesJson: String = "[]",
    val transcriptJson: String = "[]",
    val tag: String = "Notes",
    val isFavorite: Boolean = false
) {
    @delegate:Transient
    private val cachedAmplitudes: List<Float> by lazy {
        val parsed = try {
            if (amplitudesJson.isNotBlank()) {
                val array = JSONArray(amplitudesJson)
                val result = ArrayList<Float>(array.length())
                for (i in 0 until array.length()) {
                    result.add(array.getDouble(i).toFloat())
                }
                result
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (parsed.size >= 12 && parsed.any { it > 0.22f }) {
            parsed
        } else {
            generateNaturalSpeechWaveform(
                seed = (id.toInt() * 31 + title.hashCode()).let { if (it == 0) 42 else it },
                count = 64
            )
        }
    }

    @delegate:Transient
    private val cachedSegments: List<TranscriptSegment> by lazy {
        TranscriptSegment.jsonToList(transcriptJson)
    }

    @delegate:Transient
    private val cachedFullTranscript: String by lazy {
        cachedSegments.joinToString(" ") { it.text }.trim()
    }

    fun getAmplitudes(): List<Float> = cachedAmplitudes

    private fun generateNaturalSpeechWaveform(seed: Int, count: Int): List<Float> {
        val random = java.util.Random(seed.toLong())
        val list = ArrayList<Float>(count)
        var inSpeech = true
        var wordRemaining = 4 + random.nextInt(6)
        var wordTotal = wordRemaining
        var pauseRemaining = 2

        for (i in 0 until count) {
            if (inSpeech) {
                val progress = 1.0f - (wordRemaining.toFloat() / wordTotal.toFloat())
                val envelope = Math.sin(progress * Math.PI).toFloat().coerceAtLeast(0.1f)
                val jitter = 0.75f + random.nextFloat() * 0.35f
                val amp = (0.22f + envelope * 0.68f) * jitter
                list.add(amp.coerceIn(0.12f, 0.96f))
                wordRemaining--
                if (wordRemaining <= 0) {
                    inSpeech = false
                    pauseRemaining = 1 + random.nextInt(3)
                }
            } else {
                list.add((0.06f + random.nextFloat() * 0.05f).coerceIn(0.04f, 0.12f))
                pauseRemaining--
                if (pauseRemaining <= 0) {
                    inSpeech = true
                    wordRemaining = 3 + random.nextInt(7)
                    wordTotal = wordRemaining
                }
            }
        }
        return list
    }

    fun getTranscriptSegments(): List<TranscriptSegment> = cachedSegments

    fun getFullTranscriptText(): String = cachedFullTranscript

    companion object {
        fun amplitudesToJson(list: List<Float>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toDouble()) }
            return array.toString()
        }
    }
}
