package com.example.printer365.ui.main

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.printer365.printer.BitmapHelper
import kotlin.math.max
import kotlin.math.min

private enum class DragHandle {
    NONE, INSIDE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT
}

@Composable
fun ImageCropperDialog(
    sourceBitmap: Bitmap,
    onCropConfirmed: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var workingBitmap by remember { mutableStateOf(sourceBitmap) }

    // Coordenadas normalizadas del recuadro de recorte (0.0 a 1.0 respecto al workingBitmap)
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.15f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.85f) }

    var currentDragHandle by remember { mutableStateOf(DragHandle.NONE) }

    fun rotateWorkingBitmap() {
        workingBitmap = BitmapHelper.rotateBitmap(workingBitmap, 90f)
        // Reajustar recuadro a algo sensato
        cropLeft = 0.05f
        cropTop = 0.15f
        cropRight = 0.95f
        cropBottom = 0.85f
    }

    fun applyAspectRatio(targetRatio: Float) {
        // targetRatio = width / height
        val curCenterX = (cropLeft + cropRight) / 2f
        val curCenterY = (cropTop + cropBottom) / 2f

        val bmpAspect = workingBitmap.width.toFloat() / workingBitmap.height.toFloat()
        // En coordenadas normalizadas: normWidth / (normHeight * bmpAspect) = targetRatio
        // normWidth = normHeight * (targetRatio / bmpAspect)
        val relFactor = targetRatio / bmpAspect

        var newHeight = 0.6f
        var newWidth = newHeight * relFactor
        if (newWidth > 0.95f) {
            newWidth = 0.95f
            newHeight = newWidth / relFactor
        }
        if (newHeight > 0.95f) {
            newHeight = 0.95f
            newWidth = newHeight * relFactor
        }

        cropLeft = (curCenterX - newWidth / 2f).coerceIn(0f, 1f - newWidth)
        cropRight = cropLeft + newWidth
        cropTop = (curCenterY - newHeight / 2f).coerceIn(0f, 1f - newHeight)
        cropBottom = cropTop + newHeight
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121212)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.LightGray, fontSize = 16.sp)
                    }
                    Text(
                        text = "📐 Recuadrar Documento",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Button(
                        onClick = {
                            val normRect = RectF(cropLeft, cropTop, cropRight, cropBottom)
                            val cropped = BitmapHelper.cropBitmap(workingBitmap, normRect)
                            onCropConfirmed(cropped)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("✓ Listo", fontWeight = FontWeight.Bold)
                    }
                }

                // Interactive Crop Area
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val containerWidth = constraints.maxWidth.toFloat()
                    val containerHeight = constraints.maxHeight.toFloat()

                    val bmpW = workingBitmap.width.toFloat()
                    val bmpH = workingBitmap.height.toFloat()

                    val scale = min(containerWidth / bmpW, containerHeight / bmpH)
                    val displayedW = bmpW * scale
                    val displayedH = bmpH * scale

                    val offsetX = (containerWidth - displayedW) / 2f
                    val offsetY = (containerHeight - displayedH) / 2f

                    // Detección táctil y de arrastre
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(workingBitmap, displayedW, displayedH) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        val normX = ((pos.x - offsetX) / displayedW).coerceIn(0f, 1f)
                                        val normY = ((pos.y - offsetY) / displayedH).coerceIn(0f, 1f)

                                        val hitRadius = (32.dp.toPx() / displayedW)
                                        val hitRadiusY = (32.dp.toPx() / displayedH)

                                        val nearLeft = kotlin.math.abs(normX - cropLeft) < hitRadius
                                        val nearRight = kotlin.math.abs(normX - cropRight) < hitRadius
                                        val nearTop = kotlin.math.abs(normY - cropTop) < hitRadiusY
                                        val nearBottom = kotlin.math.abs(normY - cropBottom) < hitRadiusY

                                        currentDragHandle = when {
                                            nearLeft && nearTop -> DragHandle.TOP_LEFT
                                            nearRight && nearTop -> DragHandle.TOP_RIGHT
                                            nearLeft && nearBottom -> DragHandle.BOTTOM_LEFT
                                            nearRight && nearBottom -> DragHandle.BOTTOM_RIGHT
                                            nearLeft -> DragHandle.LEFT
                                            nearRight -> DragHandle.RIGHT
                                            nearTop -> DragHandle.TOP
                                            nearBottom -> DragHandle.BOTTOM
                                            normX in cropLeft..cropRight && normY in cropTop..cropBottom -> DragHandle.INSIDE
                                            else -> DragHandle.NONE
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dNormX = dragAmount.x / displayedW
                                        val dNormY = dragAmount.y / displayedH
                                        val minSizeNorm = 0.08f

                                        when (currentDragHandle) {
                                            DragHandle.INSIDE -> {
                                                val w = cropRight - cropLeft
                                                val h = cropBottom - cropTop
                                                var nL = cropLeft + dNormX
                                                var nT = cropTop + dNormY
                                                if (nL < 0f) nL = 0f
                                                if (nL + w > 1f) nL = 1f - w
                                                if (nT < 0f) nT = 0f
                                                if (nT + h > 1f) nT = 1f - h
                                                cropLeft = nL
                                                cropRight = nL + w
                                                cropTop = nT
                                                cropBottom = nT + h
                                            }
                                            DragHandle.TOP_LEFT -> {
                                                cropLeft = (cropLeft + dNormX).coerceIn(0f, cropRight - minSizeNorm)
                                                cropTop = (cropTop + dNormY).coerceIn(0f, cropBottom - minSizeNorm)
                                            }
                                            DragHandle.TOP_RIGHT -> {
                                                cropRight = (cropRight + dNormX).coerceIn(cropLeft + minSizeNorm, 1f)
                                                cropTop = (cropTop + dNormY).coerceIn(0f, cropBottom - minSizeNorm)
                                            }
                                            DragHandle.BOTTOM_LEFT -> {
                                                cropLeft = (cropLeft + dNormX).coerceIn(0f, cropRight - minSizeNorm)
                                                cropBottom = (cropBottom + dNormY).coerceIn(cropTop + minSizeNorm, 1f)
                                            }
                                            DragHandle.BOTTOM_RIGHT -> {
                                                cropRight = (cropRight + dNormX).coerceIn(cropLeft + minSizeNorm, 1f)
                                                cropBottom = (cropBottom + dNormY).coerceIn(cropTop + minSizeNorm, 1f)
                                            }
                                            DragHandle.LEFT -> {
                                                cropLeft = (cropLeft + dNormX).coerceIn(0f, cropRight - minSizeNorm)
                                            }
                                            DragHandle.RIGHT -> {
                                                cropRight = (cropRight + dNormX).coerceIn(cropLeft + minSizeNorm, 1f)
                                            }
                                            DragHandle.TOP -> {
                                                cropTop = (cropTop + dNormY).coerceIn(0f, cropBottom - minSizeNorm)
                                            }
                                            DragHandle.BOTTOM -> {
                                                cropBottom = (cropBottom + dNormY).coerceIn(cropTop + minSizeNorm, 1f)
                                            }
                                            DragHandle.NONE -> Unit
                                        }
                                    },
                                    onDragEnd = { currentDragHandle = DragHandle.NONE }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // 1. Dibujar imagen escalada
                            val bmpImage = workingBitmap.asImageBitmap()
                            drawImage(
                                image = bmpImage,
                                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                                dstSize = IntSize(displayedW.toInt(), displayedH.toInt())
                            )

                            // Coordenadas en píxeles del recuadro
                            val rectPxL = offsetX + cropLeft * displayedW
                            val rectPxT = offsetY + cropTop * displayedH
                            val rectPxR = offsetX + cropRight * displayedW
                            val rectPxB = offsetY + cropBottom * displayedH
                            val rectPxW = rectPxR - rectPxL
                            val rectPxH = rectPxB - rectPxT

                            val dimColor = Color(0xAA000000)

                            // 2. Máscara oscura exterior (4 rectángulos)
                            // Superior
                            drawRect(dimColor, Offset(0f, 0f), Size(size.width, rectPxT))
                            // Inferior
                            drawRect(dimColor, Offset(0f, rectPxB), Size(size.width, size.height - rectPxB))
                            // Izquierda
                            drawRect(dimColor, Offset(0f, rectPxT), Size(rectPxL, rectPxH))
                            // Derecha
                            drawRect(dimColor, Offset(rectPxR, rectPxT), Size(size.width - rectPxR, rectPxH))

                            // 3. Borde del recuadro de recorte
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(rectPxL, rectPxT),
                                size = Size(rectPxW, rectPxH),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // 4. Guías de tercios (cuadrícula 3x3 sutil)
                            val gridColor = Color.White.copy(alpha = 0.35f)
                            val stepX = rectPxW / 3f
                            val stepY = rectPxH / 3f

                            drawLine(gridColor, Offset(rectPxL + stepX, rectPxT), Offset(rectPxL + stepX, rectPxB), strokeWidth = 1.dp.toPx())
                            drawLine(gridColor, Offset(rectPxL + stepX * 2f, rectPxT), Offset(rectPxL + stepX * 2f, rectPxB), strokeWidth = 1.dp.toPx())
                            drawLine(gridColor, Offset(rectPxL, rectPxT + stepY), Offset(rectPxR, rectPxT + stepY), strokeWidth = 1.dp.toPx())
                            drawLine(gridColor, Offset(rectPxL, rectPxT + stepY * 2f), Offset(rectPxR, rectPxT + stepY * 2f), strokeWidth = 1.dp.toPx())

                            // 5. Esquinas reforzadas (estilo visor de cámara)
                            val cornerLen = 22.dp.toPx()
                            val cornerStroke = 4.dp.toPx()
                            val accentColor = Color(0xFF00E676) // Verde brillante

                            // Top-Left
                            drawLine(accentColor, Offset(rectPxL, rectPxT), Offset(rectPxL + cornerLen, rectPxT), cornerStroke)
                            drawLine(accentColor, Offset(rectPxL, rectPxT), Offset(rectPxL, rectPxT + cornerLen), cornerStroke)
                            // Top-Right
                            drawLine(accentColor, Offset(rectPxR, rectPxT), Offset(rectPxR - cornerLen, rectPxT), cornerStroke)
                            drawLine(accentColor, Offset(rectPxR, rectPxT), Offset(rectPxR, rectPxT + cornerLen), cornerStroke)
                            // Bottom-Left
                            drawLine(accentColor, Offset(rectPxL, rectPxB), Offset(rectPxL + cornerLen, rectPxB), cornerStroke)
                            drawLine(accentColor, Offset(rectPxL, rectPxB), Offset(rectPxL, rectPxB - cornerLen), cornerStroke)
                            // Bottom-Right
                            drawLine(accentColor, Offset(rectPxR, rectPxB), Offset(rectPxR - cornerLen, rectPxB), cornerStroke)
                            drawLine(accentColor, Offset(rectPxR, rectPxB), Offset(rectPxR, rectPxB - cornerLen), cornerStroke)
                        }
                    }
                }

                // Barra de herramientas inferior (Presets y Rotación)
                Surface(
                    color = Color(0xFF1E1E1E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Ajuste Rápido de Proporción:",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = { applyAspectRatio(85f / 54f) },
                                label = { Text("🪪 Cédula (85:54)", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = false,
                                onClick = { applyAspectRatio(4f / 3f) },
                                label = { Text("📄 Factura (4:3)", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    cropLeft = 0f
                                    cropTop = 0f
                                    cropRight = 1f
                                    cropBottom = 1f
                                },
                                label = { Text("🔳 Todo", fontSize = 12.sp) },
                                modifier = Modifier.weight(0.7f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { rotateWorkingBitmap() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔄 Rotar 90°", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = {
                                    cropLeft = 0.05f
                                    cropTop = 0.15f
                                    cropRight = 0.95f
                                    cropBottom = 0.85f
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("↺ Centrar", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
