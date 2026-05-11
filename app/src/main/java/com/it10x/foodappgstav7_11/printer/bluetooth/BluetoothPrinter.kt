package com.it10x.foodappgstav7_11.printer.bluetooth

import android.bluetooth.BluetoothAdapter
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.OutputStream
import java.util.UUID
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BluetoothPrinter {

    private const val TAG = "PRINT_BT"

    private val SPP_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val mainHandler = Handler(Looper.getMainLooper())

    // =============================
    // TEST PRINT
    // =============================
    fun printTest(
        mac: String,
        roleLabel: String,
        onResult: (Boolean) -> Unit
    ) {
        printText(
            mac,
            """
            ****************************
                 TEST PRINT
            ****************************
            Printer Role : $roleLabel
            Connection   : BLUETOOTH
            Status       : OK
            ----------------------------
            
            
            """.trimIndent(),
            onResult
        )
    }

    // =============================
    // CORE PRINT (ORDER / AUTO)
    // =============================
    fun printText(
        mac: String,
        text: String,
        onResult: (Boolean) -> Unit
    ) {
        Thread {
            var output: OutputStream? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IllegalStateException("Bluetooth not supported")

                if (!adapter.isEnabled) {
                    throw IllegalStateException("Bluetooth is OFF")
                }

                val device = adapter.getRemoteDevice(mac)
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

                adapter.cancelDiscovery()
                socket.connect()

                output = socket.outputStream

                // ✅ ESC/POS INIT (ONCE)
                output.write(byteArrayOf(0x1B, 0x40))

                val beep = byteArrayOf(
                    0x1B, 0x42, 0x03, 0x02
                )
                output.write(beep)

                // ✅ IMPORTANT: convert LF → CRLF
                val safeText = text
                    .replace("\n", "\r\n")
                    .toByteArray(Charsets.US_ASCII)

                output.write(safeText)

                // ✅ FEED PAPER
                // ✅ FEED 3 LINES + CUT PAPER
                val feedAndCut = byteArrayOf(
                    0x1B, 0x64, 0x03, // Feed 3 lines
                    0x1D, 0x56, 0x01  // Full cut
                )


//                if (prefs.isAutoCutterEnabled()) {
//                    output.write(feedAndCut)
//                } else {
//                    output.write(byteArrayOf(0x0A, 0x0A, 0x0A)) // just feed
//                }

                output.write(feedAndCut)

                output.flush()

                Thread.sleep(300)
                socket.close()

                mainHandler.post { onResult(true) }

            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth print failed", e)
                mainHandler.post { onResult(false) }
            } finally {
                try { output?.close() } catch (_: Exception) {}
            }
        }.start()
    }


    // =============================
// PRINT LOGO + TEXT
// =============================
    fun printLogoAndText(
        mac: String,
        bitmap: Bitmap,
        text: String,
        onResult: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var output: OutputStream? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IllegalStateException("Bluetooth not supported")

                if (!adapter.isEnabled) {
                    throw IllegalStateException("Bluetooth is OFF")
                }

                val device = adapter.getRemoteDevice(mac)
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

                adapter.cancelDiscovery()
                socket.connect()

                output = socket.outputStream



                // INIT
                output.write(byteArrayOf(0x1B, 0x40))

                // 🔔 BEEP (add here)
                val beep = byteArrayOf(0x1B, 0x42, 0x03, 0x02)
                output.write(beep)

// CENTER
                output.write(byteArrayOf(0x1B, 0x61, 0x01))

// IMAGE
                val imageBytes = convertBitmapToRaster(bitmap)
                output.write(imageBytes)

// SPACE
                //         output.write(byteArrayOf(0x0A, 0x0A))

// RESET
                //           output.write(byteArrayOf(0x1B, 0x40))

// LEFT ALIGN
                output.write(byteArrayOf(0x1B, 0x61, 0x00))



// TEXT
                val safeText = text
                    .replace("\n", "\r\n")
                    .toByteArray(Charsets.US_ASCII)

                // output.write(safeText)

                output.write(safeText)

                // FEED + CUT
                output.write(byteArrayOf(
                    0x1B, 0x64, 0x03,
                    0x1D, 0x56, 0x01
                ))

                output.flush()
                Thread.sleep(300)
                socket.close()

                withContext(Dispatchers.Main) {
                    onResult(true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Print with logo failed", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            } finally {
                try { output?.close() } catch (_: Exception) {}
            }
        }
    }



    // =============================
