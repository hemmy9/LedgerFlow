package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.SpendingCategory
import com.example.data.model.SpendingTransaction
import com.example.data.model.SyncSource
import com.example.data.model.TransactionDirection
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FinanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createAccount_authenticatedOwner_persistsAndEmits() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = FinanceRepository(firestore)
    val accId = "acc_${UUID.randomUUID().toString().take(8)}"

    val result =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createAccount(
          BankAccount(
            id = accId,
            name = "Primary Checking",
            institutionName = "First Republic",
            accountType = AccountType.CHECKING.id,
            mask = "4821",
            balanceCents = 250000L,
            currency = "USD",
            syncSource = SyncSource.MANUAL.id,
          )
        )
      }

    assertTrue("Account creation should succeed", result.isSuccess)
    val accounts =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAccounts(uid).first { list -> list.any { it.id == accId } }
      }
    assertTrue(accounts.any { it.id == accId && it.balanceCents == 250000L })
  }

  @Test
  fun addAndRecategorizeTransaction_authenticatedOwner_updatesSafely() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = FinanceRepository(firestore)
    val extId = "tx_${UUID.randomUUID().toString().take(12)}"

    val addRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addTransaction(
          SpendingTransaction(
            accountId = "acc_test",
            merchantName = "Blue Bottle",
            rawDescription = "SQ *BLUE BOTTLE COFFEE",
            amountCents = 675L,
            direction = TransactionDirection.EXPENSE.id,
            categoryId = SpendingCategory.SHOPPING.id,
            dateMillis = System.currentTimeMillis(),
            source = SyncSource.CSV_IMPORT.id,
            externalId = extId,
            isAutoCategorized = true,
            isReviewed = false,
          )
        )
      }
    assertTrue(addRes.isSuccess)
    val txId = addRes.getOrThrow()

    val updateRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateTransactionCategory(
          transactionId = txId,
          newCategoryId = SpendingCategory.DINING.id,
          newDirection = TransactionDirection.EXPENSE.id,
          note = "Morning espresso",
        )
      }
    assertTrue(updateRes.isSuccess)

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeTransactions(uid).first { list ->
          list.any { it.id == txId && it.categoryId == SpendingCategory.DINING.id && it.isReviewed }
        }
      }
    assertTrue(observed.any { it.id == txId && it.note == "Morning espresso" })
  }

  @Test
  fun upsertBudgetAndCreateRule_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = FinanceRepository(firestore)

    val budgetRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.upsertBudget(SpendingCategory.GROCERIES.id, 65000L)
      }
    assertTrue(budgetRes.isSuccess)

    val ruleRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createCategoryRule("TRADER JOE", SpendingCategory.GROCERIES.id)
      }
    assertTrue(ruleRes.isSuccess)

    val budgets =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeBudgets(uid).first { list -> list.any { it.categoryId == SpendingCategory.GROCERIES.id } }
      }
    assertEquals(65000L, budgets.first { it.categoryId == SpendingCategory.GROCERIES.id }.monthlyLimitCents)
  }

  @Test
  fun crossUserRead_deniedBySecurityRules() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = FinanceRepository(firestore)
    val extId = "tx_${UUID.randomUUID().toString().take(12)}"
    val txId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.addTransaction(
          SpendingTransaction(
            accountId = "acc_alice",
            merchantName = "Whole Foods",
            rawDescription = "WHOLE FOODS MKT",
            amountCents = 8420L,
            direction = TransactionDirection.EXPENSE.id,
            categoryId = SpendingCategory.GROCERIES.id,
            dateMillis = System.currentTimeMillis(),
            source = SyncSource.MANUAL.id,
            externalId = extId,
          )
        ).getOrThrow()
      }

    signInTestUser(BOB_EMAIL)
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("users").document(aliceUid).collection("transactions").document(txId).get().await()
      }
      fail("Bob should not be able to read Alice's transaction")
    } catch (e: Exception) {
      val ffe = e.unwrapFirestoreException()
        ?: throw AssertionError("Expected FirebaseFirestoreException, got $e")
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.code)
    }
  }

  @Test
  fun observeTransactions_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = FinanceRepository(firestore)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeTransactions("some_user_id").first()
      }
      fail("Unauthenticated user should be denied")
    } catch (e: Exception) {
      val ffe = e.unwrapFirestoreException()
        ?: throw AssertionError("Expected FirebaseFirestoreException, got $e")
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_ledger@test.com"
    const val BOB_EMAIL = "bob_ledger@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
