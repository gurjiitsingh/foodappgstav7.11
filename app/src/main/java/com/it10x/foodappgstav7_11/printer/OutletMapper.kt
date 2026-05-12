package com.it10x.foodappgstav7_11.data.print


import com.it10x.foodappgstav7_11.data.pos.entities.config.OutletEntity

/**
 * Utility mapper to safely build OutletInfo from database entity.
 */
object OutletMapper {

    fun fromEntity(outlet: OutletEntity?): OutletInfo {
        return outlet?.let {
            OutletInfo(
                outletId = it.outletId,

                outletName = it.outletName.takeIf { it.isNotBlank() } ?: "FOOD APP",

                addressLine1 = it.addressLine1.takeIf { it.isNotBlank() } ?: "",
                addressLine2 = it.addressLine2?.takeIf { it.isNotBlank() },
                addressLine3 = it.addressLine3?.takeIf { it.isNotBlank() },

                city = it.city.takeIf { it.isNotBlank() },
                state = it.state?.takeIf { it.isNotBlank() },
                zipcode = it.zipcode?.takeIf { it.isNotBlank() },
                country = it.country?.takeIf { it.isNotBlank() },

                taxType = it.taxType?.takeIf { it.isNotBlank() },
                gstVatNumber = it.gstVatNumber?.takeIf { it.isNotBlank() },

                phone = it.phone.takeIf { it.isNotBlank() },
                phone2 = it.phone2?.takeIf { it.isNotBlank() },

                email = it.email?.takeIf { it.isNotBlank() },
                web = it.web?.takeIf { it.isNotBlank() },

                printerWidth = it.printerWidth,
                printerName = it.printerName?.takeIf { it.isNotBlank() },

                footerNote = it.footerNote?.takeIf { it.isNotBlank() },

                // ✅ QR
                qrEnabled = it.qrEnabled,
                qrText = it.qrText?.takeIf { qr -> qr.isNotBlank() },
                qrTitle = it.qrTitle?.takeIf { qr -> qr.isNotBlank() },

                isActive = it.isActive,

                defaultCurrency = it.defaultCurrency
                    ?.takeIf { it.isNotBlank() } ?: "₹",

                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        } ?: OutletInfo(outletName = "FOOD APP")
    }
}