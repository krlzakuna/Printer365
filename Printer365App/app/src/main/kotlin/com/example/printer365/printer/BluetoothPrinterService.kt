package com.example.printer365.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

sealed class PrinterConnectionState {
    object Disconnected : PrinterConnectionState()
    data class Connecting(val deviceName: String) : PrinterConnectionState()
    data class Connected(val deviceName: String, val address: String) : PrinterConnectionState()
    data class Error(val message: String) : PrinterConnectionState()
}

class BluetoothPrinterService(private val context: Context) {

    companion object {
        // UUID estándar Serial Port Profile (SPP) para impresoras térmicas Bluetooth
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Disconnected)
    val connectionState: StateFlow<PrinterConnectionState> = _connectionState

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun getDeviceDisplayName(device: BluetoothDevice): String {
        return try {
            device.name ?: device.address
        } catch (e: SecurityException) {
            device.address
        } catch (e: Exception) {
            "Impresora Bluetooth"
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        val displayName = getDeviceDisplayName(device)
        _connectionState.value = PrinterConnectionState.Connecting(displayName)

        try {
            // Cancelar escaneo si está activo para evitar lentitud en la conexión
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (_: Exception) {}

            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            newSocket.connect()

            socket = newSocket
            outputStream = newSocket.outputStream
            _connectionState.value = PrinterConnectionState.Connected(
                deviceName = displayName,
                address = device.address
            )
            true
        } catch (e: Exception) {
            disconnect()
            _connectionState.value = PrinterConnectionState.Error("Fallo al conectar: ${e.localizedMessage}")
            false
        }
    }

    suspend fun sendBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val stream = outputStream
        if (stream == null || socket?.isConnected != true) {
            _connectionState.value = PrinterConnectionState.Error("No hay impresora conectada")
            return@withContext false
        }

        try {
            stream.write(bytes)
            stream.flush()
            true
        } catch (e: Exception) {
            _connectionState.value = PrinterConnectionState.Error("Error enviando datos: ${e.localizedMessage}")
            disconnect()
            false
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        outputStream = null
        socket = null
        _connectionState.value = PrinterConnectionState.Disconnected
    }
}
