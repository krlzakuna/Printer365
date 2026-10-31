package com.example.printer365.ui.main

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.printer365.printer.BitmapHelper
import com.example.printer365.printer.BluetoothPrinterService
import com.example.printer365.printer.EscPosBuilder
import com.example.printer365.printer.PrinterConnectionState
import com.example.printer365.printer.PrinterTemplates
import com.example.printer365.printer.ProcessingMode
import com.example.printer365.scanner.DocumentScannerHelper
import com.example.printer365.scanner.ScannerFilter
import com.example.printer365.updater.AppUpdateInfo
import com.example.printer365.updater.UpdateManager
import com.example.printer365.util.FileShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PrinterUiState(
    val connectionState: PrinterConnectionState = PrinterConnectionState.Disconnected,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val paperWidthDots: Int = 384, // 384 = 58mm, 576 = 80mm
    val selectedImageUri: Uri? = null,
    val masterBitmap: Bitmap? = null,      // Imagen original sin recortar
    val unwarpedBitmap: Bitmap? = null,    // Imagen enderezada por perspectiva
    val activeBitmap: Bitmap? = null,      // Imagen filtrada a color / vista escáner
    val previewBitmap: Bitmap? = null,     // Vista previa monocromo para impresora térmica
    val scannerFilter: ScannerFilter = ScannerFilter.ECO,
    val processingMode: ProcessingMode = ProcessingMode.DOCUMENT_ADAPTIVE,
    val documentFileName: String = FileShareHelper.generateDefaultFileName(),
    val invertColors: Boolean = false,
    val adaptiveSensitivity: Float = 14f,  // 5% a 30%
    val manualThreshold: Float = 128f,     // 0 a 255
    val contrast: Float = 20f,             // -50 a 100
    val showCamScannerDialog: Boolean = false,
    val detectedCorners: com.example.printer365.scanner.QuadCorners? = null,
    val showRenameDialog: Boolean = false,
    val currentVersionCode: Int = 3,
    val currentVersionName: String = "1.2",
    val updateUrl: String = UpdateManager.DEFAULT_UPDATE_URL,
    val availableUpdate: AppUpdateInfo? = null,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val isCheckingUpdate: Boolean = false,
    val freeText: String = "",
    val statusMessage: String? = null,
    val isPrinting: Boolean = false
)

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

    val printerService = BluetoothPrinterService(application.applicationContext)

    private val _uiState = MutableStateFlow(PrinterUiState())
    val uiState: StateFlow<PrinterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            printerService.connectionState.collect { connState ->
                _uiState.value = _uiState.value.copy(connectionState = connState)
            }
        }
        refreshPairedDevices()
        checkForUpdates(silent = true)
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        try {
            val devices = printerService.getPairedDevices()
            _uiState.value = _uiState.value.copy(pairedDevices = devices)
        } catch (_: Exception) {}
    }

    fun connectDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            printerService.connect(device)
        }
    }

    fun setPaperWidth(dots: Int) {
        _uiState.value = _uiState.value.copy(paperWidthDots = dots)
        updatePreview()
    }

    fun setScannerFilter(filter: ScannerFilter) {
        _uiState.value = _uiState.value.copy(scannerFilter = filter)
        applyScannerFilterToActive()
    }

    fun setProcessingMode(mode: ProcessingMode) {
        _uiState.value = _uiState.value.copy(processingMode = mode)
        updatePreview()
    }

    fun setInvertColors(invert: Boolean) {
        _uiState.value = _uiState.value.copy(invertColors = invert)
        updatePreview()
    }

    fun setAdaptiveSensitivity(value: Float) {
        _uiState.value = _uiState.value.copy(adaptiveSensitivity = value)
        updatePreview()
    }

    fun setManualThreshold(value: Float) {
        _uiState.value = _uiState.value.copy(manualThreshold = value)
        updatePreview()
    }

    fun setContrast(value: Float) {
        _uiState.value = _uiState.value.copy(contrast = value)
        updatePreview()
    }

    fun setDocumentFileName(name: String) {
        val clean = name.trim().replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        _uiState.value = _uiState.value.copy(documentFileName = clean)
    }

    fun setCamScannerDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showCamScannerDialog = visible)
    }

    fun setRenameDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showRenameDialog = visible)
    }

    fun rotateActiveImage() {
        val current = _uiState.value.unwarpedBitmap ?: _uiState.value.activeBitmap ?: return
        viewModelScope.launch {
            val rotated = withContext(Dispatchers.Default) {
                BitmapHelper.rotateBitmap(current, 90f)
            }
            _uiState.value = _uiState.value.copy(unwarpedBitmap = rotated)
            applyScannerFilterToActive()
        }
    }

    fun applyUnwarpedDocument(unwarped: Bitmap) {
        _uiState.value = _uiState.value.copy(
            unwarpedBitmap = unwarped,
            showCamScannerDialog = false
        )
        applyScannerFilterToActive()
        showToast("¡Documento enderezado exitosamente!")
    }

    fun resetToMasterBitmap() {
        val master = _uiState.value.masterBitmap ?: return
        _uiState.value = _uiState.value.copy(
            unwarpedBitmap = master,
            activeBitmap = master
        )
        applyScannerFilterToActive()
        showToast("Imagen restaurada al original")
    }

    private fun applyScannerFilterToActive() {
        val base = _uiState.value.unwarpedBitmap ?: _uiState.value.masterBitmap ?: return
        val filter = _uiState.value.scannerFilter

        viewModelScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                when (filter) {
                    ScannerFilter.ECO -> DocumentScannerHelper.applyEcoFilter(base)
                    ScannerFilter.MAGIC_COLOR -> DocumentScannerHelper.applyMagicColor(base)
                    ScannerFilter.DOCUMENT_BW -> {
                        BitmapHelper.processImage(
                            source = base,
                            targetWidth = base.width.coerceAtMost(1600),
                            mode = ProcessingMode.DOCUMENT_ADAPTIVE,
                            invertColors = false,
                            adaptiveSensitivity = _uiState.value.adaptiveSensitivity
                        )
                    }
                    ScannerFilter.GRAYSCALE -> DocumentScannerHelper.applyGrayscaleClean(base)
                    ScannerFilter.ORIGINAL -> base
                }
            }
            _uiState.value = _uiState.value.copy(activeBitmap = filtered)
            updatePreview()
        }
    }

    fun setFreeText(text: String) {
        _uiState.value = _uiState.value.copy(freeText = text)
    }

    fun setImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val bitmap = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }

                // Autodetección inmediata de bordes estilo CamScanner
                val detected = withContext(Dispatchers.Default) {
                    DocumentScannerHelper.detectDocumentCorners(bitmap)
                }

                _uiState.value = _uiState.value.copy(
                    selectedImageUri = uri,
                    masterBitmap = bitmap,
                    unwarpedBitmap = bitmap,
                    activeBitmap = bitmap,
                    detectedCorners = detected,
                    showCamScannerDialog = true, // Abre automáticamente el visor de recorte con las 4 esquinas detectadas
                    documentFileName = FileShareHelper.generateDefaultFileName()
                )
                applyScannerFilterToActive()
            } catch (e: Exception) {
                showToast("Error al cargar la imagen: ${e.localizedMessage}")
            }
        }
    }

    fun reDetectCorners() {
        val master = _uiState.value.masterBitmap ?: return
        viewModelScope.launch {
            val detected = withContext(Dispatchers.Default) {
                DocumentScannerHelper.detectDocumentCorners(master)
            }
            _uiState.value = _uiState.value.copy(detectedCorners = detected)
        }
    }

    private fun updatePreview() {
        val active = _uiState.value.activeBitmap ?: return
        val currentWidth = _uiState.value.paperWidthDots
        val mode = _uiState.value.processingMode
        val invert = _uiState.value.invertColors
        val sensitivity = _uiState.value.adaptiveSensitivity
        val manualThresh = _uiState.value.manualThreshold
        val contrast = _uiState.value.contrast

        viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                BitmapHelper.processImage(
                    source = active,
                    targetWidth = currentWidth,
                    mode = mode,
                    invertColors = invert,
                    adaptiveSensitivity = sensitivity,
                    manualThreshold = manualThresh,
                    contrast = contrast
                )
            }
            _uiState.value = _uiState.value.copy(previewBitmap = preview)
        }
    }

    fun shareScannedToWhatsApp() {
        val bitmapToShare = _uiState.value.activeBitmap
        if (bitmapToShare == null) {
            showToast("Selecciona o escanea un documento primero")
            return
        }
        val context = getApplication<Application>().applicationContext
        val fileName = _uiState.value.documentFileName

        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                FileShareHelper.saveBitmapToSharedCache(context, bitmapToShare, fileName)
            }
            val uri = FileShareHelper.getFileUri(context, file)
            FileShareHelper.shareToWhatsApp(context, uri, "Documento: $fileName (Escaneado con Printer365)")
        }
    }

    fun uploadScannedToGoogleDrive() {
        val bitmapToShare = _uiState.value.activeBitmap
        if (bitmapToShare == null) {
            showToast("Selecciona o escanea un documento primero")
            return
        }
        val context = getApplication<Application>().applicationContext
        val fileName = _uiState.value.documentFileName

        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                FileShareHelper.saveBitmapToSharedCache(context, bitmapToShare, fileName)
            }
            val uri = FileShareHelper.getFileUri(context, file)
            FileShareHelper.uploadToGoogleDrive(context, uri, fileName)
        }
    }

    fun saveScannedToDevice() {
        val bitmap = _uiState.value.activeBitmap
        if (bitmap == null) {
            showToast("Selecciona o escanea un documento primero")
            return
        }
        val context = getApplication<Application>().applicationContext
        val fileName = _uiState.value.documentFileName

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                FileShareHelper.saveToDevicePublic(context, bitmap, fileName)
            }
        }
    }

    fun printCurrentDocument() {
        val preview = _uiState.value.previewBitmap
        if (preview == null) {
            showToast("Selecciona una factura o imagen primero")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrinting = true)
            val bytes = withContext(Dispatchers.Default) {
                EscPosBuilder()
                    .initialize()
                    .alignCenter()
                    .rasterMonochromeBitmap(preview)
                    .feed(2)
                    .cut()
                    .build()
            }
            val success = printerService.sendBytes(bytes)
            _uiState.value = _uiState.value.copy(isPrinting = false)
            if (success) {
                showToast("¡Documento impreso con éxito!")
            }
        }
    }

    fun printFreeText() {
        val text = _uiState.value.freeText
        if (text.isBlank()) {
            showToast("El texto está vacío")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrinting = true)
            val bytes = withContext(Dispatchers.Default) {
                EscPosBuilder()
                    .initialize()
                    .alignLeft()
                    .line(text)
                    .feed(2)
                    .cut()
                    .build()
            }
            val success = printerService.sendBytes(bytes)
            _uiState.value = _uiState.value.copy(isPrinting = false)
            if (success) {
                showToast("¡Texto impreso!")
            }
        }
    }

    fun printTemplateCuentasBancarias() {
        val width = _uiState.value.paperWidthDots
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrinting = true)
            val bytes = PrinterTemplates.cuentasBancarias(width)
            val success = printerService.sendBytes(bytes)
            _uiState.value = _uiState.value.copy(isPrinting = false)
            if (success) {
                showToast("¡Cuentas Bancarias impresas!")
            }
        }
    }

    fun printTemplateRecibo(
        cliente: String,
        lote: String,
        manzana: String,
        fraccion: String,
        monto: String,
        concepto: String
    ) {
        val width = _uiState.value.paperWidthDots
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrinting = true)
            val bytes = PrinterTemplates.reciboCobranza(
                cliente = cliente.ifBlank { "Cliente Lote365" },
                lote = lote.ifBlank { "Lote" },
                manzana = manzana.ifBlank { "Mza" },
                fraccion = fraccion.ifBlank { "Condominio Km 14 Acaray" },
                monto = monto.ifBlank { "Gs. 0" },
                concepto = concepto.ifBlank { "Pago de Cuota" },
                paperWidth = width
            )
            val success = printerService.sendBytes(bytes)
            _uiState.value = _uiState.value.copy(isPrinting = false)
            if (success) {
                showToast("¡Recibo impreso con éxito!")
            }
        }
    }

    fun printTemplateContacto() {
        val width = _uiState.value.paperWidthDots
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrinting = true)
            val bytes = PrinterTemplates.tarjetaContacto(paperWidth = width)
            val success = printerService.sendBytes(bytes)
            _uiState.value = _uiState.value.copy(isPrinting = false)
            if (success) {
                showToast("¡Tarjeta de Contacto impresa!")
            }
        }
    }

    fun printTest() {
        val width = _uiState.value.paperWidthDots
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrinting = true)
            val bytes = PrinterTemplates.autoprueba(width)
            val success = printerService.sendBytes(bytes)
            _uiState.value = _uiState.value.copy(isPrinting = false)
            if (success) {
                showToast("¡Autoprueba enviada a la impresora!")
            }
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingUpdate = true)
            val info = UpdateManager.checkForUpdate(
                currentVersionCode = _uiState.value.currentVersionCode,
                updateUrl = _uiState.value.updateUrl
            )
            _uiState.value = _uiState.value.copy(
                isCheckingUpdate = false,
                availableUpdate = info
            )
            if (info == null && !silent) {
                showToast("¡Printer365 está actualizado a la última versión!")
            }
        }
    }

    fun startDownloadingUpdate() {
        val updateInfo = _uiState.value.availableUpdate ?: return
        val context = getApplication<Application>().applicationContext

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloadingUpdate = true, downloadProgress = 0f)
            UpdateManager.downloadAndInstall(
                context = context,
                updateInfo = updateInfo,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(downloadProgress = progress)
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(isDownloadingUpdate = false)
                    showToast(error)
                }
            )
            _uiState.value = _uiState.value.copy(isDownloadingUpdate = false)
        }
    }

    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(availableUpdate = null)
    }

    fun setUpdateUrl(url: String) {
        _uiState.value = _uiState.value.copy(updateUrl = url.trim())
    }

    private fun showToast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }
}