// PRINT LOGO + TEXT + QR (SMART FALLBACK)
// =============================
    fun printLogoTextQr(
        mac: String,
        logoBitmap: Bitmap?,   // nullable
        qrBitmap: Bitmap?,     // nullable
        text: String,
        onResult: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var output: OutputStream? = null

            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IllegalStateException("Bluetooth not supported")

                if (!adapter.isEnabled) {
                    throw IllegalStateException("Bluetooth is OFF")
                }

                val device = adapter.getRemoteDevice(mac)
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

                adapter.cancelDiscovery()
                socket.connect()

                output = socket.outputStream

                // INIT
                output.write(byteArrayOf(0x1B, 0x40))

                // 🔔 BEEP
                output.write(byteArrayOf(0x1B, 0x42, 0x03, 0x02))

                // =============================
                // CENTER ALIGN (for logo & QR)
                // =============================
                output.write(byteArrayOf(0x1B, 0x61, 0x01))

                // =============================
                // PRINT LOGO (if exists)
                // =============================
                if (logoBitmap != null) {
                    val resizedLogo = resizeBitmap(logoBitmap, 384)
                    val logoBytes = convertBitmapToRaster(resizedLogo)
                    output.write(logoBytes)
                    // 🔥 spacing after QR
                    //output.write(byteArrayOf(0x0A))
                }


                // =============================
                // PRINT QR (if exists)
                // =============================
                if (qrBitmap != null) {

                    // small gap before QR
                   // output.write(byteArrayOf(0x0A))

                    // 🛑 CRITICAL: wait BEFORE QR (buffer clear)
                    output.flush()
                    Thread.sleep(300)

                    // center QR
                    // print QR safely
                    printBitmapInChunks(output, qrBitmap)

                    // 🛑 CRITICAL: wait AFTER QR (finish printing)
                    output.flush()
                    Thread.sleep(400)

                    // single minimal feed
                    //output.write(byteArrayOf(0x0A))
                }

                // =============================
                // LEFT ALIGN TEXT
                // =============================
                output.write(byteArrayOf(0x1B, 0x61, 0x00))

                val safeText = text
                    .replace("\n", "\r\n")
                    .toByteArray(Charsets.US_ASCII)

                output.write(safeText)
                output.write(byteArrayOf(0x0A, 0x0A))

                // =============================
                // FEED + CUT
                // =============================
                output.write(byteArrayOf(
                    0x0A, 0x0A,   // safer than ESC d
                    0x1D, 0x56, 0x01
                ))

                output.flush()
                Thread.sleep(300)
                socket.close()

                withContext(Dispatchers.Main) {
                    onResult(true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Print logo + text + QR failed", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            } finally {
                try { output?.close() } catch (_: Exception) {}
            }
        }
    }

    fun printLogoTextQr1(
        mac: String,
        logoBitmap: Bitmap?,   // unused
        qrBitmap: Bitmap?,     // used
        text: String,          // unused
        onResult: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var output: OutputStream? = null

            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IllegalStateException("Bluetooth not supported")

                if (!adapter.isEnabled) {
                    throw IllegalStateException("Bluetooth is OFF")
                }

                val device = adapter.getRemoteDevice(mac)
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

                adapter.cancelDiscovery()
                socket.connect()

                output = socket.outputStream

                // ✅ INIT
                output.write(byteArrayOf(0x1B, 0x40))

                // ❌ REMOVED: beep (faster & silent)

                // ✅ CENTER ALIGN
                output.write(byteArrayOf(0x1B, 0x61, 0x01))

                // =============================
                // PRINT QR ONLY
                // =============================
                if (qrBitmap != null) {
                    printBitmapInChunks(output, qrBitmap)

                    // ✅ minimal feed (avoid cutting QR)
                    output.write(byteArrayOf(0x0A))
                }

                // ❌ REMOVED: big feed
                // ❌ REMOVED: cut (saves paper)

                output.flush()
                Thread.sleep(200)
                socket.close()

                withContext(Dispatchers.Main) {
                    onResult(true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "QR print failed", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            } finally {
                try { output?.close() } catch (_: Exception) {}
            }
        }
    }





    // =============================
