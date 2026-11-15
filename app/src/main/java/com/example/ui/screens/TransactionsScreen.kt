package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.SpendingCategory
import com.example.data.model.SpendingTransaction
import com.example.data.model.TransactionDirection
import com.example.ui.components.EmptyFinanceStateCard
import com.example.ui.components.TransactionRowItem
import com.example.ui.util.FinanceFormatters
import com.example.ui.viewmodel.FinanceDashboardData

enum class ActivityFilter(val label: String) {
  ALL("All"),
  EXPENSES("Expenses"),
  INCOME("Income"),
  NEEDS_REVIEW("Needs Review"),
}

@Composable
fun TransactionsScreen(
  data: FinanceDashboardData,
  initialCategoryFilter: SpendingCategory?,
  onClearInitialCategory: () -> Unit,
  onTransactionClick: (SpendingTransaction) -> Unit,
  onAddTransactionClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }
  var activeCategory by remember(initialCategoryFilter) { mutableStateOf(initialCategoryFilter) }

  val accountMap = remember(data.accounts) { data.accounts.associateBy { it.id } }

  val filtered =
    remember(data.transactions, searchQuery, selectedFilter, activeCategory) {
      data.transactions.filter { tx ->
        val matchesSearch =
          searchQuery.isBlank() ||
            tx.merchantName.contains(searchQuery, ignoreCase = true) ||
            tx.rawDescription.contains(searchQuery, ignoreCase = true) ||
            SpendingCategory.fromId(tx.categoryId).displayName.contains(searchQuery, ignoreCase = true)

        val matchesFilter =
          when (selectedFilter) {
            ActivityFilter.ALL -> true
            ActivityFilter.EXPENSES -> tx.direction == TransactionDirection.EXPENSE.id
            ActivityFilter.INCOME -> tx.direction == TransactionDirection.INCOME.id
            ActivityFilter.NEEDS_REVIEW -> !tx.isReviewed
          }

        val matchesCategory =
          activeCategory == null || tx.categoryId == activeCategory?.id

        matchesSearch && matchesFilter && matchesCategory
      }
    }

  val grouped =
    remember(filtered) {
      filtered.groupBy { FinanceFormatters.formatDateGroupLabel(it.dateMillis) }
    }

  Column(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search merchants or categories") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Rounded.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("transactions_search_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (activeCategory != null) {
          FilterChip(
            selected = true,
            onClick = {
              activeCategory = null
              onClearInitialCategory()
            },
            label = { Text("${activeCategory!!.displayName} ×") },
          )
        }
        ActivityFilter.entries.forEach { filter ->
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { selectedFilter = filter },
            label = { Text(filter.label) },
          )
        }
      }
    }

    if (filtered.isEmpty()) {
      Column(modifier = Modifier.padding(16.dp)) {
        EmptyFinanceStateCard(
          title = if (data.transactions.isEmpty()) "No activity yet" else "No matching transactions",
          message =
            if (data.transactions.isEmpty()) {
              "Add a transaction manually or import a bank statement CSV to see categorized spending here."
            } else {
              "Try clearing your search or switching category filters."
            },
          primaryActionLabel = "Add Transaction",
          onPrimaryAction = onAddTransactionClick,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        grouped.forEach { (dateLabel, itemsInGroup) ->
          item(key = "header_$dateLabel") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = dateLabel,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 4.dp),
            )
          }
          items(itemsInGroup, key = { it.id }) { tx ->
            TransactionRowItem(
              transaction = tx,
              accountLabel = accountMap[tx.accountId]?.name,
              onClick = { onTransactionClick(tx) },
            )
          }
        }
      }
    }
  }
}
