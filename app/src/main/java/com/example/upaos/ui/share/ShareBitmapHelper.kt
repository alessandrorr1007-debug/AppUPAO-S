package com.example.upaos.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Utilidades para captura de composables a Bitmap y exportación a través de FileProvider.
 */
object ShareBitmapHelper {

    const val CARD_TARGET_WIDTH_PX = 1080

    suspend fun capturarYCompartir(
        context: Context,
        graphicsLayer: GraphicsLayer,
        nombreArchivo: String,
        titulo: String
    ): Boolean {
        return try {
            val imageBitmap = graphicsLayer.toImageBitmap()
            val bitmap = imageBitmap.asAndroidBitmap()
            val file = guardarBitmapEnCache(context, bitmap, nombreArchivo)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, titulo)
                putExtra(Intent.EXTRA_TEXT, "$titulo · Compartido desde UPAO S")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartir tarjeta con...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar imagen: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun guardarBitmapEnCache(context: Context, bitmap: Bitmap, nombreArchivo: String): File {
        val dir = File(context.cacheDir, "shared_cards")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$nombreArchivo.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    const val CARD_TARGET_HEIGHT_PX = 1920

    fun obtenerDimensionesObjetivo(): Pair<Int, Int> = CARD_TARGET_WIDTH_PX to CARD_TARGET_HEIGHT_PX

    /**
     * Crea un Bitmap inicializado con las dimensiones fijas para la tarjeta compartible (1080 px de ancho).
     */
    fun crearBitmapTarjeta(ancho: Int = CARD_TARGET_WIDTH_PX, alto: Int = CARD_TARGET_HEIGHT_PX): Bitmap? {
        return try {
            Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            null
        }
    }
}
