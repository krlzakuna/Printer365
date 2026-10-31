package com.example.printer365.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer365.printer.PrinterConnectionState
import com.example.printer365.printer.ProcessingMode
import com.example.printer365.scanner.CamScannerCropperDialog
import com.example.printer365.scanner.ScannerFilter
import com.example.printer365.updater.UpdateAvailableDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PrinterViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Launcher para seleccionar factura/imagen desde galería
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setImageUri(it) }
    }

    // Launcher para tomar foto en alta resolución con la cámara nativa
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { viewModel.setImageUri(it) }
        }
    }

    // Launcher de solicitud de permiso de Cámara
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = com.example.printer365.util.FileShareHelper.createCameraTempUri(context)
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            android.widget.Toast.makeText(
                context,
                "Se requiere permiso de cámara para digitalizar documentos",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    fun launchCamera() {
        val hasCameraPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPerm) {
            val uri = com.example.printer365.util.FileShareHelper.createCameraTempUri(context)
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Launcher para permisos de Bluetooth en Android 12+
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.refreshPairedDevices()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val hasScan = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasConnect || !hasScan) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Printer365",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "LOTE365 - Impresión Térmica",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Chip de estado de conexión
                    ConnectionStatusChip(
                        state = uiState.connectionState,
                        onClick = { showDeviceDialog = true }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Facturas") },
                    icon = { Text("📄") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Lote365") },
                    icon = { Text("🏢") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Texto") },
                    icon = { Text("📝") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    label = { Text("Impresora") },
                    icon = { Text("⚙️") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> InvoicesTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onTakePhoto = { launchCamera() }
                )
                1 -> Lote365TemplatesTab(
                    uiState = uiState,
                    viewModel = viewModel
                )
                2 -> FreeTextTab(
                    uiState = uiState,
                    viewModel = viewModel
                )
                3 -> PrinterSettingsTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenDeviceSelector = { showDeviceDialog = true }
                )
            }

            if (uiState.isPrinting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("Imprimiendo en impresora térmica...", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = uiState.pairedDevices,
            onSelect = { device ->
                viewModel.connectDevice(device)
                showDeviceDialog = false
            },
            onDismiss = { showDeviceDialog = false },
            onRefresh = { viewModel.refreshPairedDevices() }
        )
    }

    if (uiState.showCamScannerDialog && uiState.masterBitmap != null) {
        CamScannerCropperDialog(
            sourceBitmap = uiState.masterBitmap!!,
            initialCorners = uiState.detectedCorners,
            onConfirmed = { unwarped ->
                viewModel.applyUnwarpedDocument(unwarped)
            },
            onDismiss = { viewModel.setCamScannerDialogVisible(false) }
        )
    }

    if (uiState.showRenameDialog) {
        RenameDocumentDialog(
            currentName = uiState.documentFileName,
            onConfirm = { newName ->
                viewModel.setDocumentFileName(newName)
                viewModel.setRenameDialogVisible(false)
            },
            onDismiss = { viewModel.setRenameDialogVisible(false) }
        )
    }

    val availableUpdate = uiState.availableUpdate
    if (availableUpdate != null) {
        UpdateAvailableDialog(
            updateInfo = availableUpdate,
            isDownloading = uiState.isDownloadingUpdate,
            downloadProgress = uiState.downloadProgress,
            onStartDownload = { viewModel.startDownloadingUpdate() },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }
}

@Composable
fun ConnectionStatusChip(
    state: PrinterConnectionState,
    onClick: () -> Unit
) {
    val (bgColor, textColor, text) = when (state) {
        is PrinterConnectionState.Connected -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            "🟢 ${state.deviceName}"
        )
        is PrinterConnectionState.Connecting -> Triple(
            Color(0xFFFFF8E1),
            Color(0xFFF57F17),
            "🟡 Conectando..."
        )
        is PrinterConnectionState.Error -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            "⚠️ Error"
        )
        PrinterConnectionState.Disconnected -> Triple(
            Color(0xFFEEEEEE),
            Color(0xFF616161),
            "🔴 Sin Conexión"
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(end = 12.dp)
            .clickable { onClick() }
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// -------------------------------------------------------------
// TAB 1: Facturas y Escáner Digitalizador (Tecnología CamScanner)
// -------------------------------------------------------------
@Composable
fun InvoicesTab(
    uiState: PrinterUiState,
    viewModel: PrinterViewModel,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit
) {
    var viewMode by remember { mutableIntStateOf(0) } // 0 = Documento Digitalizado (Color/HD), 1 = Ticket Térmico

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TARJETA DE CARGA Y HERRAMIENTAS DE ESCÁNER
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Digitalizador de Documentos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Cédulas, facturas, contratos o recibos", fontSize = 12.sp, color = Color.Gray)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onTakePhoto,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                        ) {
                            Text("📷 Cámara", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onPickImage) {
                            Text("📁 Galería")
                        }
                    }
                }

                // Acciones de Escáner 4 Puntos estilo CamScanner
                if (uiState.masterBitmap != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setCamScannerDialogVisible(true) },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                        ) {
                            Text("🔍 Escáner 4 Pts", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.rotateActiveImage() },
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("🔄 Rotar")
                        }
                        if (uiState.unwarpedBitmap != null && uiState.unwarpedBitmap != uiState.masterBitmap) {
                            OutlinedButton(
                                onClick = { viewModel.resetToMasterBitmap() }
                            ) {
                                Text("↺ Reset")
                            }
                        }
                    }
                }
            }
        }

        // BARRA DE RENOMBRADO DE ARCHIVO
        if (uiState.activeBitmap != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nombre del Archivo:",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${uiState.documentFileName}.jpg",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.setRenameDialogVisible(true) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("✏️ Renombrar")
                    }
                }
            }

            // FILTROS DIGITALIZADORES (TECNOLOGÍA CAMSCANNER)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Filtro Digitalizador CamScanner:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = uiState.scannerFilter == ScannerFilter.ECO,
                            onClick = { viewModel.setScannerFilter(ScannerFilter.ECO) },
                            label = { Text("🍃 Eco (B&N)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1.1f)
                        )
                        FilterChip(
                            selected = uiState.scannerFilter == ScannerFilter.MAGIC_COLOR,
                            onClick = { viewModel.setScannerFilter(ScannerFilter.MAGIC_COLOR) },
                            label = { Text("🌟 Mágico", fontSize = 10.5.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = uiState.scannerFilter == ScannerFilter.DOCUMENT_BW,
                            onClick = { viewModel.setScannerFilter(ScannerFilter.DOCUMENT_BW) },
                            label = { Text("📄 B&W Doc", fontSize = 10.5.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = uiState.scannerFilter == ScannerFilter.GRAYSCALE,
                            onClick = { viewModel.setScannerFilter(ScannerFilter.GRAYSCALE) },
                            label = { Text("🌫️ Grises", fontSize = 10.5.sp) },
                            modifier = Modifier.weight(0.9f)
                        )
                        FilterChip(
                            selected = uiState.scannerFilter == ScannerFilter.ORIGINAL,
                            onClick = { viewModel.setScannerFilter(ScannerFilter.ORIGINAL) },
                            label = { Text("🖼️ Orig.", fontSize = 10.5.sp) },
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Text(
                        text = uiState.scannerFilter.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // SELECTOR DE VISTA: DIGITALIZADO COLOR vs TICKET TÉRMICO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = viewMode == 0,
                    onClick = { viewMode = 0 },
                    label = { Text("📱 Digitalizado (Color/HD)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = viewMode == 1,
                    onClick = { viewMode = 1 },
                    label = { Text("🖨️ Ticket Térmico") },
                    modifier = Modifier.weight(1f)
                )
            }

            // VISUALIZADOR DE IMAGEN SEGÚN EL MODO
            if (viewMode == 0) {
                // Vista Documento HD / Realce Mágico
                val activeBmp = uiState.activeBitmap
                if (activeBmp != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "DOCUMENTO DIGITALIZADO (${activeBmp.width}x${activeBmp.height} px)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                            Image(
                                bitmap = activeBmp.asImageBitmap(),
                                contentDescription = "Documento Escaneado",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                            )
                        }
                    }
                }
            } else {
                // Vista Ticket Térmico (WYSIWYG)
                val preview = uiState.previewBitmap
                if (preview != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "VISTA PREVIA DEL PAPEL TÉRMICO (WYSIWYG)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray
                            )
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = "Vista Previa Monocromo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }

            // HUB DE COMPARTIR Y EXPORTAR (WhatsApp, Google Drive, Galería)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📤 Enviar y Compartir Documento:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón Enviar a WhatsApp
                        Button(
                            onClick = { viewModel.shareScannedToWhatsApp() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Text("💬 WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Botón Subir a Google Drive
                        Button(
                            onClick = { viewModel.uploadScannedToGoogleDrive() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                        ) {
                            Text("☁️ Google Drive", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.saveScannedToDevice() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💾 Guardar en Galería del Teléfono")
                    }
                }
            }

            // AJUSTES DE IMPRESIÓN Y BOTÓN IMPRIMIR
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Configuración de Impresión Térmica", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.paperWidthDots == 384,
                            onClick = { viewModel.setPaperWidth(384) },
                            label = { Text("58 mm") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = uiState.paperWidthDots == 576,
                            onClick = { viewModel.setPaperWidth(576) },
                            label = { Text("80 mm") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Invertir Colores (Fondo negro a blanco):", fontSize = 12.sp)
                        Switch(
                            checked = uiState.invertColors,
                            onCheckedChange = { viewModel.setInvertColors(it) }
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.printCurrentDocument() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("🖨️ IMPRIMIR EN TÉRMICA", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.LightGray.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No hay documento cargado", fontWeight = FontWeight.Medium, color = Color.Gray)
                    Text("Selecciona una foto para escanear y digitalizar", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: Secciones Rápidas Inmobiliaria (LOTE365)
// -------------------------------------------------------------
@Composable
fun Lote365TemplatesTab(
    uiState: PrinterUiState,
    viewModel: PrinterViewModel
) {
    var cliente by remember { mutableStateOf("") }
    var fraccion by remember { mutableStateOf("Condominio Km 14 Acaray") }
    var manzana by remember { mutableStateOf("04") }
    var lote by remember { mutableStateOf("12") }
    var monto by remember { mutableStateOf("Gs. 1.500.000") }
    var concepto by remember { mutableStateOf("Cuota N° 05") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Plantillas Inmobiliarias LOTE365", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Imprime información a tus clientes con un solo toque.", fontSize = 13.sp, color = Color.Gray)

        // SECCIÓN 1: Cuentas Bancarias
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🏦 Cuentas Bancarias Oficiales", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Incluye: Banco Continental, Banco Itaú, Banco GNB (USD), RUC, Alias y código QR de validación.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Button(
                    onClick = { viewModel.printTemplateCuentasBancarias() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Imprimir Cuentas (1-Click)")
                }
            }
        }

        // SECCIÓN 2: Recibo Provisorio de Cobranza
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🧾 Recibo Provisorio de Cuota / Reserva", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                OutlinedTextField(
                    value = cliente,
                    onValueChange = { cliente = it },
                    label = { Text("Nombre del Cliente") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fraccion,
                    onValueChange = { fraccion = it },
                    label = { Text("Fracción / Proyecto") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manzana,
                        onValueChange = { manzana = it },
                        label = { Text("Manzana") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lote,
                        onValueChange = { lote = it },
                        label = { Text("Lote") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monto,
                        onValueChange = { monto = it },
                        label = { Text("Monto (Gs. / USD)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = concepto,
                        onValueChange = { concepto = it },
                        label = { Text("Concepto") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        viewModel.printTemplateRecibo(
                            cliente = cliente,
                            lote = lote,
                            manzana = manzana,
                            fraccion = fraccion,
                            monto = monto,
                            concepto = concepto
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Imprimir Recibo (1-Click)")
                }
            }
        }

        // SECCIÓN 3: Tarjeta de Contacto & QR
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📱 Tarjeta de Contacto y Catálogo QR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Imprime un comprobante de contacto con código QR directo al portal y catálogo de lotes.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Button(
                    onClick = { viewModel.printTemplateContacto() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Imprimir Tarjeta (1-Click)")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: Texto Rápido
// -------------------------------------------------------------
@Composable
fun FreeTextTab(
    uiState: PrinterUiState,
    viewModel: PrinterViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Impresión de Texto Libre", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Escribe cualquier mensaje, nota o lista para imprimir inmediatamente.", fontSize = 13.sp, color = Color.Gray)

        OutlinedTextField(
            value = uiState.freeText,
            onValueChange = { viewModel.setFreeText(it) },
            label = { Text("Texto a imprimir...") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Button(
            onClick = { viewModel.printFreeText() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("🖨️ IMPRIMIR TEXTO", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// TAB 4: Ajustes de Impresora y Bluetooth
// -------------------------------------------------------------
@SuppressLint("MissingPermission")
@Composable
fun PrinterSettingsTab(
    uiState: PrinterUiState,
    viewModel: PrinterViewModel,
    onOpenDeviceSelector: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuración de Impresora", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        Card(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Dispositivo Conectado", fontWeight = FontWeight.Bold)

                when (val state = uiState.connectionState) {
                    is PrinterConnectionState.Connected -> {
                        Text("Nombre: ${state.deviceName}", fontWeight = FontWeight.Medium)
                        Text("Dirección MAC: ${state.address}", fontSize = 12.sp, color = Color.Gray)
                    }
                    is PrinterConnectionState.Connecting -> {
                        Text("Conectando con ${state.deviceName}...", color = Color(0xFFF57F17))
                    }
                    is PrinterConnectionState.Error -> {
                        Text(state.message, color = Color.Red, fontSize = 13.sp)
                    }
                    PrinterConnectionState.Disconnected -> {
                        Text("No hay ninguna impresora conectada actualmente.", color = Color.Gray)
                    }
                }

                Button(
                    onClick = onOpenDeviceSelector,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Seleccionar Impresora Bluetooth")
                }
            }
        }

        Card(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ancho de Cabezal Térmico", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.paperWidthDots == 384,
                        onClick = { viewModel.setPaperWidth(384) },
                        label = { Text("58 mm (384 puntos)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = uiState.paperWidthDots == 576,
                        onClick = { viewModel.setPaperWidth(576) },
                        label = { Text("80 mm (576 puntos)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // CARD DE ACTUALIZACIÓN DEL SISTEMA (OTA)
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Actualizaciones del Sistema (OTA)", fontWeight = FontWeight.Bold)
                Text(
                    text = "Versión instalada: v${uiState.currentVersionName} (Compilación ${uiState.currentVersionCode})",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Button(
                    onClick = { viewModel.checkForUpdates(silent = false) },
                    enabled = !uiState.isCheckingUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscando en GitHub...")
                    } else {
                        Text("🔄 Buscar Actualizaciones Ahora")
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.printTest() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Imprimir Autoprueba (Test)")
        }
    }
}

// -------------------------------------------------------------
// Diálogo de Selección de Dispositivos Bluetooth
// -------------------------------------------------------------
@SuppressLint("MissingPermission")
@Composable
fun DeviceSelectionDialog(
    devices: List<android.bluetooth.BluetoothDevice>,
    onSelect: (android.bluetooth.BluetoothDevice) -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impresoras Bluetooth Emparejadas") },
        text = {
            if (devices.isEmpty()) {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("No se encontraron dispositivos emparejados.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Empareja tu impresora térmica en los Ajustes de Bluetooth de tu teléfono y luego vuelve a intentar.", fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(devices) { device ->
                        val devName = try {
                            device.name ?: device.address
                        } catch (_: Exception) {
                            device.address
                        }
                        ListItem(
                            headlineContent = { Text(devName, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(device.address, fontSize = 12.sp, color = Color.Gray) },
                            modifier = Modifier.clickable { onSelect(device) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) { Text("Actualizar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
