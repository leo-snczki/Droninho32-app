package pt.droninho32.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Lê um stream **MJPEG** (multipart/x-mixed-replace, como o da ESP32-CAM) e emite
 * cada frame como [Bitmap].
 *
 * Em vez de depender do formato exato dos cabeçalhos multipart, procura os
 * marcadores JPEG no fluxo de bytes: **SOI = FF D8** (início) e **EOI = FF D9** (fim).
 * É robusto entre servidores/firmwares diferentes.
 */
object MjpegStreamer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)   // stream contínuo: sem timeout de leitura
        .build()

    fun frames(url: String): Flow<Bitmap> = flow {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body ?: error("resposta sem corpo")
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val input = BufferedInputStream(body.byteStream(), 32 * 1024)
            val buffer = ByteArrayOutputStream(64 * 1024)
            var prev = -1
            var capturing = false

            while (true) {
                coroutineContext.ensureActive()   // termina quando o ecrã sai
                val b = input.read()
                if (b == -1) break

                if (!capturing) {
                    if (prev == 0xFF && b == 0xD8) {   // SOI
                        capturing = true
                        buffer.reset()
                        buffer.write(0xFF)
                        buffer.write(0xD8)
                    }
                } else {
                    buffer.write(b)
                    if (prev == 0xFF && b == 0xD9) {   // EOI → frame completo
                        val bytes = buffer.toByteArray()
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) emit(bmp)
                        capturing = false
                    }
                }
                prev = b
            }
        }
    }.flowOn(Dispatchers.IO)
}
