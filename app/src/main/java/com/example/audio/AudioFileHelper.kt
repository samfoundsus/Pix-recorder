package com.example.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object AudioFileHelper {
    private const val TAG = "AudioFileHelper"

    /**
     * Writes a valid PCM WAV audio file with human-vocal range harmonic tones
     * simulating natural voice phrasing. Executes on Dispatchers.IO with fast buffered streaming.
     */
    suspend fun writeWavToStream(
        outputStream: java.io.OutputStream,
        durationMs: Long,
        sampleRate: Int = 16000,
        baseFreqHz: Double = 260.0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val clampedDuration = durationMs.coerceIn(1000L, 60000L)
            val numSamples = ((sampleRate.toLong() * clampedDuration) / 1000L).toInt()
            val byteCount = numSamples * 2 // 16-bit mono = 2 bytes per sample

            // Precompute 1 second of speech-like harmonic pattern for instant writing
            val oneSecSamples = sampleRate
            val oneSecBytes = ByteBuffer.allocate(oneSecSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
            val twoPi = 2.0 * Math.PI

            for (i in 0 until oneSecSamples) {
                val tSec = i.toDouble() / sampleRate
                val syllableMod = sin(twoPi * 3.2 * tSec)
                val phraseEnvelope = (sin(twoPi * 0.8 * tSec) * 0.4 + 0.6).coerceIn(0.2, 1.0)
                val pitchInflection = sin(twoPi * 1.5 * tSec) * 25.0
                val fundamental = baseFreqHz + pitchInflection
                val harmonic = fundamental * 2.0

                val wave = (sin(twoPi * fundamental * tSec) * 0.75 + sin(twoPi * harmonic * tSec) * 0.25)
                val amp = (wave * phraseEnvelope * (0.65 + 0.35 * syllableMod)).coerceIn(-0.95, 0.95)
                val pcm = (amp * 16000.0).toInt().toShort()
                oneSecBytes.putShort(pcm)
            }
            val oneSecArray = oneSecBytes.array()

            // Write 44-byte WAV header
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + byteCount)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // Subchunk1Size
            header.putShort(1.toShort()) // PCM format
            header.putShort(1.toShort()) // Mono
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2) // ByteRate
            header.putShort(2.toShort()) // BlockAlign
            header.putShort(16.toShort()) // 16 bits
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(byteCount)

            outputStream.write(header.array())

            var writtenBytes = 0
            while (writtenBytes < byteCount) {
                val remaining = byteCount - writtenBytes
                val toWrite = minOf(remaining, oneSecArray.size)
                outputStream.write(oneSecArray, 0, toWrite)
                writtenBytes += toWrite
            }
            outputStream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write WAV to stream: ${e.message}", e)
            false
        }
    }

    suspend fun writeWavFile(
        file: File,
        durationMs: Long,
        sampleRate: Int = 16000,
        baseFreqHz: Double = 260.0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.parentFile?.exists() == false) {
                file.parentFile?.mkdirs()
            }
            FileOutputStream(file).use { fos ->
                writeWavToStream(fos, durationMs, sampleRate, baseFreqHz)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write WAV file: ${e.message}", e)
            false
        }
    }

    /**
     * Ensures demo audio files exist so initial seed recordings are fully playable.
     * Guaranteed non-blocking and safe to call.
     */
    suspend fun ensureSampleAudioFiles(context: Context): Pair<String, String> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()

        val sample1 = File(dir, "demo_sync_recording.wav")
        if (!sample1.exists() || sample1.length() < 1000) {
            writeWavFile(sample1, durationMs = 38000L, baseFreqHz = 240.0)
        }

        val sample2 = File(dir, "demo_ux_memo.wav")
        if (!sample2.exists() || sample2.length() < 1000) {
            writeWavFile(sample2, durationMs = 21000L, baseFreqHz = 310.0)
        }

        Pair(sample1.absolutePath, sample2.absolutePath)
    }
}
