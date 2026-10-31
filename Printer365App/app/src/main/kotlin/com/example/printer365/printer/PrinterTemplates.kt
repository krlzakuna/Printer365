package com.example.printer365.printer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PrinterTemplates {

    /**
     * Plantilla 1: Cuentas Bancarias Oficiales de la Inmobiliaria (LOTE365)
     */
    fun cuentasBancarias(paperWidth: Int = 384): ByteArray {
        val dividerLen = if (paperWidth > 400) 42 else 32
        return EscPosBuilder()
            .initialize()
            .alignCenter()
            .setDoubleSize(true)
            .line("LOTE365")
            .resetFormat()
            .setBold(true)
            .line("INMOBILIARIA & DESARROLLOS")
            .line("RUC: 80123456-7")
            .divider('=', dividerLen)
            .setBold(true)
            .line("CUENTAS BANCARIAS HABILITADAS")
            .resetFormat()
            .line("Titular: LOTE365 S.A.")
            .divider('-', dividerLen)
            .alignLeft()
            .setBold(true).line("1. BANCO CONTINENTAL (Guaraníes)").resetFormat()
            .line("   Cta Cte N°: 14-258963-01")
            .line("   Alias: LOTE365.PY")
            .line("")
            .setBold(true).line("2. BANCO ITAÚ (Guaraníes)").resetFormat()
            .line("   Cta Cte N°: 7854129-02")
            .line("   Alias: LOTES.ITAU")
            .line("")
            .setBold(true).line("3. BANCO GNB (Dólares USD)").resetFormat()
            .line("   Cta Cte N°: 99-887744-05")
            .divider('-', dividerLen)
            .alignCenter()
            .line("IMPORTANTE:")
            .line("Enviar el comprobante de transferencia")
            .line("indicando Nombre, Lote y Manzana.")
            .line("WhatsApp: +595 981 123 456")
            .divider('=', dividerLen)
            .qrCode("https://lote365.com/pagos", size = if (paperWidth > 400) 7 else 5)
            .line("Escanee para validar comprobante")
            .feed(2)
            .cut()
            .build()
    }

    /**
     * Plantilla 2: Recibo Provisorio de Cobranza / Seña de Lote
     */
    fun reciboCobranza(
        cliente: String = "Cliente General",
        lote: String = "Lote 12",
        manzana: String = "Mza 04",
        fraccion: String = "Condominio Km 14 Acaray",
        monto: String = "Gs. 1.500.000",
        concepto: String = "Cuota N° 05 / Seña de Lote",
        asesor: String = "Asesor Comercial Lote365",
        paperWidth: Int = 384
    ): ByteArray {
        val dividerLen = if (paperWidth > 400) 42 else 32
        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        return EscPosBuilder()
            .initialize()
            .alignCenter()
            .setDoubleSize(true)
            .line("LOTE365")
            .resetFormat()
            .setBold(true)
            .line("COMPROBANTE PROVISORIO")
            .line("DE PAGO / RESERVA")
            .resetFormat()
            .divider('=', dividerLen)
            .alignLeft()
            .line("Fecha y Hora: $fecha")
            .line("Cliente: $cliente")
            .divider('-', dividerLen)
            .setBold(true).line("DATOS DEL INMUEBLE:").resetFormat()
            .line("Fracción: $fraccion")
            .line("Manzana: $manzana  |  Lote: $lote")
            .divider('-', dividerLen)
            .line("Concepto: $concepto")
            .alignRight()
            .setBold(true)
            .setDoubleHeight(true)
            .line("TOTAL: $monto")
            .resetFormat()
            .divider('=', dividerLen)
            .alignCenter()
            .line("Asesor: $asesor")
            .line("Gracias por confiar en Lote365!")
            .feed(2)
            .cut()
            .build()
    }

    /**
     * Plantilla 3: Tarjeta de Contacto & QR del Asesor
     */
    fun tarjetaContacto(
        asesor: String = "Equipo Comercial Lote365",
        telefono: String = "+595 981 123 456",
        portalUrl: String = "https://lote365.com",
        paperWidth: Int = 384
    ): ByteArray {
        val dividerLen = if (paperWidth > 400) 42 else 32
        return EscPosBuilder()
            .initialize()
            .alignCenter()
            .setDoubleSize(true)
            .line("LOTE365")
            .resetFormat()
            .line("Tu nuevo hogar comienza aquí")
            .divider('=', dividerLen)
            .setBold(true).line(asesor).resetFormat()
            .line("Teléfono / WhatsApp:")
            .setBold(true).line(telefono).resetFormat()
            .line("Oficina Central: Km 14 Acaray")
            .divider('-', dividerLen)
            .qrCode(portalUrl, size = if (paperWidth > 400) 7 else 5)
            .line("Visite nuestro catálogo de lotes")
            .line(portalUrl)
            .feed(2)
            .cut()
            .build()
    }

    /**
     * Autoprueba de la Impresora
     */
    fun autoprueba(paperWidth: Int = 384): ByteArray {
        val dividerLen = if (paperWidth > 400) 42 else 32
        return EscPosBuilder()
            .initialize()
            .alignCenter()
            .setBold(true)
            .line("PRINTER365 - TEST OK")
            .resetFormat()
            .line("Impresora Térmica Conectada")
            .line("Ancho de papel: ${if (paperWidth > 400) "80 mm (576 dots)" else "58 mm (384 dots)"}")
            .divider('-', dividerLen)
            .alignLeft()
            .line("1. Texto normal")
            .setBold(true).line("2. Texto en negrita").resetFormat()
            .setDoubleHeight(true).line("3. Doble altura").resetFormat()
            .setDoubleSize(true).line("4. Doble tamaño").resetFormat()
            .alignCenter()
            .qrCode("PRINTER365-AUTOPRUEBA-OK", size = 5)
            .line("Código QR de Prueba")
            .feed(2)
            .cut()
            .build()
    }
}

