package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.SpendingCategory
import com.example.ui.components.AddRuleDialog
import com.example.ui.components.BudgetEditorDialog
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.MonoFigureMedium
import com.example.ui.theme.accentColor
import com.example.ui.util.FinanceFormatters
import com.example.ui.util.icon
import com.example.ui.viewmodel.BudgetProgress
import com.example.ui.viewmodel.FinanceDashboardData

@Composable
fun BudgetsAndRulesScreen(
  data: FinanceDashboardData,
  userEmail: String?,
  userDisplayName: String?,
  onSaveBudget: (SpendingCategory, Long) -> Unit,
  onDeleteBudget: (String) -> Unit,
  onCreateRule: (keyword: String, category: SpendingCategory) -> Unit,
  onDeleteRule: (ruleId: String) -> Unit,
  onReapplyRules: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingBudgetCategory by remember { mutableStateOf<BudgetProgress?>(null) }
  var showAddRuleDialog by remember { mutableStateOf(false) }

  val displayProgress =
    remember(data.budgetProgress) {
      if (data.budgetProgress.isNotEmpty()) {
        data.budgetProgress
      } else {
        SpendingCategory.expenseCategories.take(6).map { cat ->
          BudgetProgress(
            category = cat,
            budget = null,
            spentCents = 0L,
            progressFraction = 0f,
            isOverBudget = false,
          )
        }
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Monthly Budgets Section
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("Monthly Budgets", style = MaterialTheme.typography.titleLarge)
          Text(
            text = FinanceFormatters.formatMonthLabel(data.selectedYear, data.selectedMonth),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        TextButton(
          onClick = {
            val nextUnbudgeted =
              SpendingCategory.expenseCategories.firstOrNull { cat ->
                data.budgets.none { it.categoryId == cat.id }
              } ?: SpendingCategory.GROCERIES
            editingBudgetCategory =
              BudgetProgress(nextUnbudgeted, null, 0L, 0f, false)
          },
          modifier = Modifier.testTag("add_budget_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Set Limit")
        }
      }
    }

    items(displayProgress, key = { it.category.id }) { item ->
      BudgetProgressCard(
        progress = item,
        onClick = { editingBudgetCategory = item },
      )
    }

    // Auto-Categorization Rules Section
    item {
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Categorization Rules", style = MaterialTheme.typography.titleLarge)
          Text(
            text = "Built-in engine covers 120+ merchants. Add custom rules below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = { showAddRuleDialog = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("add_rule_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Rule")
        }
      }
    }

    if (data.categoryRules.isEmpty()) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "No custom rules yet. Tap \"+ Rule\" or recategorize any transaction and toggle \"Always categorize\" to train your rules.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    } else {
      items(data.categoryRules, key = { it.id }) { rule ->
        val cat = SpendingCategory.fromId(rule.categoryId)
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = cat.accentColor().copy(alpha = 0.14f),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.size(38.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = cat.icon(),
                  contentDescription = cat.displayName,
                  tint = cat.accentColor(),
                  modifier = Modifier.size(20.dp),
                )
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(text = "Contains \"${rule.keyword}\"", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "Assign -> ${cat.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(onClick = { onDeleteRule(rule.id) }) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete rule",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }

      if (data.unreviewedTransactions.isNotEmpty()) {
        item {
          OutlinedButton(
            onClick = onReapplyRules,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Re-apply Rules to ${data.unreviewedTransactions.size} Unreviewed Items")
          }
        }
      }
    }

    // Account & Profile Card
    item {
      Spacer(modifier = Modifier.height(8.dp))
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(44.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = userDisplayName?.takeIf { it.isNotBlank() } ?: "Google Account",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = userEmail.orEmpty().ifBlank { "Signed in" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          TextButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("sign_out_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Sign out")
          }
        }
      }
    }
  }

  editingBudgetCategory?.let { item ->
    BudgetEditorDialog(
      category = item.category,
      currentLimitCents = item.budget?.monthlyLimitCents,
      onDismiss = { editingBudgetCategory = null },
      onSave = { limitCents ->
        onSaveBudget(item.category, limitCents)
        editingBudgetCategory = null
      },
      onDelete =
        item.budget?.let { budget ->
          {
            onDeleteBudget(budget.id)
            editingBudgetCategory = null
          }
        },
    )
  }

  if (showAddRuleDialog) {
    AddRuleDialog(
      onDismiss = { showAddRuleDialog = false },
      onSave = { kw, cat ->
        onCreateRule(kw, cat)
        showAddRuleDialog = false
      },
    )
  }
}

@Composable
private fun BudgetProgressCard(
  progress: BudgetProgress,
  onClick: () -> Unit,
) {
  val catColor = progress.category.accentColor()
  val limit = progress.budget?.monthlyLimitCents

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onClick),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = catColor.copy(alpha = 0.14f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.size(36.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = progress.category.icon(),
              contentDescription = progress.category.displayName,
              tint = catColor,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = progress.category.displayName,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        Text(
          text =
            if (limit != null) {
              "${FinanceFormatters.formatCents(progress.spentCents)} / ${FinanceFormatters.formatCents(limit)}"
            } else {
              "${FinanceFormatters.formatCents(progress.spentCents)} • Tap to set limit"
            },
          style = if (limit != null) MonoFigureMedium else MaterialTheme.typography.bodySmall,
          color = if (progress.isOverBudget) ExpenseCoral else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (limit != null) {
        LinearProgressIndicator(
          progress = { progress.progressFraction.coerceIn(0f, 1f) },
          color = if (progress.isOverBudget) ExpenseCoral else catColor,
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
      }
    }
  }
}
