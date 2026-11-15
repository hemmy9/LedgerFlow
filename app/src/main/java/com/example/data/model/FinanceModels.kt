package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SpendingCategory(
  val id: String,
  val displayName: String,
  val isExpenseCategory: Boolean = true,
) {
  GROCERIES("groceries", "Groceries"),
  DINING("dining", "Dining & Coffee"),
  TRANSPORT("transport", "Transport & Gas"),
  HOUSING("housing", "Housing & Utilities"),
  SHOPPING("shopping", "Shopping"),
  SUBSCRIPTIONS("subscriptions", "Subscriptions"),
  ENTERTAINMENT("entertainment", "Entertainment"),
  HEALTH("health", "Health & Wellness"),
  TRAVEL("travel", "Travel"),
  INCOME("income", "Income", isExpenseCategory = false),
  TRANSFER("transfer", "Transfers", isExpenseCategory = false),
  OTHER("other", "Other");

  companion object {
    fun fromId(id: String): SpendingCategory =
      entries.firstOrNull { it.id == id } ?: OTHER

    val expenseCategories: List<SpendingCategory>
      get() = entries.filter { it.isExpenseCategory }
  }
}

enum class AccountType(val id: String, val displayName: String, val isLiability: Boolean = false) {
  CHECKING("checking", "Checking"),
  SAVINGS("savings", "Savings"),
  CREDIT("credit", "Credit Card", isLiability = true),
  INVESTMENT("investment", "Investment"),
  CASH("cash", "Cash");

  companion object {
    fun fromId(id: String): AccountType =
      entries.firstOrNull { it.id == id } ?: CHECKING
  }
}

enum class TransactionDirection(val id: String, val displayName: String) {
  EXPENSE("expense", "Expense"),
  INCOME("income", "Income"),
  TRANSFER("transfer", "Transfer");

  companion object {
    fun fromId(id: String): TransactionDirection =
      entries.firstOrNull { it.id == id } ?: EXPENSE
  }
}

enum class SyncSource(val id: String, val displayName: String) {
  MANUAL("manual", "Manual"),
  CSV_IMPORT("csv_import", "CSV Statement"),
  PLAID("plaid", "Plaid Bank Sync");

  companion object {
    fun fromId(id: String): SyncSource =
      entries.firstOrNull { it.id == id } ?: MANUAL
  }
}

@IgnoreExtraProperties
data class BankAccount(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val institutionName: String = "",
  val accountType: String = AccountType.CHECKING.id,
  val mask: String = "",
  val balanceCents: Long = 0L,
  val currency: String = "USD",
  val syncSource: String = SyncSource.MANUAL.id,
  val plaidItemId: String = "",
  val plaidAccountId: String = "",
  val plaidCursor: String = "",
  val lastSyncedAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PlaidConnection(
  @DocumentId val id: String = "",
  val userId: String = "",
  val institutionId: String = "",
  val institutionName: String = "",
  val accessToken: String = "",
  val cursor: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SpendingTransaction(
  @DocumentId val id: String = "",
  val userId: String = "",
  val accountId: String = "",
  val merchantName: String = "",
  val rawDescription: String = "",
  val amountCents: Long = 0L,
  val direction: String = TransactionDirection.EXPENSE.id,
  val categoryId: String = SpendingCategory.OTHER.id,
  val dateMillis: Long = 0L,
  val source: String = SyncSource.MANUAL.id,
  val externalId: String = "",
  val isAutoCategorized: Boolean = false,
  val isReviewed: Boolean = true,
  val note: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CategoryBudget(
  @DocumentId val id: String = "",
  val userId: String = "",
  val categoryId: String = SpendingCategory.OTHER.id,
  val monthlyLimitCents: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CategoryRule(
  @DocumentId val id: String = "",
  val userId: String = "",
  val keyword: String = "",
  val categoryId: String = SpendingCategory.OTHER.id,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}
