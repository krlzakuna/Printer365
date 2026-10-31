package com.example.printer365.scanner

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class QuadCorners(
    var topLeft: PointF = PointF(0.05f, 0.08f),
    var topRight: PointF = PointF(0.95f, 0.08f),
    var bottomRight: PointF = PointF(0.95f, 0.92f),
    var bottomLeft: PointF = PointF(0.05f, 0.92f)
)

enum class ScannerFilter(val title: String, val description: String) {
    ECO(
        title = "🍃 Eco (B&N)",
        description = "Fondo blanco puro y texto/códigos negros de alto contraste estilo CamScanner Eco."
    ),
    MAGIC_COLOR(
        title = "🌟 Realce Mágico",
        description = "Fondo blanco puro, elimina sombras e intensifica letras, sellos y firmas a color."
    ),
    DOCUMENT_BW(
        title = "📄 Doc B&W (Térmica)",
        description = "Binarización adaptativa local Bradley-Roth. Óptimo para impresión térmica y archivo legal."
    ),
    GRAYSCALE(
        title = "🌫️ Escala de Grises",
        description = "Convierte a grises con eliminación de sombras e iluminación uniforme."
    ),
    ORIGINAL(
        title = "🖼️ Original Enderezado",
        description = "Corrección de perspectiva sin alteración de colores."
    )
}

object DocumentScannerHelper {

    /**
     * Aplica transformación de perspectiva 4-puntos (Homografía / Unwarp) estilo CamScanner.
     * Mapea el cuadrilátero deformado a un rectángulo plano perfecto.
     */
    fun unwarpPerspective(source: Bitmap, quadNorm: QuadCorners): Bitmap {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()

        // Coordenadas absolutas en píxeles de las 4 esquinas
        val p0x = (quadNorm.topLeft.x * srcW).coerceIn(0f, srcW)
        val p0y = (quadNorm.topLeft.y * srcH).coerceIn(0f, srcH)

        val p1x = (quadNorm.topRight.x * srcW).coerceIn(0f, srcW)
        val p1y = (quadNorm.topRight.y * srcH).coerceIn(0f, srcH)

        val p2x = (quadNorm.bottomRight.x * srcW).coerceIn(0f, srcW)
        val p2y = (quadNorm.bottomRight.y * srcH).coerceIn(0f, srcH)

        val p3x = (quadNorm.bottomLeft.x * srcW).coerceIn(0f, srcW)
        val p3y = (quadNorm.bottomLeft.y * srcH).coerceIn(0f, srcH)

        // Calcular ancho y alto objetivo del documento desdoblado
        val widthTop = hypot((p1x - p0x).toDouble(), (p1y - p0y).toDouble()).toFloat()
        val widthBottom = hypot((p2x - p3x).toDouble(), (p2y - p3y).toDouble()).toFloat()
        val targetWidth = max(widthTop, widthBottom).toInt().coerceIn(100, 4000)

        val heightLeft = hypot((p3x - p0x).toDouble(), (p3y - p0y).toDouble()).toFloat()
        val heightRight = hypot((p2x - p1x).toDouble(), (p2y - p1y).toDouble()).toFloat()
        val targetHeight = max(heightLeft, heightRight).toInt().coerceIn(100, 4000)

        // Puntos de origen en la imagen (src) y destino plano (dst)
        val srcPts = floatArrayOf(
            p0x, p0y,
            p1x, p1y,
            p2x, p2y,
            p3x, p3y
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        // Matriz de mapeo inverso: dst -> src
        val matrix = Matrix()
        matrix.setPolyToPoly(dstPts, 0, srcPts, 0, 4)

        val matrixValues = FloatArray(9)
        matrix.getValues(matrixValues)

        val h00 = matrixValues[Matrix.MSCALE_X]
        val h01 = matrixValues[Matrix.MSKEW_X]
        val h02 = matrixValues[Matrix.MTRANS_X]

        val h10 = matrixValues[Matrix.MSKEW_Y]
        val h11 = matrixValues[Matrix.MSCALE_Y]
        val h12 = matrixValues[Matrix.MTRANS_Y]

        val h20 = matrixValues[Matrix.MPERSP_0]
        val h21 = matrixValues[Matrix.MPERSP_1]
        val h22 = matrixValues[Matrix.MPERSP_2]

        val outPixels = IntArray(targetWidth * targetHeight)
        val srcPixels = IntArray(source.width * source.height)
        source.getPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)

        val srcWInt = source.width
        val srcHInt = source.height

        // Muestreo inverso con interpolación bilineal para máxima nitidez
        for (y in 0 until targetHeight) {
            val yOffset = y * targetWidth
            val h01_y_h02 = h01 * y + h02
            val h11_y_h12 = h11 * y + h12
            val h21_y_h22 = h21 * y + h22

            for (x in 0 until targetWidth) {
                val z = h20 * x + h21_y_h22
                val invZ = if (z != 0f) 1f / z else 1f

                val u = (h00 * x + h01_y_h02) * invZ
                val v = (h10 * x + h11_y_h12) * invZ

                val u0 = u.toInt().coerceIn(0, srcWInt - 2)
                val v0 = v.toInt().coerceIn(0, srcHInt - 2)
                val u1 = u0 + 1
                val v1 = v0 + 1

                val fx = (u - u0).coerceIn(0f, 1f)
                val fy = (v - v0).coerceIn(0f, 1f)

                val c00 = srcPixels[v0 * srcWInt + u0]
                val c10 = srcPixels[v0 * srcWInt + u1]
                val c01 = srcPixels[v1 * srcWInt + u0]
                val c11 = srcPixels[v1 * srcWInt + u1]

                val r = (Color.red(c00) * (1 - fx) * (1 - fy) +
                        Color.red(c10) * fx * (1 - fy) +
                        Color.red(c01) * (1 - fx) * fy +
                        Color.red(c11) * fx * fy).toInt().coerceIn(0, 255)

                val g = (Color.green(c00) * (1 - fx) * (1 - fy) +
                        Color.green(c10) * fx * (1 - fy) +
                        Color.green(c01) * (1 - fx) * fy +
                        Color.green(c11) * fx * fy).toInt().coerceIn(0, 255)

                val b = (Color.blue(c00) * (1 - fx) * (1 - fy) +
                        Color.blue(c10) * fx * (1 - fy) +
                        Color.blue(c01) * (1 - fx) * fy +
                        Color.blue(c11) * fx * fy).toInt().coerceIn(0, 255)

                outPixels[yOffset + x] = Color.rgb(r, g, b)
            }
        }

        val outBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        return outBitmap
    }

