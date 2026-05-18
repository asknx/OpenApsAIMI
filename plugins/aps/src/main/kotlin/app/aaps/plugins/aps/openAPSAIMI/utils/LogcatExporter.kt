package app.aaps.plugins.aps.openAPSAIMI.utils

import android.content.Context
import android.os.Environment
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogcatExporter @Inject constructor(
    private val aapsLogger: AAPSLogger
) {
    suspend fun exportLogs(context: Context, minutes: Int): File? = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val startTime = now - (minutes * 60 * 1000L)
            
            // 1. Setup directory
            val aapsDir = File(Environment.getExternalStorageDirectory(), "Documents/AAPS")
            val logsDir = File(aapsDir, "logs")
            if (!logsDir.exists()) logsDir.mkdirs()

            // 2. Filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
            val logFile = File(logsDir, "logcat_$timestamp.txt")

            // 3. Command execution
            // We use 'logcat -d' to dump and 'grep' for timestamp or direct 't' filter
            // Note: logcat -t with date string requires MM-dd HH:mm:ss.SSS format
            val startTimeStr = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(startTime))
            
            aapsLogger.info(LTag.APS, "Attempting logcat export since $startTimeStr")
            
            // Run logcat command
            // Note: Use simple -v threadtime for best compatibility with -t date
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "-t", startTimeStr))
            
            val output = logFile.outputStream()
            val input = process.inputStream
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytes = 0L
            
            input.use { i ->
                output.use { o ->
                    while (i.read(buffer).also { bytesRead = it } != -1) {
                        o.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                }
            }
            
            process.waitFor()
            
            // If the filtered file is too small (e.g. less than 1KB), it likely missed critical info
            // or the time filter didn't work as expected. Fallback to a broader dump.
            if (totalBytes < 1024L) {
                aapsLogger.warn(LTag.APS, "Filtered logcat was too small ($totalBytes bytes), dumping last 4000 lines as fallback")
                val fallbackProcess = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "-t", "4000"))
                logFile.outputStream().use { fos ->
                    fallbackProcess.inputStream.copyTo(fos)
                }
            }

            aapsLogger.info(LTag.APS, "Logcat export finished. Size: ${logFile.length()} bytes")
            return@withContext logFile
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Failed to export logcat", e)
            null
        }
    }
}
