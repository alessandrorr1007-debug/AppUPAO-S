package com.example.upaos.ui.canvas

import androidx.compose.runtime.Composable
import com.example.upaos.data.model.CanvasFileDetail

/** Compatibility entry point; downloads share the account-aware document transport. */
@Composable
fun PrivatePdfViewer(url: String) {
    CanvasDocument(CanvasFileDetail(0, filename = "documento.pdf", contentType = "application/pdf", url = url), canvasSavedToken())
}
