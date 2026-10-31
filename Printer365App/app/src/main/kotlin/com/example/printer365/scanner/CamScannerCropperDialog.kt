package com.example.printer365.scanner

import android.graphics.Bitmap
import android.graphics.PointF
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
import androidx.compose.ui.graphics.Path
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
import kotlin.math.hypot
import kotlin.math.min

private enum class CornerId {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, INSIDE
}

@Composable
fun CamScannerCropperDialog(
    sourceBitmap: Bitmap,
    onConfirmed: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var workingBitmap by remember { mutableStateOf(sourceBitmap) }

    // 4 esquinas del documento en coordenadas normalizadas (0.0 a 1.0)
    var p0 by remember { mutableStateOf(PointF(0.06f, 0.08f)) } // Top-Left
    var p1 by remember { mutableStateOf(PointF(0.94f, 0.08f)) } // Top-Right
    var p2 by remember { mutableStateOf(PointF(0.94f, 0.92f)) } // Bottom-Right
    var p3 by remember { mutableStateOf(PointF(0.06f, 0.92f)) } // Bottom-Left

    var activeCorner by remember { mutableStateOf(CornerId.NONE) }
    var touchPosPx by remember { mutableStateOf(Offset.Zero) }

    fun rotateImage() {
        workingBitmap = BitmapHelper.rotateBitmap(workingBitmap, 90f)
        p0 = PointF(0.06f, 0.08f)
        p1 = PointF(0.94f, 0.08f)
        p2 = PointF(0.94f, 0.92f)
        p3 = PointF(0.06f, 0.92f)
    }

    fun applyCardRatio(ratio: Float) {
        // Centrar un rectángulo con la proporción deseada (ej. 85:54 para cédula)
        val bmpRatio = workingBitmap.width.toFloat() / workingBitmap.height.toFloat()
        var w = 0.88f
        var h = (w * bmpRatio) / ratio
        if (h > 0.88f) {
            h = 0.88f
            w = (h * ratio) / bmpRatio
        }
        val left = (1f - w) / 2f
        val top = (1f - h) / 2f
        p0 = PointF(left, top)
        p1 = PointF(left + w, top)
        p2 = PointF(left + w, top + h)
        p3 = PointF(left, top + h)
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
            color = Color(0xFF101418)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra superior
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1F26))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.LightGray, fontSize = 15.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🔍 Escáner Digitalizador",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Ajusta los 4 puntos a las esquinas",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = {
                            val quad = QuadCorners(p0, p1, p2, p3)
                            val unwarped = DocumentScannerHelper.unwarpPerspective(workingBitmap, quad)
                            onConfirmed(unwarped)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("✓ Enderezar", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                // Área interactiva de recorte por 4 puntos
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
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

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(workingBitmap, displayedW, displayedH) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        touchPosPx = pos
                                        val normX = ((pos.x - offsetX) / displayedW).coerceIn(0f, 1f)
                                        val normY = ((pos.y - offsetY) / displayedH).coerceIn(0f, 1f)

                                        val hitRadiusX = 40.dp.toPx() / displayedW
                                        val hitRadiusY = 40.dp.toPx() / displayedH

                                        fun distSq(p: PointF): Float {
                                            val dx = (normX - p.x) / hitRadiusX
                                            val dy = (normY - p.y) / hitRadiusY
                                            return dx * dx + dy * dy
                                        }

                                        val d0 = distSq(p0)
                                        val d1 = distSq(p1)
                                        val d2 = distSq(p2)
                                        val d3 = distSq(p3)

                                        val minDist = min(min(d0, d1), min(d2, d3))
                                        activeCorner = if (minDist < 1.4f) {
                                            when (minDist) {
                                                d0 -> CornerId.TOP_LEFT
                                                d1 -> CornerId.TOP_RIGHT
                                                d2 -> CornerId.BOTTOM_RIGHT
                                                else -> CornerId.BOTTOM_LEFT
                                            }
                                        } else {
                                            // Comprobar si tocó dentro del cuadrilátero
                                            CornerId.INSIDE
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        touchPosPx += dragAmount
                                        val dNormX = dragAmount.x / displayedW
                                        val dNormY = dragAmount.y / displayedH

                                        when (activeCorner) {
                                            CornerId.TOP_LEFT -> {
                                                p0 = PointF((p0.x + dNormX).coerceIn(0f, 1f), (p0.y + dNormY).coerceIn(0f, 1f))
                                            }
                                            CornerId.TOP_RIGHT -> {
                                                p1 = PointF((p1.x + dNormX).coerceIn(0f, 1f), (p1.y + dNormY).coerceIn(0f, 1f))
                                            }
                                            CornerId.BOTTOM_RIGHT -> {
                                                p2 = PointF((p2.x + dNormX).coerceIn(0f, 1f), (p2.y + dNormY).coerceIn(0f, 1f))
                                            }
                                            CornerId.BOTTOM_LEFT -> {
                                                p3 = PointF((p3.x + dNormX).coerceIn(0f, 1f), (p3.y + dNormY).coerceIn(0f, 1f))
                                            }
                                            CornerId.INSIDE -> {
                                                // Mover todas las esquinas simultáneamente
                                                val maxMoveX = min(
                                                    min(1f - p0.x, 1f - p1.x),
                                                    min(1f - p2.x, 1f - p3.x)
                                                )
                                                val minMoveX = -min(min(p0.x, p1.x), min(p2.x, p3.x))

                                                val maxMoveY = min(
                                                    min(1f - p0.y, 1f - p1.y),
                                                    min(1f - p2.y, 1f - p3.y)
                                                )
                                                val minMoveY = -min(min(p0.y, p1.y), min(p2.y, p3.y))

                                                val safeDx = dNormX.coerceIn(minMoveX, maxMoveX)
                                                val safeDy = dNormY.coerceIn(minMoveY, maxMoveY)

                                                p0 = PointF(p0.x + safeDx, p0.y + safeDy)
                                                p1 = PointF(p1.x + safeDx, p1.y + safeDy)
                                                p2 = PointF(p2.x + safeDx, p2.y + safeDy)
                                                p3 = PointF(p3.x + safeDx, p3.y + safeDy)
                                            }
                                            CornerId.NONE -> Unit
                                        }
                                    },
                                    onDragEnd = { activeCorner = CornerId.NONE }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // 1. Dibujar imagen de fondo
                            val bmpImg = workingBitmap.asImageBitmap()
                            drawImage(
                                image = bmpImg,
                                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                                dstSize = IntSize(displayedW.toInt(), displayedH.toInt())
                            )

                            // Coordenadas absolutas de los 4 puntos
                            val pt0 = Offset(offsetX + p0.x * displayedW, offsetY + p0.y * displayedH)
                            val pt1 = Offset(offsetX + p1.x * displayedW, offsetY + p1.y * displayedH)
                            val pt2 = Offset(offsetX + p2.x * displayedW, offsetY + p2.y * displayedH)
                            val pt3 = Offset(offsetX + p3.x * displayedW, offsetY + p3.y * displayedH)

                            // 2. Dibujar polígono translúcido (sombreado verde CamScanner)
                            val polyPath = Path().apply {
                                moveTo(pt0.x, pt0.y)
                                lineTo(pt1.x, pt1.y)
                                lineTo(pt2.x, pt2.y)
                                lineTo(pt3.x, pt3.y)
                                close()
                            }
                            drawPath(polyPath, color = Color(0x3500E676))

                            // 3. Líneas de contorno
                            val strokeColor = Color(0xFF00E676)
                            val strokeWidth = 2.5.dp.toPx()
                            drawLine(strokeColor, pt0, pt1, strokeWidth)
                            drawLine(strokeColor, pt1, pt2, strokeWidth)
                            drawLine(strokeColor, pt2, pt3, strokeWidth)
                            drawLine(strokeColor, pt3, pt0, strokeWidth)

                            // Puntos medios de los bordes
                            val mid01 = Offset((pt0.x + pt1.x) / 2f, (pt0.y + pt1.y) / 2f)
                            val mid12 = Offset((pt1.x + pt2.x) / 2f, (pt1.y + pt2.y) / 2f)
                            val mid23 = Offset((pt2.x + pt3.x) / 2f, (pt2.y + pt3.y) / 2f)
                            val mid30 = Offset((pt3.x + pt0.x) / 2f, (pt3.y + pt0.y) / 2f)

                            val midDotRadius = 4.dp.toPx()
                            drawCircle(Color.White, midDotRadius, mid01)
                            drawCircle(Color.White, midDotRadius, mid12)
                            drawCircle(Color.White, midDotRadius, mid23)
                            drawCircle(Color.White, midDotRadius, mid30)

                            // 4. Manijas circulares de las 4 esquinas (estilo CamScanner)
                            val cornerRadius = 14.dp.toPx()
                            val cornerInner = 6.dp.toPx()

                            fun drawCornerHandle(pt: Offset, isSelected: Boolean) {
                                // Halo exterior blanco
                                drawCircle(Color.White, cornerRadius + 2.dp.toPx(), pt, style = Stroke(1.5.dp.toPx()))
                                // Círculo exterior verde brillante
                                drawCircle(if (isSelected) Color(0xFFFFD600) else Color(0xFF00E676), cornerRadius, pt)
                                // Centro blanco
                                drawCircle(Color.White, cornerInner, pt)
                            }

                            drawCornerHandle(pt0, activeCorner == CornerId.TOP_LEFT)
                            drawCornerHandle(pt1, activeCorner == CornerId.TOP_RIGHT)
                            drawCornerHandle(pt2, activeCorner == CornerId.BOTTOM_RIGHT)
                            drawCornerHandle(pt3, activeCorner == CornerId.BOTTOM_LEFT)
                        }
                    }
                }

                // Barra inferior de herramientas (Presets y Rotación)
                Surface(
                    color = Color(0xFF1A1F26),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Ajuste Rápido de Forma:",
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
                                onClick = { applyCardRatio(85f / 54f) },
                                label = { Text("🪪 Cédula (85:54)", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = false,
                                onClick = { applyCardRatio(1f / 1.414f) },
                                label = { Text("📄 Factura A4", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    p0 = PointF(0f, 0f)
                                    p1 = PointF(1f, 0f)
                                    p2 = PointF(1f, 1f)
                                    p3 = PointF(0f, 1f)
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
                                onClick = { rotateImage() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔄 Rotar 90°", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = {
                                    p0 = PointF(0.06f, 0.08f)
                                    p1 = PointF(0.94f, 0.08f)
                                    p2 = PointF(0.94f, 0.92f)
                                    p3 = PointF(0.06f, 0.92f)
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

