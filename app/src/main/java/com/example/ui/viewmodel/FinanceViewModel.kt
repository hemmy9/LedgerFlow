package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.CategoryBudget
import com.example.data.model.CategoryRule
import com.example.data.model.PlaidConnection
import com.example.data.model.SpendingCategory
import com.example.data.model.SpendingTransaction
import com.example.data.model.SyncSource
import com.example.data.model.TransactionDirection
import com.example.data.model.UiState
import com.example.data.remote.PlaidInstitutionDto
import com.example.data.repository.FinanceRepository
import com.example.data.repository.PlaidSyncRepository
import com.example.domain.BankCsvParser
import com.example.domain.CategorizationEngine
import com.example.ui.util.FinanceFormatters
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

data class CategorySpendSummary(
  val category: SpendingCategory,
  val totalCents: Long,
  val transactionCount: Int,
  val shareFraction: Float,
)

data class BudgetProgress(
  val category: SpendingCategory,
  val budget: CategoryBudget?,
  val spentCents: Long,
  val progressFraction: Float,
  val isOverBudget: Boolean,
)

data class FinanceDashboardData(
  val accounts: List<BankAccount> = emptyList(),
  val transactions: List<SpendingTransaction> = emptyList(),
  val budgets: List<CategoryBudget> = emptyList(),
  val categoryRules: List<CategoryRule> = emptyList(),
  val plaidConnections: List<PlaidConnection> = emptyList(),
  val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
  val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
  val netWorthCents: Long = 0L,
  val assetsCents: Long = 0L,
  val liabilitiesCents: Long = 0L,
  val monthlySpentCents: Long = 0L,
  val monthlyIncomeCents: Long = 0L,
  val categoryBreakdown: List<CategorySpendSummary> = emptyList(),
  val budgetProgress: List<BudgetProgress> = emptyList(),
  val unreviewedTransactions: List<SpendingTransaction> = emptyList(),
)

data class CsvImportPreview(
  val rawFileName: String,
  val parsedTransactions: List<SpendingTransaction>,
  val skippedRows: Int,
)

data class FinanceActionState(
  val isSyncing: Boolean = false,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val plaidInstitutions: List<PlaidInstitutionDto> = emptyList(),
  val isSearchingInstitutions: Boolean = false,
  val csvPreview: CsvImportPreview? = null,
)