// GENERATE QR BITMAP (ZXing)
// =============================
    fun generateQr(text: String, size: Int = 280): Bitmap {
        val writer = QRCodeWriter()

        val hints = hashMapOf<com.google.zxing.EncodeHintType, Any>(
            com.google.zxing.EncodeHintType.MARGIN to 2 // 🔥 IMPORTANT (quiet zone)
        )

        val bitMatrix = writer.encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        for (y in 0 until size) {
            for (x in 0 until size) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                )
            }
        }

        return bitmap
    }


    private fun convertBitmapToRaster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val originalHeight = bitmap.height

//        val padding = 24 // 🔥 extra safety rows
        val height = originalHeight //+ padding

        val bytes = ArrayList<Byte>()
        val bytesPerLine = (width + 7) / 8

        // GS v 0
        bytes.add(0x1D)
        bytes.add(0x76)
        bytes.add(0x30)
        bytes.add(0x00)

        bytes.add((bytesPerLine % 256).toByte())
        bytes.add((bytesPerLine / 256).toByte())

        bytes.add((height % 256).toByte())
        bytes.add((height / 256).toByte())

        for (y in 0 until height) {
            for (x in 0 until bytesPerLine * 8 step 8) {

                var byte = 0

                for (bit in 0 until 8) {
                    val xPos = x + bit

                    if (y < originalHeight && xPos < width) {
                        val pixel = bitmap.getPixel(xPos, y)

                        val r = (pixel shr 16) and 0xff
                        val g = (pixel shr 8) and 0xff
                        val b = pixel and 0xff

                        val gray = (r + g + b) / 3

                        if (gray < 128) {
                            byte = byte or (1 shl (7 - bit))
                        }
                    }
                }

                bytes.add(byte.toByte())
            }
        }

        return bytes.toByteArray()
    }


    private fun printBitmapInChunks(output: OutputStream, bitmap: Bitmap) {
        val chunkHeight = 48

        var y = 0
        while (y < bitmap.height) {
            val height = minOf(chunkHeight, bitmap.height - y)

            val chunk = Bitmap.createBitmap(bitmap, 0, y, bitmap.width, height)
            val bytes = convertBitmapToRaster(chunk)

            output.write(bytes)
            output.flush()

            Thread.sleep(40)   // 🔥 THIS IS MUST

            y += height
        }
    }

    private fun convertBitmapToEscPos(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        val bytes = ArrayList<Byte>()

        for (y in 0 until height step 24) {

            // ESC * m nL nH
            bytes.add(0x1B)
            bytes.add(0x2A)
            bytes.add(33) // 24-dot double density

            bytes.add((width % 256).toByte())
            bytes.add((width / 256).toByte())

            for (x in 0 until width) {
                for (k in 0 until 3) {

                    var slice = 0

                    for (b in 0 until 8) {
                        val yPos = y + (k * 8) + b

                        if (yPos < height) {
                            val pixel = bitmap.getPixel(x, yPos) // ✅ FIXED

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

            // line feed
            bytes.add(0x0A)
        }

        return bytes.toByteArray()
    }

    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val ratio = bitmap.height.toFloat() / bitmap.width
        val height = (targetWidth * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, height, true)
    }


}
