package com.example.printer365.printer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

enum class ProcessingMode(val label: String, val description: String) {
    DOCUMENT_ADAPTIVE(
        label = "Documento / Cédula (Adaptativo)",
        description = "Elimina sombras, manchas y fondos oscuros. Texto y números 100% nítidos."
    ),
    PHOTO_DITHER(
        label = "Foto / Grises (Tramado)",
        description = "Tramado Floyd-Steinberg para fotos, caras y gráficos con gradientes."
    ),
    MANUAL_THRESHOLD(
        label = "Umbral Manual",
        description = "Corte blanco/negro clásico con ajuste manual de sensibilidad."
    )
}

object BitmapHelper {

    /**
     * Rota un bitmap en el ángulo especificado en grados (90, 180, 270, etc.).
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Recorta un bitmap según coordenadas relativas normalizadas (0.0 a 1.0).
     */
    fun cropBitmap(source: Bitmap, cropRectNorm: RectF): Bitmap {
        val left = (cropRectNorm.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (cropRectNorm.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (cropRectNorm.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (cropRectNorm.bottom * source.height).toInt().coerceIn(top + 1, source.height)

        val width = max(1, right - left)
        val height = max(1, bottom - top)

        return Bitmap.createBitmap(source, left, top, width, height)
    }

    /**
     * Procesa la imagen aplicando redimensionamiento, ajuste de contraste, inversión
     * y binarización avanzada (Bradley-Roth Adaptativo, Floyd-Steinberg Dithering o Umbral).
     */
    fun processImage(
        source: Bitmap,
        targetWidth: Int = 384,
        mode: ProcessingMode = ProcessingMode.DOCUMENT_ADAPTIVE,
        invertColors: Boolean = false,
        adaptiveSensitivity: Float = 14f, // 5 a 30 %
        manualThreshold: Float = 128f,    // 0 a 255
        contrast: Float = 20f             // -50 a 100
    ): Bitmap {
        // 1. Redimensionar manteniendo relación de aspecto
        val aspectRatio = source.height.toFloat() / source.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)

        val totalPixels = targetWidth * targetHeight
        val rawPixels = IntArray(totalPixels)
        scaled.getPixels(rawPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        // 2. Extraer canal de grises con corrección de contraste
        val grays = IntArray(totalPixels)
        val contrastFactor = if (contrast != 0f) {
            (259f * (contrast + 255f)) / (255f * (259f - contrast))
        } else 1.0f

        for (i in 0 until totalPixels) {
            val pixel = rawPixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            // Fórmula de luminancia perceptiva estándar ITU-R BT.601
            var lum = (0.299 * r + 0.587 * g + 0.114 * b).toFloat()

            // Aplicar contraste
            if (contrastFactor != 1.0f) {
                lum = contrastFactor * (lum - 128f) + 128f
            }
            var gray = lum.toInt().coerceIn(0, 255)

            // Inversión si fue solicitada
            if (invertColors) {
                gray = 255 - gray
            }
            grays[i] = gray
        }

        val outPixels = IntArray(totalPixels)

        when (mode) {
            ProcessingMode.DOCUMENT_ADAPTIVE -> {
                // Algoritmo Bradley-Roth con Imagen Integral O(N)
                // Perfecto para cédulas, recibos y documentos con sombras o fondos sucios.
                val s = max(targetWidth / 8, 16)
                val sHalf = s / 2
                val t = adaptiveSensitivity.coerceIn(2f, 40f)

                // Construcción de la imagen integral usando LongArray para evitar overflow
                val stride = targetWidth + 1
                val integral = LongArray(stride * (targetHeight + 1))

                for (y in 0 until targetHeight) {
                    var rowSum = 0L
                    val rowOffset = (y + 1) * stride
                    val prevRowOffset = y * stride
                    for (x in 0 until targetWidth) {
                        rowSum += grays[y * targetWidth + x]
                        integral[rowOffset + (x + 1)] = integral[prevRowOffset + (x + 1)] + rowSum
                    }
                }

                // Umbralizado local relativo
                for (y in 0 until targetHeight) {
                    val y1 = max(0, y - sHalf)
                    val y2 = min(targetHeight - 1, y + sHalf)

                    for (x in 0 until targetWidth) {
                        val x1 = max(0, x - sHalf)
                        val x2 = min(targetWidth - 1, x + sHalf)

                        val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                        val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                                integral[y1 * stride + (x2 + 1)] -
                                integral[(y2 + 1) * stride + x1] +
                                integral[y1 * stride + x1]

                        val gray = grays[y * targetWidth + x]
                        // Si el píxel es un T% más oscuro que el promedio de su vecindario local, es tinta (negro)
                        val isInk = (gray * count.toLong()) < (sum * (100.0 - t) / 100.0).toLong()

                        outPixels[y * targetWidth + x] = if (isInk) Color.BLACK else Color.WHITE
                    }
                }
            }

            ProcessingMode.PHOTO_DITHER -> {
                // Algoritmo Floyd-Steinberg de difusión de error para fotos y escala de grises
                val buffer = FloatArray(totalPixels) { grays[it].toFloat() }

                for (y in 0 until targetHeight) {
                    for (x in 0 until targetWidth) {
                        val idx = y * targetWidth + x
                        val oldVal = buffer[idx]
                        val newVal = if (oldVal < 128f) 0f else 255f
                        buffer[idx] = newVal

                        outPixels[idx] = if (newVal == 0f) Color.BLACK else Color.WHITE

                        val error = oldVal - newVal

                        // Difusión a vecinos: derecha (7/16), abajo-izq (3/16), abajo (5/16), abajo-der (1/16)
                        if (x + 1 < targetWidth) {
                            buffer[idx + 1] += error * (7f / 16f)
                        }
                        if (y + 1 < targetHeight) {
                            if (x - 1 >= 0) {
                                buffer[(y + 1) * targetWidth + (x - 1)] += error * (3f / 16f)
                            }
                            buffer[(y + 1) * targetWidth + x] += error * (5f / 16f)
                            if (x + 1 < targetWidth) {
                                buffer[(y + 1) * targetWidth + (x + 1)] += error * (1f / 16f)
                            }
                        }
                    }
                }
            }

            ProcessingMode.MANUAL_THRESHOLD -> {
                val thresh = manualThreshold.toInt()
                for (i in 0 until totalPixels) {
                    outPixels[i] = if (grays[i] < thresh) Color.BLACK else Color.WHITE
                }
            }
        }

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        return output
    }
}
