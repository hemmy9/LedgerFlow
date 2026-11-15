package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.BankAccount
import com.example.data.model.CategoryBudget
import com.example.data.model.CategoryRule
import com.example.data.model.PlaidConnection
import com.example.data.model.SpendingTransaction
import com.example.data.model.TransactionDirection
import com.example.domain.CategorizationEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    Firebase.auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  private fun sanitizeDocId(raw: String): String =
    raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifEmpty { "doc_${System.currentTimeMillis()}" }

  private fun userDoc(uid: String) = db.collection("users").document(uid)

  // --- Observation Streams ---

  fun observeAccounts(userId: String): Flow<List<BankAccount>> = flow {
    val path = "users/$userId/accounts"
    emitAll(
      userDoc(userId).collection("accounts")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            BankAccount::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  fun observeTransactions(userId: String): Flow<List<SpendingTransaction>> = flow {
    val path = "users/$userId/transactions"
    emitAll(
      userDoc(userId).collection("transactions")
        .orderBy("dateMillis", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            SpendingTransaction::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  fun observeBudgets(userId: String): Flow<List<CategoryBudget>> = flow {
    val path = "users/$userId/budgets"
    emitAll(
      userDoc(userId).collection("budgets")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            CategoryBudget::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  fun observeCategoryRules(userId: String): Flow<List<CategoryRule>> = flow {
    val path = "users/$userId/categoryRules"
    emitAll(
      userDoc(userId).collection("categoryRules")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            CategoryRule::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  fun observePlaidConnections(userId: String): Flow<List<PlaidConnection>> = flow {
    val path = "users/$userId/plaidItems"
    emitAll(
      userDoc(userId).collection("plaidItems")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            PlaidConnection::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logStreamErrorIfActive(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  private fun logStreamErrorIfActive(
    error: Throwable,
    op: OperationType,
    path: String,
    expectedUid: String,
  ) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val exceptionToLog = firestoreError ?: (error as? Exception) ?: return
    if (Firebase.auth.currentUser?.uid == expectedUid) {
      handleFirestoreError(exceptionToLog, op, path)
    }
  }

  // --- Accounts ---

  suspend fun createAccount(account: BankAccount): Result<String> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val col = userDoc(uid).collection("accounts")
    val docRef =
      if (account.id.isNotBlank()) col.document(sanitizeDocId(account.id)) else col.document()
    val payload =
      mapOf(
        "userId" to uid,
        "name" to account.name.trim().take(80),
        "institutionName" to account.institutionName.trim().take(80),
        "accountType" to account.accountType,
        "mask" to account.mask.trim().take(8),
        "balanceCents" to account.balanceCents,
        "currency" to account.currency.ifBlank { "USD" }.take(3),
        "syncSource" to account.syncSource,
        "plaidItemId" to account.plaidItemId.take(128),
        "plaidAccountId" to account.plaidAccountId.take(128),
        "plaidCursor" to account.plaidCursor.take(256),
        "lastSyncedAtMillis" to account.lastSyncedAtMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateAccountBalance(
    accountId: String,
    newBalanceCents: Long,
    lastSyncedAtMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docRef = userDoc(uid).collection("accounts").document(accountId)
    val updates =
      mapOf(
        "balanceCents" to newBalanceCents,
        "lastSyncedAtMillis" to lastSyncedAtMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteAccount(accountId: String): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docRef = userDoc(uid).collection("accounts").document(accountId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- Transactions ---

  suspend fun addTransaction(tx: SpendingTransaction): Result<String> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val col = userDoc(uid).collection("transactions")
    val docRef =
      if (tx.externalId.isNotBlank()) col.document(sanitizeDocId(tx.externalId)) else col.document()
    val payload = transactionCreatePayload(uid, tx)
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun upsertSyncedTransactions(
    transactions: List<SpendingTransaction>,
  ): Result<Int> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    if (transactions.isEmpty()) return Result.success(0)

    val col = userDoc(uid).collection("transactions")
    return try {
      // Check existing IDs in batches so we preserve user-reviewed categories on re-sync
      val existingDocs =
        col.get().await().documents.associateBy { it.id }

      var committed = 0
      val chunks = transactions.chunked(400)
      for (chunk in chunks) {
        val batch = db.batch()
        for (tx in chunk) {
          val docId =
            if (tx.externalId.isNotBlank()) sanitizeDocId(tx.externalId) else col.document().id
          val docRef = col.document(docId)
          val existing = existingDocs[docId]
          if (existing != null) {
            val alreadyReviewed = existing.getBoolean("isReviewed") == true
            if (!alreadyReviewed) {
              batch.update(
                docRef,
                mapOf(
                  "merchantName" to tx.merchantName.trim().take(100).ifEmpty { "Unknown" },
                  "rawDescription" to tx.rawDescription.trim().take(240),
                  "amountCents" to tx.amountCents.coerceAtLeast(1L),
                  "direction" to tx.direction,
                  "categoryId" to tx.categoryId,
                  "dateMillis" to tx.dateMillis.coerceAtLeast(1L),
                  "isAutoCategorized" to tx.isAutoCategorized,
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
              committed++
            }
          } else {
            batch.set(docRef, transactionCreatePayload(uid, tx))
            committed++
          }
        }
        batch.commit().await()
      }
      Result.success(committed)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$uid/transactions")
      Result.failure(e)
    }
  }

  suspend fun updateTransactionCategory(
    transactionId: String,
    newCategoryId: String,
    newDirection: String,
    note: String,
  ): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docRef = userDoc(uid).collection("transactions").document(transactionId)
    val updates =
      mapOf(
        "categoryId" to newCategoryId,
        "direction" to newDirection,
        "isAutoCategorized" to false,
        "isReviewed" to true,
        "note" to note.trim().take(200),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteTransaction(transactionId: String): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docRef = userDoc(uid).collection("transactions").document(transactionId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- Budgets ---

  suspend fun upsertBudget(categoryId: String, monthlyLimitCents: Long): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docId = sanitizeDocId(categoryId)
    val docRef = userDoc(uid).collection("budgets").document(docId)
    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(
          mapOf(
            "monthlyLimitCents" to monthlyLimitCents.coerceAtLeast(100L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(
          mapOf(
            "userId" to uid,
            "categoryId" to categoryId,
            "monthlyLimitCents" to monthlyLimitCents.coerceAtLeast(100L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteBudget(budgetId: String): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docRef = userDoc(uid).collection("budgets").document(budgetId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- Category Rules ---

  suspend fun createCategoryRule(keyword: String, categoryId: String): Result<String> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val normalizedKeyword = keyword.trim().uppercase().take(80)
    require(normalizedKeyword.isNotEmpty()) { "Rule keyword must not be empty" }
    val docRef = userDoc(uid).collection("categoryRules").document()
    val payload =
      mapOf(
        "userId" to uid,
        "keyword" to normalizedKeyword,
        "categoryId" to categoryId,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteCategoryRule(ruleId: String): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docRef = userDoc(uid).collection("categoryRules").document(ruleId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun reapplyRulesToUnreviewed(
    transactions: List<SpendingTransaction>,
    customRules: List<CategoryRule>,
  ): Result<Int> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val candidates = transactions.filter { !it.isReviewed }
    if (candidates.isEmpty()) return Result.success(0)

    return try {
      var updated = 0
      val col = userDoc(uid).collection("transactions")
      for (chunk in candidates.chunked(400)) {
        val batch = db.batch()
        var chunkChanged = 0
        for (tx in chunk) {
          val result =
            CategorizationEngine.categorize(
              rawDescription = tx.rawDescription.ifBlank { tx.merchantName },
              merchantName = tx.merchantName,
              isInflow = tx.direction == TransactionDirection.INCOME.id,
              customRules = customRules,
            )
          if (result.categoryId != tx.categoryId) {
            batch.update(
              col.document(tx.id),
              mapOf(
                "categoryId" to result.categoryId,
                "direction" to result.direction,
                "isAutoCategorized" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
            chunkChanged++
            updated++
          }
        }
        if (chunkChanged > 0) {
          batch.commit().await()
        }
      }
      Result.success(updated)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "users/$uid/transactions")
      Result.failure(e)
    }
  }

  // --- Plaid Connection Storage ---

  suspend fun savePlaidConnection(connection: PlaidConnection): Result<String> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val col = userDoc(uid).collection("plaidItems")
    val docId = if (connection.id.isNotBlank()) sanitizeDocId(connection.id) else col.document().id
    val docRef = col.document(docId)
    val payload =
      mapOf(
        "userId" to uid,
        "institutionId" to connection.institutionId.take(80),
        "institutionName" to connection.institutionName.take(80),
        "accessToken" to connection.accessToken.take(256),
        "cursor" to connection.cursor.take(256),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updatePlaidCursor(itemId: String, newCursor: String): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docRef = userDoc(uid).collection("plaidItems").document(itemId)
    return try {
      docRef.update(
        mapOf(
          "cursor" to newCursor.take(256),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  private fun transactionCreatePayload(uid: String, tx: SpendingTransaction): Map<String, Any> =
    mapOf(
      "userId" to uid,
      "accountId" to tx.accountId.take(128),
      "merchantName" to tx.merchantName.trim().take(100).ifEmpty { "Unknown Merchant" },
      "rawDescription" to tx.rawDescription.trim().take(240),
      "amountCents" to tx.amountCents.coerceAtLeast(1L),
      "direction" to tx.direction,
      "categoryId" to tx.categoryId,
      "dateMillis" to tx.dateMillis.coerceAtLeast(1L),
      "source" to tx.source,
      "externalId" to tx.externalId.take(128),
      "isAutoCategorized" to tx.isAutoCategorized,
      "isReviewed" to tx.isReviewed,
      "note" to tx.note.trim().take(200),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
}
