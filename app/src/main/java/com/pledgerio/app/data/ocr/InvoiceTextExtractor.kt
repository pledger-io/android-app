package com.pledgerio.app.data.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pledgerio.app.util.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Singleton
class InvoiceTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extractText(uri: Uri): Resource<String> {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).awaitResult()
            val text = result.text.trim()
            recognizer.close()
            if (text.isBlank()) {
                Resource.Error("No readable text found. Try a clearer photo.")
            } else {
                Resource.Success(text)
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Could not read text from the selected image")
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
    }
    addOnCanceledListener {
        if (continuation.isActive) continuation.cancel()
    }
}
