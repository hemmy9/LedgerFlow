package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlaidInstitutionsGetRequest(
  @Json(name = "client_id") val clientId: String,
  val secret: String,
  val count: Int = 15,
  val offset: Int = 0,
  @Json(name = "country_codes") val countryCodes: List<String> = listOf("US"),
)

@JsonClass(generateAdapter = true)
data class PlaidInstitutionsSearchRequest(
  @Json(name = "client_id") val clientId: String,
  val secret: String,
  val query: String,
  val products: List<String> = listOf("transactions"),
  @Json(name = "country_codes") val countryCodes: List<String> = listOf("US"),
)

@JsonClass(generateAdapter = true)
data class PlaidInstitutionsResponse(
  val institutions: List<PlaidInstitutionDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PlaidInstitutionDto(
  @Json(name = "institution_id") val institutionId: String,
  val name: String,
  val products: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PlaidSandboxPublicTokenRequest(
  @Json(name = "client_id") val clientId: String,
  val secret: String,
  @Json(name = "institution_id") val institutionId: String,
  @Json(name = "initial_products") val initialProducts: List<String> = listOf("transactions"),
)

@JsonClass(generateAdapter = true)
data class PlaidSandboxPublicTokenResponse(
  @Json(name = "public_token") val publicToken: String,
)

@JsonClass(generateAdapter = true)
data class PlaidExchangeTokenRequest(
  @Json(name = "client_id") val clientId: String,
  val secret: String,
  @Json(name = "public_token") val publicToken: String,
)

@JsonClass(generateAdapter = true)
data class PlaidExchangeTokenResponse(
  @Json(name = "access_token") val accessToken: String,
  @Json(name = "item_id") val itemId: String,
)

@JsonClass(generateAdapter = true)
data class PlaidAccountsGetRequest(
  @Json(name = "client_id") val clientId: String,
  val secret: String,
  @Json(name = "access_token") val accessToken: String,
)

@JsonClass(generateAdapter = true)
data class PlaidAccountsGetResponse(
  val accounts: List<PlaidAccountDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PlaidAccountDto(
  @Json(name = "account_id") val accountId: String,
  val name: String,
  @Json(name = "official_name") val officialName: String? = null,
  val mask: String? = null,
  val type: String = "depository",
  val subtype: String? = null,
  val balances: PlaidBalancesDto,
)

@JsonClass(generateAdapter = true)
data class PlaidBalancesDto(
  val current: Double? = null,
  val available: Double? = null,
  @Json(name = "iso_currency_code") val isoCurrencyCode: String? = "USD",
)

@JsonClass(generateAdapter = true)
data class PlaidTransactionsSyncRequest(
  @Json(name = "client_id") val clientId: String,
  val secret: String,
  @Json(name = "access_token") val accessToken: String,
  val cursor: String? = null,
  val count: Int = 100,
)

@JsonClass(generateAdapter = true)
data class PlaidTransactionsSyncResponse(
  val added: List<PlaidTransactionDto> = emptyList(),
  val modified: List<PlaidTransactionDto> = emptyList(),
  val removed: List<PlaidRemovedTransactionDto> = emptyList(),
  @Json(name = "next_cursor") val nextCursor: String = "",
  @Json(name = "has_more") val hasMore: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PlaidTransactionDto(
  @Json(name = "transaction_id") val transactionId: String,
  @Json(name = "account_id") val accountId: String,
  val amount: Double,
  val date: String,
  val name: String,
  @Json(name = "merchant_name") val merchantName: String? = null,
  val pending: Boolean = false,
  @Json(name = "personal_finance_category") val personalFinanceCategory: PlaidPfcDto? = null,
)

@JsonClass(generateAdapter = true)
data class PlaidPfcDto(
  val primary: String? = null,
  val detailed: String? = null,
)

@JsonClass(generateAdapter = true)
data class PlaidRemovedTransactionDto(
  @Json(name = "transaction_id") val transactionId: String,
)
