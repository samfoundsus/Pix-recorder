package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.audio.AudioFileHelper
import com.example.data.model.RecordingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object RecordingShareHelper {

    fun getAudioMimeType(recording: RecordingEntity, context: Context? = null): String {
        val path = recording.filePath.lowercase(Locale.ROOT)
        if (path.startsWith("content://") && context != null) {
            try {
                val uri = Uri.parse(recording.filePath)
                val resolved = context.contentResolver.getType(uri)
                if (!resolved.isNullOrBlank()) {
                    return resolved
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return when {
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".m4a") || path.endsWith(".mp4") -> "audio/mp4"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".ogg") -> "audio/ogg"
            else -> "audio/*"
        }
    }

    suspend fun getShareableUri(context: Context, recording: RecordingEntity): Uri? = withContext(Dispatchers.IO) {
        val path = recording.filePath
        if (path.isBlank()) {
            return@withContext null
        }

        if (path.startsWith("content://")) {
            return@withContext Uri.parse(path)
        }

        val file = File(path)
        if (file.exists()) {
            return@withContext try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Uri.fromFile(file)
            }
        }
        null
    }

    fun shareAudio(context: Context, recording: RecordingEntity) {
        CoroutineScope(Dispatchers.Main).launch {
            val uri = getShareableUri(context, recording)
            if (uri == null) {
                Toast.makeText(context, "Audio file not available for \"${recording.title}\"", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val mimeType = getAudioMimeType(recording, context)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, "${recording.title} (Audio)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share audio"))
        }
    }

    fun shareTranscript(context: Context, recording: RecordingEntity) {
        val transcript = recording.getFullTranscriptText()
        if (transcript.isBlank()) {
            Toast.makeText(context, "No transcript available for \"${recording.title}\"", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "${recording.title} (Transcript)")
            putExtra(Intent.EXTRA_SUBJECT, recording.title)
            putExtra(Intent.EXTRA_TEXT, "${recording.title}\n\n$transcript")
        }
        context.startActivity(Intent.createChooser(intent, "Share transcript"))
    }

    fun shareMultipleAudio(context: Context, recordings: List<RecordingEntity>) {
        if (recordings.isEmpty()) return

        CoroutineScope(Dispatchers.Main).launch {
            val validUris = ArrayList<Uri>()
            for (rec in recordings) {
                val uri = getShareableUri(context, rec)
                if (uri != null) {
                    validUris.add(uri)
                }
            }

            if (validUris.isEmpty()) {
                Toast.makeText(context, "No audio files available to share", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (validUris.size == 1) {
                val firstRec = recordings.first()
                val mimeType = getAudioMimeType(firstRec, context)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, validUris[0])
                    putExtra(Intent.EXTRA_TITLE, "${firstRec.title} (Audio)")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share audio"))
            } else {
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "audio/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, validUris)
                    putExtra(Intent.EXTRA_TITLE, "${validUris.size} Audio Recordings")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${validUris.size} audio recordings"))
            }
        }
    }

    fun shareMultipleTranscripts(context: Context, recordings: List<RecordingEntity>) {
        if (recordings.isEmpty()) return

        val withTranscripts = recordings.filter { it.getFullTranscriptText().isNotBlank() }
        if (withTranscripts.isEmpty()) {
            Toast.makeText(context, "No transcripts available for the selected recordings", Toast.LENGTH_SHORT).show()
            return
        }

        val combinedText = withTranscripts.joinToString("\n\n--------------------\n\n") { rec ->
            "${rec.title}\n\n${rec.getFullTranscriptText()}"
        }

        val title = if (withTranscripts.size == 1) {
            "${withTranscripts.first().title} (Transcript)"
        } else {
            "${withTranscripts.size} Transcripts"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, combinedText)
        }
        context.startActivity(Intent.createChooser(intent, "Share transcripts"))
    }
}
