package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface PlaidApiService {
  @POST("institutions/get")
  suspend fun getInstitutions(@Body body: PlaidInstitutionsGetRequest): PlaidInstitutionsResponse

  @POST("institutions/search")
  suspend fun searchInstitutions(@Body body: PlaidInstitutionsSearchRequest): PlaidInstitutionsResponse

  @POST("sandbox/public_token/create")
  suspend fun createSandboxPublicToken(
    @Body body: PlaidSandboxPublicTokenRequest,
  ): PlaidSandboxPublicTokenResponse

  @POST("item/public_token/exchange")
  suspend fun exchangePublicToken(
    @Body body: PlaidExchangeTokenRequest,
  ): PlaidExchangeTokenResponse

  @POST("accounts/get")
  suspend fun getAccounts(@Body body: PlaidAccountsGetRequest): PlaidAccountsGetResponse

  @POST("transactions/sync")
  suspend fun syncTransactions(
    @Body body: PlaidTransactionsSyncRequest,
  ): PlaidTransactionsSyncResponse

  companion object {
    fun isConfigured(): Boolean {
      val clientId = BuildConfig.PLAID_CLIENT_ID
      val secret = BuildConfig.PLAID_SECRET
      return clientId.isNotBlank() &&
        !clientId.startsWith("YOUR_") &&
        secret.isNotBlank() &&
        !secret.startsWith("YOUR_")
    }

    fun create(): PlaidApiService {
      val logging =
        HttpLoggingInterceptor().apply {
          level = HttpLoggingInterceptor.Level.BASIC
        }
      val client =
        OkHttpClient.Builder()
          .addInterceptor(logging)
          .connectTimeout(20, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .build()

      val moshi =
        Moshi.Builder()
          .addLast(KotlinJsonAdapterFactory())
          .build()

      return Retrofit.Builder()
        .baseUrl(BuildConfig.PLAID_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(PlaidApiService::class.java)
    }
  }
}
