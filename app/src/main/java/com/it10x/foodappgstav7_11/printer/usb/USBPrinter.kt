package com.it10x.foodappgstav7_11.printer.usb

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.*
import android.util.Log
import com.it10x.foodappgstav7_11.usb.USBPermissionHelper
import kotlinx.coroutines.*

object USBPrinter {

    private const val TAG = "USBPrinter"
    const val ACTION_USB_PERMISSION = "com.it10x.foodappgstav7_11.USB_PERMISSION"

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var outEndpoint: UsbEndpoint? = null

    // =================================================
    // INIT + PERMISSION
    // =================================================
    fun init(
        context: Context,
        device: UsbDevice,
        onReady: (Boolean) -> Unit
    ) {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        usbDevice = device

        USBPermissionHelper.requestPermission(context, device) {
            try {
                setupConnection(device)
                onReady(true)
            } catch (e: Exception) {
                Log.e(TAG, "USB setup failed", e)
                onReady(false)
            }
        }
    }

    // =================================================
    // USB CONNECTION SETUP
    // =================================================
    private fun setupConnection(device: UsbDevice) {
        val iface = device.getInterface(0)
        outEndpoint = null

        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (
                ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                ep.direction == UsbConstants.USB_DIR_OUT
            ) {
                outEndpoint = ep
                break
            }
        }

        if (outEndpoint == null) {
            throw IllegalStateException("No OUT endpoint found")
        }

        connection = usbManager?.openDevice(device)
            ?: throw IllegalStateException("Unable to open USB device")

        connection?.claimInterface(iface, true)
        Log.d(TAG, "USB printer connected: ${device.deviceName}")
    }

    // =================================================
    // TEST PRINT
    // =================================================
    fun printTest(
        context: Context,
        device: UsbDevice,
        roleLabel: String,
        onResult: (Boolean) -> Unit
    ) {
        init(context, device) { ready ->
            if (!ready) {
                onResult(false)
                return@init
            }

            val testText = """
            ****************************
                 TEST PRINT
            ****************************
            Printer Role : $roleLabel
            Connection   : USB
            Device Name  : ${device.deviceName}
            Status       : OK
            ----------------------------


        """.trimIndent()

//            printText(testText) { success ->
//                onResult(success)
//            }
        }
    }

    // =================================================
    // CORE PRINT (ORDER / AUTO)
    // =================================================
    fun printText(
        context: Context,
        device: UsbDevice,
        text: String,
        onResult: (Boolean) -> Unit
    ) {
        init(context, device) { ready ->

            if (!ready) {
                onResult(false)
                return@init
            }

            val ep = outEndpoint
            val conn = connection

            if (ep == null || conn == null) {
                Log.e(TAG, "USB printer not ready AFTER INIT")
                onResult(false)
                return@init
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val init = byteArrayOf(0x1B, 0x40)
                    val beep = byteArrayOf(0x1B, 0x42, 0x03, 0x02)

                    val feedAndCut = byteArrayOf(
                        0x1B, 0x64, 0x03,
                        0x1D, 0x56, 0x01
                    )

                    val safeText = text.replace("\n", "\r\n")
                        .toByteArray(Charsets.US_ASCII)

                    val data = init + beep + safeText + feedAndCut

                    val sent = conn.bulkTransfer(ep, data, data.size, 5000)

                    withContext(Dispatchers.Main) {
                        onResult(sent > 0)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "USB print error", e)
                    withContext(Dispatchers.Main) {
                        onResult(false)
                    }
                }
            }
        }
    }


    fun printLogoAndText(
        context: Context,
        device: UsbDevice,
        bitmap: Bitmap,
        text: String,
        onResult: (Boolean) -> Unit
    ) {

        // ✅ ALWAYS INIT FIRST
        init(context, device) { ready ->

            if (!ready) {
                Log.e(TAG, "USB init failed")
                onResult(false)
                return@init
            }

            val ep = outEndpoint
            val conn = connection

            if (ep == null || conn == null) {
                Log.e(TAG, "USB printer not ready AFTER INIT")
                onResult(false)
                return@init
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // INIT
                    val init = byteArrayOf(0x1B, 0x40)

                    // 🔔 BEEP
                    val beep = byteArrayOf(0x1B, 0x42, 0x03, 0x02)

                    // CENTER ALIGN
                    val center = byteArrayOf(0x1B, 0x61, 0x01)

                    // LEFT ALIGN
                    val left = byteArrayOf(0x1B, 0x61, 0x00)

                    // IMAGE
                    val imageBytes = convertBitmapToEscPos(bitmap)

                    // TEXT
                    val safeText = text
                        .replace("\n", "\r\n")
                        .toByteArray(Charsets.US_ASCII)

                    // FEED + CUT
                    val feedAndCut = byteArrayOf(
                        0x1B, 0x64, 0x03,
                        0x1D, 0x56, 0x01
                    )

                    // 🔥 FINAL DATA
                    val data = init +
                            beep +
                            center +
                            imageBytes +
                            left +
                            safeText +
                            feedAndCut

                    val sent = conn.bulkTransfer(ep, data, data.size, 5000)

                    withContext(Dispatchers.Main) {
                        onResult(sent > 0)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "USB print logo error", e)
                    withContext(Dispatchers.Main) {
                        onResult(false)
                    }
                }
            }
        }
    }


    private fun convertBitmapToEscPos(bitmap: android.graphics.Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        val bytes = ArrayList<Byte>()

        for (y in 0 until height step 24) {

            bytes.add(0x1B)
            bytes.add(0x2A)
            bytes.add(33)

            bytes.add((width % 256).toByte())
            bytes.add((width / 256).toByte())

            for (x in 0 until width) {
                for (k in 0 until 3) {

                    var slice = 0

                    for (b in 0 until 8) {
                        val yPos = y + (k * 8) + b

                        if (yPos < height) {
                            val pixel = bitmap.getPixel(x, yPos)

                            val r = (pixel shr 16) and 0xff
                            val g = (pixel shr 8) and 0xff
                            val bVal = pixel and 0xff

                            val gray = (r + g + bVal) / 3

                            if (gray < 128) {
                                slice = slice or (1 shl (7 - b))
                            }
                        }
                    }

                    bytes.add(slice.toByte())
                }
            }

            if (y + 24 < height) {
                bytes.add(0x0A)
            }
        }

        return bytes.toByteArray()
    }
    // =================================================
    // DEVICE LIST
    // =================================================
    fun getConnectedUSBDevices(context: Context): List<UsbDevice> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values.toList()
    }

    // =================================================
    // RELEASE
    // =================================================
    fun release() {
        try {
            connection?.close()
        } catch (_: Exception) {}
        connection = null
        outEndpoint = null
        usbDevice = null
    }
}
