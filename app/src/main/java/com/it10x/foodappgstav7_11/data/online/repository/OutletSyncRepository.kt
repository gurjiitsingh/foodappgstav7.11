package com.it10x.foodappgstav7_11.data.online.models.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.it10x.foodappgstav7_11.data.pos.AppDatabase
import com.it10x.foodappgstav7_11.data.pos.entities.config.OutletEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OutletSyncRepository(
    private val db: AppDatabase,
    private val context: android.content.Context
) {

    private val firestore = FirebaseFirestore.getInstance()

    // --------------------------------------------------
    // ⭐ DOWNLOAD & SAVE SINGLE OUTLET
    // --------------------------------------------------
    suspend fun syncOutlet() = withContext(Dispatchers.IO) {

        val snapshot = firestore
            .collection("outlets")
            .limit(1)     // ✅ SINGLE OUTLET DESIGN
            .get()
            .await()

        if (snapshot.isEmpty) {
            Log.w("SYNC_OUTLET", "No outlet found in Firestore")
            db.outletDao().deleteOutlet()
            return@withContext
        }

        val doc = snapshot.documents.first()
        val data = doc.data ?: return@withContext

        val outlet = OutletEntity(

            // 🔑 Firestore doc id
            outletId = doc.id,

            outletName = data["outletName"] as? String ?: "",
            ownerId = data["ownerId"] as? String ?: "",
            // ---------- ADDRESS ----------
            addressLine1 = data["addressLine1"] as? String ?: "",
            addressLine2 = data["addressLine2"] as? String,
            addressLine3 = data["addressLine3"] as? String,   // ⭐ NEW
            city = data["city"] as? String ?: "",
            state = data["state"] as? String,
            zipcode = data["zipcode"] as? String,
            country = data["country"] as? String,

            // ---------- TAX ----------
            taxType = data["taxType"] as? String,
            gstVatNumber = data["gstVatNumber"] as? String,

            // ---------- CONTACT ----------
            phone = data["phone"] as? String ?: "",
            phone2 = data["phone2"] as? String,
            email = data["email"] as? String,
            web = data["web"] as? String,                    // ⭐ NEW
            logoUrl = data["logoUrl"] as? String,
            // ---------- POS / PRINTER ----------
            printerWidth = when ((data["printerWidth"] as? Number)?.toInt()) {
                58 -> 58
                else -> 80
            },
            printerName = data["printerName"] as? String,
            footerNote = data["footerNote"] as? String,

            // ---------- STATUS ----------
            isActive = data["isActive"] as? Boolean ?: true,
            // ---------- NEW: DEFAULT CURRENCY ----------
            defaultCurrency = data["defaultCurrency"] as? String ?: "₹"
        )

        Log.d(
            "SYNC_OUTLET",
            """
Outlet Synced:
Name       = ${outlet.outletName}
Addr1      = ${outlet.addressLine1}
Addr2      = ${outlet.addressLine2 ?: "-"}
Addr3      = ${outlet.addressLine3 ?: "-"}
City       = ${outlet.city}
Phone1     = ${outlet.phone}
Phone2     = ${outlet.phone2 ?: "-"}
Email      = ${outlet.email ?: "-"}
Web        = ${outlet.web ?: "-"}
Logo       = ${'$'}{outlet.logoUrl ?: "No Logo"}
GST/VAT    = ${outlet.gstVatNumber ?: "N/A"}
Printer    = ${outlet.printerWidth}mm
""".trimIndent()
        )

        // ✅ Single-row table → replace
        db.outletDao().deleteOutlet()
        db.outletDao().saveOutlet(outlet)


        // --------------------------------------------
// ✅ DOWNLOAD LOGO AFTER SAVING OUTLET
// --------------------------------------------
        outlet.logoUrl?.let { url ->
            try {
                Log.d("LOGO_DEBUG", "Downloading from: $url")
                val logoFile = java.io.File(context.filesDir, "logo.png")

                val bitmap = downloadBitmapFromUrl(url)
                bitmap?.let {
                    java.io.FileOutputStream(logoFile).use { out ->
                        it.compress(
                            android.graphics.Bitmap.CompressFormat.PNG,
                            100,
                            out
                        )
                    }
                    Log.d("LOGO_DEBUG", "Saved at: ${logoFile.absolutePath}")
                    Log.d("LOGO_DEBUG", "File exists: ${logoFile.exists()}")
                    Log.d("LOGO_DEBUG", "File size: ${logoFile.length()}")
                    Log.d("SYNC_OUTLET", "Logo downloaded/updated")
                }

            } catch (e: Exception) {
                Log.e("SYNC_OUTLET", "Logo download failed", e)
            }
        }

     //   Log.d("SYNC_OUTLET", "Outlet saved into Room")
    }

    private fun downloadBitmapFromUrl(url: String): Bitmap? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connect()

            val input = connection.inputStream
            val bytes = input.readBytes()

            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (bitmap == null) {
                Log.e("LOGO_DEBUG", "Bitmap decode FAILED ❌")
            } else {
                Log.d("LOGO_DEBUG", "Bitmap decode SUCCESS ✅")
            }

            bitmap

        } catch (e: Exception) {
            Log.e("LOGO_DEBUG", "Download error ❌", e)
            null
        }
    }

}
