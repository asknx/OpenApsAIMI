package app.aaps.plugins.aps.openAPSAIMI.advisor.meal

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to extract barcode from Bitmap using ML Kit
 */
object BarcodeScanner {
    suspend fun scanBarcode(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_ITF
                )
                .build()
            val scanner = BarcodeScanning.getClient(options)
            val barcodes = Tasks.await(scanner.process(image))
            
            // Return first found barcode raw value
            return@withContext barcodes.firstOrNull()?.rawValue
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
