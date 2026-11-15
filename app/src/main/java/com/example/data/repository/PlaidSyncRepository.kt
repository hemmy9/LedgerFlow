package com.example.data.repository

import com.example.BuildConfig
import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.CategoryRule
import com.example.data.model.PlaidConnection
import com.example.data.model.SpendingTransaction
import com.example.data.model.SyncSource
import com.example.data.model.TransactionDirection
import com.example.data.remote.PlaidAccountsGetRequest
import com.example.data.remote.PlaidApiService
import com.example.data.remote.PlaidExchangeTokenRequest
import com.example.data.remote.PlaidInstitutionDto
import com.example.data.remote.PlaidInstitutionsGetRequest
import com.example.data.remote.PlaidInstitutionsSearchRequest
import com.example.data.remote.PlaidSandboxPublicTokenRequest
import com.example.data.remote.PlaidTransactionsSyncRequest
import com.example.domain.CategorizationEngine
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

data class PlaidSyncOutcome(
  val accountsSynced: Int,
  val transactionsSynced: Int,
  val institutionName: String,
)

class PlaidSyncRepository(
  private val plaidApi: PlaidApiService = PlaidApiService.create(),
  private val financeRepository: FinanceRepository,
) {

  private val dateFormatter =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }

  fun isConfigured(): Boolean = PlaidApiService.isConfigured()

  suspend fun fetchInstitutions(query: String): Result<List<PlaidInstitutionDto>> {
    if (!isConfigured()) {
      return Result.failure(IllegalStateException("Plaid credentials are not configured in AI Studio Secrets."))
    }
    return try {
      val response =
        if (query.isBlank()) {
          plaidApi.getInstitutions(
            PlaidInstitutionsGetRequest(
              clientId = BuildConfig.PLAID_CLIENT_ID,
              secret = BuildConfig.PLAID_SECRET,
              count = 15,
            )
          )
        } else {
          plaidApi.searchInstitutions(
            PlaidInstitutionsSearchRequest(
              clientId = BuildConfig.PLAID_CLIENT_ID,
              secret = BuildConfig.PLAID_SECRET,
              query = query.trim(),
            )
          )
        }
      Result.success(response.institutions)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun connectAndSyncInstitution(
    institution: PlaidInstitutionDto,
    customRules: List<CategoryRule>,
  ): Result<PlaidSyncOutcome> {
    if (!isConfigured()) {
      return Result.failure(IllegalStateException("Plaid credentials are not configured in AI Studio Secrets."))
    }
    return try {
      val sandboxToken =
        plaidApi.createSandboxPublicToken(
          PlaidSandboxPublicTokenRequest(
            clientId = BuildConfig.PLAID_CLIENT_ID,
            secret = BuildConfig.PLAID_SECRET,
            institutionId = institution.institutionId,
          )
        )
      val exchange =
        plaidApi.exchangePublicToken(
          PlaidExchangeTokenRequest(
            clientId = BuildConfig.PLAID_CLIENT_ID,
            secret = BuildConfig.PLAID_SECRET,
            publicToken = sandboxToken.publicToken,
          )
        )

      val itemId = exchange.itemId
      val connection =
        PlaidConnection(
          id = itemId,
          institutionId = institution.institutionId,
          institutionName = institution.name,
          accessToken = exchange.accessToken,
          cursor = "",
        )
      financeRepository.savePlaidConnection(connection).getOrThrow()
      syncExistingConnection(connection, customRules)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun syncExistingConnection(
    connection: PlaidConnection,
    customRules: List<CategoryRule>,
  ): Result<PlaidSyncOutcome> {
    if (!isConfigured()) {
      return Result.failure(IllegalStateException("Plaid credentials are not configured in AI Studio Secrets."))
    }
    return try {
      val nowMillis = System.currentTimeMillis()
      val accountsResponse =
        plaidApi.getAccounts(
          PlaidAccountsGetRequest(
            clientId = BuildConfig.PLAID_CLIENT_ID,
            secret = BuildConfig.PLAID_SECRET,
            accessToken = connection.accessToken,
          )
        )

      val accountIdMap = mutableMapOf<String, String>()
      for (acct in accountsResponse.accounts) {
        val mappedType =
          when {
            acct.type.equals("credit", ignoreCase = true) -> AccountType.CREDIT
            acct.type.equals("investment", ignoreCase = true) -> AccountType.INVESTMENT
            acct.subtype.equals("savings", ignoreCase = true) -> AccountType.SAVINGS
            else -> AccountType.CHECKING
          }
        val balanceDouble = acct.balances.current ?: acct.balances.available ?: 0.0
        val balanceCents = (balanceDouble * 100.0).roundToLong()
        val localDocId = "plaid_${acct.accountId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}".take(120)

        financeRepository.createAccount(
          BankAccount(
            id = localDocId,
            name = acct.name,
            institutionName = connection.institutionName,
            accountType = mappedType.id,
            mask = acct.mask.orEmpty().take(8),
            balanceCents = balanceCents,
            currency = acct.balances.isoCurrencyCode ?: "USD",
            syncSource = SyncSource.PLAID.id,
            plaidItemId = connection.id,
            plaidAccountId = acct.accountId,
            plaidCursor = connection.cursor,
            lastSyncedAtMillis = nowMillis,
          )
        ).getOrThrow()
        accountIdMap[acct.accountId] = localDocId
      }

      var cursor: String? = connection.cursor.ifBlank { null }
      var hasMore = true
      var syncedTxCount = 0
      val allTransactions = mutableListOf<SpendingTransaction>()

      while (hasMore) {
        val syncResp =
          plaidApi.syncTransactions(
            PlaidTransactionsSyncRequest(
              clientId = BuildConfig.PLAID_CLIENT_ID,
              secret = BuildConfig.PLAID_SECRET,
              accessToken = connection.accessToken,
              cursor = cursor,
            )
          )
        for (dto in syncResp.added + syncResp.modified) {
          val localAccountId = accountIdMap[dto.accountId] ?: continue
          val rawCents = (dto.amount * 100.0).roundToLong()
          if (rawCents == 0L) continue
          // Plaid convention: positive = outflow/expense, negative = inflow/refund/income
          val isInflow = rawCents < 0L
          val catResult =
            CategorizationEngine.categorize(
              rawDescription = dto.name,
              merchantName = dto.merchantName.orEmpty(),
              isInflow = isInflow,
              plaidPrimaryCategory = dto.personalFinanceCategory?.primary,
              plaidDetailedCategory = dto.personalFinanceCategory?.detailed,
              customRules = customRules,
            )
          val dateMillis =
            try {
              synchronized(dateFormatter) { dateFormatter.parse(dto.date)?.time } ?: nowMillis
            } catch (_: Exception) {
              nowMillis
            }
          allTransactions +=
            SpendingTransaction(
              accountId = localAccountId,
              merchantName = catResult.normalizedMerchant,
              rawDescription = dto.name.take(240),
              amountCents = abs(rawCents),
              direction = catResult.direction,
              categoryId = catResult.categoryId,
              dateMillis = dateMillis,
              source = SyncSource.PLAID.id,
              externalId = "plaid_${dto.transactionId}".take(120),
              isAutoCategorized = true,
              isReviewed = false,
              note = if (dto.pending) "Pending" else "",
            )
        }
        cursor = syncResp.nextCursor
        hasMore = syncResp.hasMore
      }

      if (allTransactions.isNotEmpty()) {
        syncedTxCount = financeRepository.upsertSyncedTransactions(allTransactions).getOrThrow()
      }
      if (!cursor.isNullOrBlank()) {
        financeRepository.updatePlaidCursor(connection.id, cursor)
      }

      Result.success(
        PlaidSyncOutcome(
          accountsSynced = accountsResponse.accounts.size,
          transactionsSynced = syncedTxCount,
          institutionName = connection.institutionName,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
