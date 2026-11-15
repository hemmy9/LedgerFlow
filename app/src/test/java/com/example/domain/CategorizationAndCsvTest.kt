package com.example.domain

import com.example.data.model.CategoryRule
import com.example.data.model.SpendingCategory
import com.example.data.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizationAndCsvTest {

  @Test
  fun categorize_builtInMerchants_assignsExpectedCategories() {
    val grocery = CategorizationEngine.categorize("POS DEBIT TRADER JOES #104 SAN FRANCISCO CA")
    assertEquals(SpendingCategory.GROCERIES.id, grocery.categoryId)
    assertEquals(TransactionDirection.EXPENSE.id, grocery.direction)

    val coffee = CategorizationEngine.categorize("SQ *BLUE BOTTLE COFFEE OAKLAND")
    assertEquals(SpendingCategory.DINING.id, coffee.categoryId)

    val payroll = CategorizationEngine.categorize("GUSTO PAYROLL DIRECT DEP", isInflow = true)
    assertEquals(SpendingCategory.INCOME.id, payroll.categoryId)
    assertEquals(TransactionDirection.INCOME.id, payroll.direction)
  }

  @Test
  fun categorize_customRule_overridesBuiltInRules() {
    val custom = listOf(CategoryRule(keyword = "TARGET", categoryId = SpendingCategory.GROCERIES.id))
    val result = CategorizationEngine.categorize("TARGET T-1920", customRules = custom)
    assertEquals(SpendingCategory.GROCERIES.id, result.categoryId)
  }

  @Test
  fun parseCsv_handlesStandardBankExportAndQuotes() {
    val csv = """
      Date,Description,Amount
      2026-11-10,"TRADER JOE'S #142, CA",-84.50
      2026-11-11,"ACME CORP PAYROLL",3250.00
      2026-11-12,"NETFLIX.COM",(15.99)
    """.trimIndent()

    val summary = BankCsvParser.parse(csv)
    assertEquals(3, summary.transactions.size)
    assertEquals(8450L, summary.transactions[0].amountCents)
    assertTrue(!summary.transactions[0].isInflow)
    assertEquals(325000L, summary.transactions[1].amountCents)
    assertTrue(summary.transactions[1].isInflow)
    assertEquals(1599L, summary.transactions[2].amountCents)
    assertTrue(!summary.transactions[2].isInflow)
  }
}
