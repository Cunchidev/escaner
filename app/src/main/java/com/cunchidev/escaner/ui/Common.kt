package com.cunchidev.escaner.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * O escáner de Google: cámara con detección de bordes, recorte, filtros, varias páxinas e
 * importar da galería. Devolve unha función que o abre; as páxinas chegan a [onPages] como JPG.
 */
@Composable
fun rememberScanner(onPages: (List<Uri>) -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val actual by rememberUpdatedState(onPages)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val paxinas = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }.orEmpty()
            if (paxinas.isNotEmpty()) actual(paxinas)
        }
    }
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setGalleryImportAllowed(true)
                .build()
        )
    }
    return {
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener {
                    Toast.makeText(
                        activity,
                        "Non se puido abrir o escáner. Fai falta Google Play Services actualizado.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}

/** Decodifica o JPG reducido para que o lado longo quede preto de [maxPx] (non enche a memoria). */
private fun decodeSampled(file: File, maxPx: Int): ImageBitmap? = runCatching {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, limites)
    var mostra = 1
    while (maxOf(limites.outWidth, limites.outHeight) / (mostra * 2) >= maxPx) mostra *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = mostra })?.asImageBitmap()
}.getOrNull()

/** Unha páxina (ou a súa miniatura) cargada do disco fóra do fío principal. */
@Composable
fun PageImage(file: File?, maxPx: Int, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val imaxe by produceState<ImageBitmap?>(initialValue = null, file, maxPx) {
        value = file?.let { withContext(Dispatchers.IO) { decodeSampled(it, maxPx) } }
    }
    val bitmap = imaxe
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
