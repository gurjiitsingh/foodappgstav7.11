package com.it10x.foodappgstav7_11.network

import com.it10x.foodappgstav7_11.data.models.ClientResponse

object ApiClient {

    suspend fun getClientConfig(clientId: String): ClientResponse {
        return RetrofitInstance.api.getClientConfig(clientId)
    }
}