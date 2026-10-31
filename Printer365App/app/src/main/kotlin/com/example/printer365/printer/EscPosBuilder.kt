package com.example.printer365.printer

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class EscPosBuilder(private val charset: Charset = Charset.forName("CP858")) {

    private val stream = ByteArrayOutputStream()

    fun initialize(): EscPosBuilder {
        stream.write(byteArrayOf(0x1B, 0x40)) // ESC @
        return this
    }

    fun alignLeft(): EscPosBuilder {
        stream.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0
        return this
    }

    fun alignCenter(): EscPosBuilder {
        stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1
        return this
    }

    fun alignRight(): EscPosBuilder {
        stream.write(byteArrayOf(0x1B, 0x61, 0x02)) // ESC a 2
        return this
    }

    fun setBold(enabled: Boolean): EscPosBuilder {
        stream.write(byteArrayOf(0x1B, 0x45, if (enabled) 0x01 else 0x00)) // ESC E n
        return this
    }

    fun setDoubleHeight(enabled: Boolean): EscPosBuilder {
        val mode = if (enabled) 0x10 else 0x00
        stream.write(byteArrayOf(0x1B, 0x21, mode.toByte())) // ESC ! n
        return this
    }

    fun setDoubleSize(enabled: Boolean): EscPosBuilder {
        val mode = if (enabled) 0x30 else 0x00 // double width + height
        stream.write(byteArrayOf(0x1B, 0x21, mode.toByte()))
        return this
    }

    fun resetFormat(): EscPosBuilder {
        stream.write(byteArrayOf(0x1B, 0x21, 0x00))
        stream.write(byteArrayOf(0x1B, 0x45, 0x00))
        return this
    }

    fun text(text: String): EscPosBuilder {
        val bytes = text.toByteArray(charset)
        stream.write(bytes)
        return this
    }

    fun line(text: String = ""): EscPosBuilder {
        if (text.isNotEmpty()) {
            text(text)
        }
        stream.write(byteArrayOf(0x0A)) // LF
        return this
    }

    fun feed(lines: Int = 1): EscPosBuilder {
        if (lines <= 1) {
            stream.write(byteArrayOf(0x0A))
        } else {
            stream.write(byteArrayOf(0x1B, 0x64, lines.toByte())) // ESC d n
        }
        return this
    }

    fun divider(char: Char = '-', length: Int = 32): EscPosBuilder {
        return line(char.toString().repeat(length))
    }

    fun cut(full: Boolean = true): EscPosBuilder {
        feed(3)
        stream.write(byteArrayOf(0x1D, 0x56, if (full) 0x00 else 0x01)) // GS V m
        return this
    }

    /**
     * Imprime un código QR nativo de ESC/POS
     */
    fun qrCode(data: String, size: Int = 6): EscPosBuilder {
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        val len = dataBytes.size + 3
        val pL = (len % 256).toByte()
        val pH = (len / 256).toByte()

        // 1. Modelo 2 (GS ( k 4 0 49 65 50 0)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
        // 2. Tamaño de módulo (GS ( k 3 0 49 67 size)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, size.toByte()))
        // 3. Nivel de corrección M (GS ( k 3 0 49 69 49)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))
        // 4. Guardar datos en el buffer (GS ( k pL pH 49 80 48 <data>)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        stream.write(dataBytes)
        // 5. Imprimir QR almacenado (GS ( k 3 0 49 81 48)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        feed(1)
        return this
    }

    /**
     * Imprime directamente un Bitmap monocromo procesado (donde los píxeles negros son puntos térmicos
     * y los blancos son papel), garantizando coincidencia 100% WYSIWYG con la vista previa.
     */
    fun rasterMonochromeBitmap(bitmap: Bitmap): EscPosBuilder {
        val width = bitmap.width
        val height = bitmap.height

        val widthBytes = (width + 7) / 8
        val xL = (widthBytes % 256).toByte()
        val xH = (widthBytes / 256).toByte()
        val yL = (height % 256).toByte()
        val yH = (height / 256).toByte()

        // Encabezado GS v 0: [0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH]
        stream.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val imageBytes = ByteArray(widthBytes * height)
        var byteIndex = 0

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var currentByte = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = pixels[y * width + x]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        val lum = (0.299 * r + 0.587 * g + 0.114 * b)

                        // En papel térmico: bit 1 = quemar punto (negro), bit 0 = blanco
                        if (lum < 128.0) {
                            currentByte = currentByte or (1 shl (7 - bit))
                        }
                    }
                }
                imageBytes[byteIndex++] = currentByte.toByte()
            }
        }

        stream.write(imageBytes)
        feed(1)
        return this
    }

    /**
     * Renderiza e inserta una imagen rasterizada mediante comando GS v 0.
     * @param originalBitmap Imagen fuente
     * @param targetWidth Ancho en puntos de la impresora (384 para 58mm, 576 para 80mm)
     * @param invertColors Si es true, invierte los píxeles (convierte fondos negros en blanco y texto blanco en negro)
     * @param threshold Umbral de corte blanco/negro (0-255, default 128)
     */
    fun rasterImage(
        originalBitmap: Bitmap,
        targetWidth: Int = 384,
        invertColors: Boolean = false,
        threshold: Int = 128
    ): EscPosBuilder {
        // Redimensionar manteniendo relación de aspecto
        val aspectRatio = originalBitmap.height.toFloat() / originalBitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)

        val width = scaledBitmap.width
        val height = scaledBitmap.height

        // El ancho en bytes debe ser múltiplo de 8 bits
        val widthBytes = (width + 7) / 8
        val xL = (widthBytes % 256).toByte()
        val xH = (widthBytes / 256).toByte()
        val yL = (height % 256).toByte()
        val yH = (height / 256).toByte()

        // Encabezado GS v 0: [0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH]
        stream.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val imageBytes = ByteArray(widthBytes * height)
        var byteIndex = 0

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var currentByte = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = pixels[y * width + x]
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)

                        // Luminancia estándar
                        var gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                        // INVERSIÓN DE COLOR:
                        // Si invertColors es true: fondo negro (gray cercano a 0) se convierte en blanco (255)
                        // y texto blanco (gray cercano a 255) se convierte en negro (0).
                        if (invertColors) {
                            gray = 255 - gray
                        }

                        // En papel térmico: bit 1 = punto quemado (negro), bit 0 = papel (blanco)
                        if (gray < threshold) {
                            currentByte = currentByte or (1 shl (7 - bit))
                        }
                    }
                }
                imageBytes[byteIndex++] = currentByte.toByte()
            }
        }

        stream.write(imageBytes)
        feed(1)
        return this
    }

    fun build(): ByteArray {
        return stream.toByteArray()
    }
}