    /**
     * Filtro "Realce Mágico" (Magic Color) estilo CamScanner:
     * Estima la superficie de iluminación del fondo mediante filtrado integral de ventana grande
     * y normaliza los canales RGB, dejando el papel en blanco puro y las letras/sellos con color intenso.
     */
    fun applyMagicColor(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val total = width * height

        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val grays = IntArray(total)
        for (i in 0 until total) {
            val c = pixels[i]
            grays[i] = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
        }

        // Imagen integral para calcular el fondo de iluminación en tiempo O(1)
        val stride = width + 1
        val integral = LongArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            val rowOffset = (y + 1) * stride
            val prevRowOffset = y * stride
            for (x in 0 until width) {
                rowSum += grays[y * width + x]
                integral[rowOffset + (x + 1)] = integral[prevRowOffset + (x + 1)] + rowSum
            }
        }

        // Radio de ventana grande para estimar la iluminación ambiental del papel (CamScanner Deshadow)
        val radius = max(width / 16, 20)
        val outPixels = IntArray(total)

        for (y in 0 until height) {
            val y1 = max(0, y - radius)
            val y2 = min(height - 1, y + radius)
            val yOffset = y * width

            for (x in 0 until width) {
                val x1 = max(0, x - radius)
                val x2 = min(width - 1, x + radius)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                        integral[y1 * stride + (x2 + 1)] -
                        integral[(y2 + 1) * stride + x1] +
                        integral[y1 * stride + x1]

                val localBg = max(1L, sum / count).toFloat()

                val pixel = pixels[yOffset + x]
                var r = Color.red(pixel).toFloat()
                var g = Color.green(pixel).toFloat()
                var b = Color.blue(pixel).toFloat()

                // Normalización de iluminación (división por el fondo local)
                r = (r / localBg) * 230f
                g = (g / localBg) * 230f
                b = (b / localBg) * 230f

                // Curva de realce CamScanner: blanquear fondo alto (>205) y profundizar tinta oscura (<90)
                if (r > 205f && g > 205f && b > 205f) {
                    r = 255f
                    g = 255f
                    b = 255f
                } else {
                    if (r < 90f) r *= 0.75f
                    if (g < 90f) g *= 0.75f
                    if (b < 90f) b *= 0.75f
                }

                outPixels[yOffset + x] = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Filtro Escala de Grises con nivelación de iluminación y contraste.
     */
    fun applyGrayscaleClean(source: Bitmap): Bitmap {
        val magic = applyMagicColor(source)
        val width = magic.width
        val height = magic.height
        val total = width * height

        val pixels = IntArray(total)
        magic.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(total)
        for (i in 0 until total) {
            val c = pixels[i]
            val gray = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt().coerceIn(0, 255)
            outPixels[i] = Color.rgb(gray, gray, gray)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Filtro "Eco" / Blanco y Negro de Alto Contraste estilo CamScanner:
     * - Blanquea el fondo a blanco puro (#FFFFFF) eliminando sombras, manchas y tonos amarillos.
     * - Maximiza la nitidez e intensidad de letras, firmas y códigos de barra (PDF417, 1D/2D).
     * - Produce el acabado de escaneo limpio tipo fotocopia de alta fidelidad.
     */
    fun applyEcoFilter(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val total = width * height

        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val grays = IntArray(total)
        for (i in 0 until total) {
            val c = pixels[i]
            grays[i] = (299 * Color.red(c) + 587 * Color.green(c) + 114 * Color.blue(c)) / 1000
        }

        // Imagen integral para calcular el fondo de iluminación local en O(1)
        val stride = width + 1
        val integral = LongArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            val rowOffset = (y + 1) * stride
            val prevRowOffset = y * stride
            for (x in 0 until width) {
                rowSum += grays[y * width + x]
                integral[rowOffset + (x + 1)] = integral[prevRowOffset + (x + 1)] + rowSum
            }
        }

        // Radio adaptativo de ventana local
        val radius = max(width / 22, 18)
        val outPixels = IntArray(total)

        for (y in 0 until height) {
            val y1 = max(0, y - radius)
            val y2 = min(height - 1, y + radius)
            val yOffset = y * width

            for (x in 0 until width) {
                val x1 = max(0, x - radius)
                val x2 = min(width - 1, x + radius)

                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                        integral[y1 * stride + (x2 + 1)] -
                        integral[(y2 + 1) * stride + x1] +
                        integral[y1 * stride + x1]

                val localBg = max(1L, sum / count).toFloat()
                val grayVal = grays[yOffset + x].toFloat()

                // Razón de luminosidad relativa respecto al papel circundante
                val ratio = grayVal / localBg
                val delta = localBg - grayVal

                val outVal = when {
                    // Fondo limpio: papel blanco puro
                    ratio > 0.88f || grayVal > 215f || delta < 9f -> 255

                    // Tinta fuerte / código de barras / letras: negro profundo
                    ratio < 0.73f || grayVal < 65f || delta > 26f -> {
                        (grayVal * 0.28f).toInt().coerceIn(0, 45)
                    }

                    // Zona de transición con curva sigmoide suave
                    else -> {
                        val t = (ratio - 0.73f) / (0.88f - 0.73f)
                        val s = t * t * (3f - 2f * t)
                        (s * 255f).toInt().coerceIn(0, 255)
                    }
                }

                outPixels[yOffset + x] = Color.rgb(outVal, outVal, outVal)
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Detección automática de bordes de documentos (4 esquinas / contorno de cédula o papel)
     * estilo CamScanner. Analiza gradientes y contraste con el fondo.
     */
    fun detectDocumentCorners(source: Bitmap): QuadCorners {
        val srcW = source.width
        val srcH = source.height
        if (srcW < 50 || srcH < 50) return QuadCorners()

        // Redimensionar para análisis rápido (máx 240px)
        val maxDim = 240
        val scale = if (srcW > srcH) maxDim.toFloat() / srcW else maxDim.toFloat() / srcH
        val dw = (srcW * scale).toInt().coerceAtLeast(30)
        val dh = (srcH * scale).toInt().coerceAtLeast(30)

        val small = Bitmap.createScaledBitmap(source, dw, dh, true)
        val pixels = IntArray(dw * dh)
        small.getPixels(pixels, 0, dw, 0, 0, dw, dh)

        val grays = IntArray(dw * dh)
        var totalGray = 0L
        for (i in 0 until dw * dh) {
            val c = pixels[i]
            val g = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            grays[i] = g
            totalGray += g
        }
        val avgGray = (totalGray / (dw * dh)).toInt()

        // Estimar luminancia del fondo en el marco perimetral (bordes de la foto)
        var borderSum = 0L
        var borderCount = 0
        for (x in 0 until dw) {
            borderSum += grays[x] + grays[(dh - 1) * dw + x]
            borderCount += 2
        }
        for (y in 1 until dh - 1) {
            borderSum += grays[y * dw] + grays[y * dw + (dw - 1)]
            borderCount += 2
        }
        val borderBg = (borderSum / borderCount).toInt()

        // Gradiente Sobel + detección de bordes
        val grad = IntArray(dw * dh)
        var maxGrad = 0
        for (y in 1 until dh - 1) {
            val yOffset = y * dw
            for (x in 1 until dw - 1) {
                val gx = (grays[yOffset + (x + 1)] - grays[yOffset + (x - 1)]) * 2 +
                        (grays[(y - 1) * dw + (x + 1)] - grays[(y - 1) * dw + (x - 1)]) +
                        (grays[(y + 1) * dw + (x + 1)] - grays[(y + 1) * dw + (x - 1)])
                val gy = (grays[(y + 1) * dw + x] - grays[(y - 1) * dw + x]) * 2 +
                        (grays[(y + 1) * dw + (x - 1)] - grays[(y - 1) * dw + (x - 1)]) +
                        (grays[(y + 1) * dw + (x + 1)] - grays[(y - 1) * dw + (x + 1)])
                val mag = kotlin.math.abs(gx) + kotlin.math.abs(gy)
                grad[yOffset + x] = mag
                if (mag > maxGrad) maxGrad = mag
            }
        }

        // Puntos candidatos del documento:
        val gradThreshold = (maxGrad * 0.22f).toInt().coerceIn(35, 180)
        val isDarkBg = borderBg < avgGray

        val candidateX = ArrayList<Int>()
        val candidateY = ArrayList<Int>()

        // Margen de seguridad del 2%
        val marginX = (dw * 0.02f).toInt()
        val marginY = (dh * 0.02f).toInt()

        for (y in marginY until dh - marginY) {
            val yOff = y * dw
            for (x in marginX until dw - marginX) {
                val g = grays[yOff + x]
                val gr = grad[yOff + x]
                val isDocPixel = if (isDarkBg) {
                    (g > borderBg + 18) || (gr > gradThreshold)
                } else {
                    (g < borderBg - 18) || (gr > gradThreshold)
                }
                if (isDocPixel) {
                    candidateX.add(x)
                    candidateY.add(y)
                }
            }
        }

        if (candidateX.size < (dw * dh * 0.05f)) {
            return fallbackCardCorners(srcW, srcH)
        }

        // Encontrar las 4 esquinas extremas del cuadrilátero:
        var minSum = Int.MAX_VALUE
        var maxSum = Int.MIN_VALUE
        var minDiff = Int.MAX_VALUE
        var maxDiff = Int.MIN_VALUE

        var tlX = 0; var tlY = 0
        var brX = 0; var brY = 0
        var trX = 0; var trY = 0
        var blX = 0; var blY = 0

        for (i in 0 until candidateX.size) {
            val x = candidateX[i]
            val y = candidateY[i]
            val sum = x + y
            val diff = x - y

            if (sum < minSum) { minSum = sum; tlX = x; tlY = y }
            if (sum > maxSum) { maxSum = sum; brX = x; brY = y }
            if (diff > maxDiff) { maxDiff = diff; trX = x; trY = y }
            if (diff < minDiff) { minDiff = diff; blX = x; blY = y }
        }

        val p0 = PointF((tlX.toFloat() / dw).coerceIn(0.02f, 0.98f), (tlY.toFloat() / dh).coerceIn(0.02f, 0.98f))
        val p1 = PointF((trX.toFloat() / dw).coerceIn(0.02f, 0.98f), (trY.toFloat() / dh).coerceIn(0.02f, 0.98f))
        val p2 = PointF((brX.toFloat() / dw).coerceIn(0.02f, 0.98f), (brY.toFloat() / dh).coerceIn(0.02f, 0.98f))
        val p3 = PointF((blX.toFloat() / dw).coerceIn(0.02f, 0.98f), (blY.toFloat() / dh).coerceIn(0.02f, 0.98f))

        val widthNorm = hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble())
        val heightNorm = hypot((p3.x - p0.x).toDouble(), (p3.y - p0.y).toDouble())
        val approxArea = widthNorm * heightNorm

        return if (approxArea in 0.08..0.98 && p1.x > p0.x && p2.y > p1.y && p3.y > p0.y) {
            QuadCorners(topLeft = p0, topRight = p1, bottomRight = p2, bottomLeft = p3)
        } else {
            fallbackCardCorners(srcW, srcH)
        }
    }

    private fun fallbackCardCorners(width: Int, height: Int): QuadCorners {
        val bmpRatio = width.toFloat() / height.toFloat()
        val cardRatio = 85.6f / 53.98f
        var w = 0.88f
        var h = (w * bmpRatio) / cardRatio
        if (h > 0.88f) {
            h = 0.88f
            w = (h * cardRatio) / bmpRatio
        }
        val left = (1f - w) / 2f
        val top = (1f - h) / 2f
        return QuadCorners(
            topLeft = PointF(left, top),
            topRight = PointF(left + w, top),
            bottomRight = PointF(left + w, top + h),
            bottomLeft = PointF(left, top + h)
        )
    }
}