class FinanceViewModel(
  private val repository: FinanceRepository,
  private val plaidSyncRepository: PlaidSyncRepository,
  private val currentUserId: String,
) : ViewModel() {

  private val nowCal = Calendar.getInstance()
  private val _selectedYearMonth =
    MutableStateFlow(nowCal.get(Calendar.YEAR) to nowCal.get(Calendar.MONTH))
  val selectedYearMonth: StateFlow<Pair<Int, Int>> = _selectedYearMonth.asStateFlow()

  private val _actionState = MutableStateFlow(FinanceActionState())
  val actionState: StateFlow<FinanceActionState> = _actionState.asStateFlow()

  val isPlaidConfigured: Boolean
    get() = plaidSyncRepository.isConfigured()

  private val accountsFlow =
    repository
      .observeAccounts(currentUserId)
      .catch { error ->
        unwrapAndLog("accounts", error)
        throw error
      }

  private val transactionsFlow =
    repository
      .observeTransactions(currentUserId)
      .catch { error ->
        unwrapAndLog("transactions", error)
        throw error
      }

  private val budgetsFlow =
    repository
      .observeBudgets(currentUserId)
      .catch { error ->
        unwrapAndLog("budgets", error)
        throw error
      }

  private val rulesFlow =
    repository
      .observeCategoryRules(currentUserId)
      .catch { error ->
        unwrapAndLog("categoryRules", error)
        throw error
      }

  private val plaidFlow =
    repository
      .observePlaidConnections(currentUserId)
      .catch { error ->
        unwrapAndLog("plaidItems", error)
        throw error
      }

  val dashboardState: StateFlow<UiState<FinanceDashboardData>> =
    combine(
      accountsFlow,
      transactionsFlow,
      budgetsFlow,
      rulesFlow,
      combine(plaidFlow, _selectedYearMonth) { plaid, ym -> plaid to ym },
    ) { accounts, transactions, budgets, rules, (plaidConnections, ym) ->
      buildDashboardData(
        accounts = accounts,
        transactions = transactions,
        budgets = budgets,
        rules = rules,
        plaidConnections = plaidConnections,
        year = ym.first,
        month = ym.second,
      )
    }
      .map<FinanceDashboardData, UiState<FinanceDashboardData>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing finance data", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load financial data"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private fun unwrapAndLog(streamName: String, error: Throwable) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    Log.w(TAG, "Stream failure on $streamName", firestoreError ?: error)
  }

  private fun buildDashboardData(
    accounts: List<BankAccount>,
    transactions: List<SpendingTransaction>,
    budgets: List<CategoryBudget>,
    rules: List<CategoryRule>,
    plaidConnections: List<PlaidConnection>,
    year: Int,
    month: Int,
  ): FinanceDashboardData {
    var assets = 0L
    var liabilities = 0L
    for (acc in accounts) {
      val type = AccountType.fromId(acc.accountType)
      if (type.isLiability) {
        liabilities += kotlin.math.abs(acc.balanceCents)
      } else {
        assets += acc.balanceCents
      }
    }

    val monthTx =
      transactions.filter { tx ->
        FinanceFormatters.isInMonth(tx.dateMillis, year, month)
      }

    val monthlySpent =
      monthTx
        .filter { it.direction == TransactionDirection.EXPENSE.id }
        .sumOf { it.amountCents }

    val monthlyIncome =
      monthTx
        .filter { it.direction == TransactionDirection.INCOME.id }
        .sumOf { it.amountCents }

    val expenseByCategory =
      monthTx
        .filter { it.direction == TransactionDirection.EXPENSE.id }
        .groupBy { SpendingCategory.fromId(it.categoryId) }

    val breakdown =
      expenseByCategory
        .map { (cat, list) ->
          val sum = list.sumOf { it.amountCents }
          CategorySpendSummary(
            category = cat,
            totalCents = sum,
            transactionCount = list.size,
            shareFraction = if (monthlySpent > 0L) (sum.toFloat() / monthlySpent.toFloat()) else 0f,
          )
        }
        .sortedByDescending { it.totalCents }

    val budgetMap = budgets.associateBy { SpendingCategory.fromId(it.categoryId) }
    val allRelevantCats =
      (budgetMap.keys + expenseByCategory.keys).distinct().sortedBy { it.displayName }

    val budgetProgress =
      allRelevantCats
        .filter { it.isExpenseCategory }
        .map { cat ->
          val budget = budgetMap[cat]
          val spent = expenseByCategory[cat]?.sumOf { it.amountCents } ?: 0L
          val limit = budget?.monthlyLimitCents ?: 0L
          val fraction = if (limit > 0L) (spent.toFloat() / limit.toFloat()) else 0f
          BudgetProgress(
            category = cat,
            budget = budget,
            spentCents = spent,
            progressFraction = fraction,
            isOverBudget = limit > 0L && spent > limit,
          )
        }
        .sortedWith(
          compareByDescending<BudgetProgress> { it.budget != null }
            .thenByDescending { it.progressFraction }
        )

    val unreviewed =
      transactions.filter { !it.isReviewed }.sortedByDescending { it.dateMillis }

    return FinanceDashboardData(
      accounts = accounts.sortedByDescending { it.balanceCents },
      transactions = transactions.sortedByDescending { it.dateMillis },
      budgets = budgets,
      categoryRules = rules.sortedBy { it.keyword },
      plaidConnections = plaidConnections,
      selectedYear = year,
      selectedMonth = month,
      netWorthCents = assets - liabilities,
      assetsCents = assets,
      liabilitiesCents = liabilities,
      monthlySpentCents = monthlySpent,
      monthlyIncomeCents = monthlyIncome,
      categoryBreakdown = breakdown,
      budgetProgress = budgetProgress,
      unreviewedTransactions = unreviewed,
    )
  }

  fun shiftMonth(deltaMonths: Int) {
    val (y, m) = _selectedYearMonth.value
    val cal = Calendar.getInstance().apply {
      set(Calendar.YEAR, y)
      set(Calendar.MONTH, m)
      set(Calendar.DAY_OF_MONTH, 1)
      add(Calendar.MONTH, deltaMonths)
    }
    _selectedYearMonth.value = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
  }

  fun clearMessages() {
    _actionState.update { it.copy(statusMessage = null, errorMessage = null) }
  }

  // --- Account Operations ---

  fun createManualAccount(
    name: String,
    institutionName: String,
    accountType: AccountType,
    mask: String,
    balanceCents: Long,
  ) {
    viewModelScope.launch {
      _actionState.update { it.copy(isSyncing = true, errorMessage = null) }
      val result =
        repository.createAccount(
          BankAccount(
            name = name.ifBlank { accountType.displayName },
            institutionName = institutionName.ifBlank { "Personal" },
            accountType = accountType.id,
            mask = mask.take(4),
            balanceCents = balanceCents,
            currency = "USD",
            syncSource = SyncSource.MANUAL.id,
            lastSyncedAtMillis = System.currentTimeMillis(),
          )
        )
      result.fold(
        onSuccess = {
          _actionState.update {
            it.copy(isSyncing = false, statusMessage = "Added account $name")
          }
        },
        onFailure = { e ->
          _actionState.update {
            it.copy(isSyncing = false, errorMessage = e.message ?: "Failed to add account")
          }
        },
      )
    }
  }

  fun updateAccountBalance(accountId: String, newBalanceCents: Long) {
    viewModelScope.launch {
      repository.updateAccountBalance(accountId, newBalanceCents).fold(
        onSuccess = {
          _actionState.update { it.copy(statusMessage = "Updated account balance") }
        },
        onFailure = { e ->
          _actionState.update { it.copy(errorMessage = e.message ?: "Balance update failed") }
        },
      )
    }
  }

  fun deleteAccount(accountId: String) {
    viewModelScope.launch {
      repository.deleteAccount(accountId).fold(
        onSuccess = { _actionState.update { it.copy(statusMessage = "Account removed") } },
        onFailure = { e -> _actionState.update { it.copy(errorMessage = e.message) } },
      )
    }
  }

  // --- Transaction Operations ---

  fun addManualTransaction(
    accountId: String,
    merchantInput: String,
    amountCents: Long,
    direction: TransactionDirection,
    explicitCategory: SpendingCategory?,
    dateMillis: Long,
    note: String,
    customRules: List<CategoryRule>,
  ) {
    viewModelScope.launch {
      val catResult =
        CategorizationEngine.categorize(
          rawDescription = merchantInput,
          merchantName = merchantInput,
          isInflow = direction == TransactionDirection.INCOME,
          customRules = customRules,
        )
      val finalCategory = explicitCategory?.id ?: catResult.categoryId
      val isAuto = explicitCategory == null
      val tx =
        SpendingTransaction(
          accountId = accountId,
          merchantName = catResult.normalizedMerchant,
          rawDescription = merchantInput.trim().take(240),
          amountCents = amountCents.coerceAtLeast(1L),
          direction = direction.id,
          categoryId = finalCategory,
          dateMillis = dateMillis,
          source = SyncSource.MANUAL.id,
          externalId = "man_${UUID.randomUUID().toString().take(16)}",
          isAutoCategorized = isAuto,
          isReviewed = true,
          note = note.trim().take(200),
        )
      repository.addTransaction(tx).fold(
        onSuccess = {
          _actionState.update { it.copy(statusMessage = "Transaction saved") }
        },
        onFailure = { e ->
          _actionState.update { it.copy(errorMessage = e.message ?: "Failed to save transaction") }
        },
      )
    }
  }

  fun recategorizeTransaction(
    tx: SpendingTransaction,
    newCategory: SpendingCategory,
    newDirection: TransactionDirection,
    note: String,
    createRuleForMerchant: Boolean,
    allTransactions: List<SpendingTransaction>,
    existingRules: List<CategoryRule>,
  ) {
    viewModelScope.launch {
      val updateRes =
        repository.updateTransactionCategory(
          transactionId = tx.id,
          newCategoryId = newCategory.id,
          newDirection = newDirection.id,
          note = note,
        )
      if (updateRes.isFailure) {
        _actionState.update {
          it.copy(errorMessage = updateRes.exceptionOrNull()?.message ?: "Update failed")
        }
        return@launch
      }

      if (createRuleForMerchant && tx.merchantName.isNotBlank()) {
        val keyword = tx.merchantName.trim().uppercase()
        repository.createCategoryRule(keyword, newCategory.id)
        val updatedRules =
          existingRules + CategoryRule(keyword = keyword, categoryId = newCategory.id)
        repository.reapplyRulesToUnreviewed(allTransactions, updatedRules)
        _actionState.update {
          it.copy(statusMessage = "Updated & saved rule for \"$keyword\"")
        }
      } else {
        _actionState.update { it.copy(statusMessage = "Transaction updated") }
      }
    }
  }

  fun deleteTransaction(txId: String) {
    viewModelScope.launch {
      repository.deleteTransaction(txId).fold(
        onSuccess = { _actionState.update { it.copy(statusMessage = "Transaction deleted") } },
        onFailure = { e -> _actionState.update { it.copy(errorMessage = e.message) } },
      )
    }
  }

  // --- CSV Bank Statement Sync ---

  fun prepareCsvPreview(
    fileName: String,
    csvContent: String,
    accountId: String,
    customRules: List<CategoryRule>,
  ) {
    val summary = BankCsvParser.parse(csvContent)
    if (summary.transactions.isEmpty()) {
      _actionState.update {
        it.copy(
          errorMessage =
            "Could not parse rows from \"$fileName\". Ensure columns include Date, Description, and Amount (or Debit/Credit)."
        )
      }
      return
    }
    val mapped =
      summary.transactions.map { parsed ->
        val catResult =
          CategorizationEngine.categorize(
            rawDescription = parsed.rawDescription,
            isInflow = parsed.isInflow,
            csvCategoryHint = parsed.categoryHint,
            customRules = customRules,
          )
        SpendingTransaction(
          accountId = accountId,
          merchantName = catResult.normalizedMerchant,
          rawDescription = parsed.rawDescription,
          amountCents = parsed.amountCents,
          direction = catResult.direction,
          categoryId = catResult.categoryId,
          dateMillis = parsed.dateMillis,
          source = SyncSource.CSV_IMPORT.id,
          externalId = parsed.externalId,
          isAutoCategorized = true,
          isReviewed = false,
          note = "",
        )
      }
    _actionState.update {
      it.copy(
        csvPreview =
          CsvImportPreview(
            rawFileName = fileName,
            parsedTransactions = mapped,
            skippedRows = summary.skippedRows,
          )
      )
    }
  }

  fun dismissCsvPreview() {
    _actionState.update { it.copy(csvPreview = null) }
  }

  fun commitCsvImport(
    targetAccountId: String?,
    newAccountName: String,
    newInstitutionName: String,
    newAccountType: AccountType,
    newAccountBalanceCents: Long,
  ) {
    val preview = _actionState.value.csvPreview ?: return
    viewModelScope.launch {
      _actionState.update { it.copy(isSyncing = true, errorMessage = null) }
      val accountId =
        if (!targetAccountId.isNullOrBlank()) {
          targetAccountId
        } else {
          val createRes =
            repository.createAccount(
              BankAccount(
                name = newAccountName.ifBlank { "Imported Bank Account" },
                institutionName = newInstitutionName.ifBlank { "Bank CSV" },
                accountType = newAccountType.id,
                mask = "",
                balanceCents = newAccountBalanceCents,
                currency = "USD",
                syncSource = SyncSource.CSV_IMPORT.id,
                lastSyncedAtMillis = System.currentTimeMillis(),
              )
            )
          if (createRes.isFailure) {
            _actionState.update {
              it.copy(
                isSyncing = false,
                errorMessage = createRes.exceptionOrNull()?.message ?: "Failed to create account",
              )
            }
            return@launch
          }
          createRes.getOrThrow()
        }

      val boundTransactions = preview.parsedTransactions.map { it.copy(accountId = accountId) }
      repository.upsertSyncedTransactions(boundTransactions).fold(
        onSuccess = { count ->
          _actionState.update {
            it.copy(
              isSyncing = false,
              csvPreview = null,
              statusMessage = "Synced $count transactions from ${preview.rawFileName}",
            )
          }
        },
        onFailure = { e ->
          _actionState.update {
            it.copy(
              isSyncing = false,
              errorMessage = e.message ?: "Failed to import transactions",
            )
          }
        },
      )
    }
  }

  // --- Plaid Bank Account Sync ---

  fun searchPlaidInstitutions(query: String) {
    if (!isPlaidConfigured) return
    viewModelScope.launch {
      _actionState.update { it.copy(isSearchingInstitutions = true, errorMessage = null) }
      plaidSyncRepository.fetchInstitutions(query).fold(
        onSuccess = { list ->
          _actionState.update {
            it.copy(isSearchingInstitutions = false, plaidInstitutions = list)
          }
        },
        onFailure = { e ->
          _actionState.update {
            it.copy(
              isSearchingInstitutions = false,
              errorMessage = "Plaid lookup failed: ${e.localizedMessage}",
            )
          }
        },
      )
    }
  }

  fun connectPlaidInstitution(
    institution: PlaidInstitutionDto,
    customRules: List<CategoryRule>,
  ) {
    viewModelScope.launch {
      _actionState.update { it.copy(isSyncing = true, errorMessage = null) }
      plaidSyncRepository.connectAndSyncInstitution(institution, customRules).fold(
        onSuccess = { outcome ->
          _actionState.update {
            it.copy(
              isSyncing = false,
              statusMessage =
                "Connected ${outcome.institutionName}: ${outcome.accountsSynced} accounts, ${outcome.transactionsSynced} transactions synced",
            )
          }
        },
        onFailure = { e ->
          _actionState.update {
            it.copy(
              isSyncing = false,
              errorMessage = "Plaid connection error: ${e.localizedMessage}",
            )
          }
        },
      )
    }
  }

  fun syncAllPlaidConnections(
    connections: List<PlaidConnection>,
    customRules: List<CategoryRule>,
  ) {
    if (connections.isEmpty()) return
    viewModelScope.launch {
      _actionState.update { it.copy(isSyncing = true, errorMessage = null) }
      var totalTx = 0
      var totalAccounts = 0
      for (conn in connections) {
        val res = plaidSyncRepository.syncExistingConnection(conn, customRules)
        if (res.isSuccess) {
          val out = res.getOrThrow()
          totalTx += out.transactionsSynced
          totalAccounts += out.accountsSynced
        } else {
          _actionState.update {
            it.copy(
              isSyncing = false,
              errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Plaid sync failed",
            )
          }
          return@launch
        }
      }
      _actionState.update {
        it.copy(
          isSyncing = false,
          statusMessage = "Synced $totalAccounts accounts and $totalTx new/updated transactions",
        )
      }
    }
  }

  // --- Budgets & Rules ---

  fun upsertBudget(category: SpendingCategory, limitCents: Long) {
    viewModelScope.launch {
      repository.upsertBudget(category.id, limitCents).fold(
        onSuccess = {
          _actionState.update { it.copy(statusMessage = "${category.displayName} budget updated") }
        },
        onFailure = { e ->
          _actionState.update { it.copy(errorMessage = e.message ?: "Failed to save budget") }
        },
      )
    }
  }

  fun deleteBudget(budgetId: String) {
    viewModelScope.launch {
      repository.deleteBudget(budgetId).fold(
        onSuccess = { _actionState.update { it.copy(statusMessage = "Budget removed") } },
        onFailure = { e -> _actionState.update { it.copy(errorMessage = e.message) } },
      )
    }
  }

  fun createCategoryRule(
    keyword: String,
    category: SpendingCategory,
    allTransactions: List<SpendingTransaction>,
    existingRules: List<CategoryRule>,
  ) {
    viewModelScope.launch {
      repository.createCategoryRule(keyword, category.id).fold(
        onSuccess = {
          val updatedRules =
            existingRules + CategoryRule(keyword = keyword.trim().uppercase(), categoryId = category.id)
          val recatCount =
            repository.reapplyRulesToUnreviewed(allTransactions, updatedRules).getOrDefault(0)
          _actionState.update {
            it.copy(
              statusMessage =
                if (recatCount > 0) "Rule added & updated $recatCount unreviewed transactions"
                else "Rule added for \"${keyword.trim().uppercase()}\""
            )
          }
        },
        onFailure = { e ->
          _actionState.update { it.copy(errorMessage = e.message ?: "Failed to create rule") }
        },
      )
    }
  }

  fun deleteCategoryRule(ruleId: String) {
    viewModelScope.launch {
      repository.deleteCategoryRule(ruleId).fold(
        onSuccess = { _actionState.update { it.copy(statusMessage = "Rule deleted") } },
        onFailure = { e -> _actionState.update { it.copy(errorMessage = e.message) } },
      )
    }
  }

  fun reapplyAllRules(transactions: List<SpendingTransaction>, rules: List<CategoryRule>) {
    viewModelScope.launch {
      repository.reapplyRulesToUnreviewed(transactions, rules).fold(
        onSuccess = { count ->
          _actionState.update {
            it.copy(
              statusMessage =
                if (count > 0) "Recategorized $count unreviewed transactions"
                else "All unreviewed transactions already match current rules"
            )
          }
        },
        onFailure = { e -> _actionState.update { it.copy(errorMessage = e.message) } },
      )
    }
  }

  private companion object {
    const val TAG = "FinanceVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
