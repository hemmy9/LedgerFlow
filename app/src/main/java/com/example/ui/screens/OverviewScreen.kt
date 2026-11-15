package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.SpendingCategory
import com.example.data.model.SpendingTransaction
import com.example.ui.components.EmptyFinanceStateCard
import com.example.ui.components.SpendingDonutCard
import com.example.ui.components.TransactionRowItem
import com.example.ui.theme.HeroGradientEnd
import com.example.ui.theme.HeroGradientStart
import com.example.ui.theme.MonoFigureLarge
import com.example.ui.theme.MonoFigureMedium
import com.example.ui.theme.accentColor
import com.example.ui.util.FinanceFormatters
import com.example.ui.viewmodel.FinanceDashboardData

@Composable
fun OverviewScreen(
  data: FinanceDashboardData,
  onPrevMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onSyncBankClick: () -> Unit,
  onAddTransactionClick: () -> Unit,
  onTransactionClick: (SpendingTransaction) -> Unit,
  onCategoryFilterClick: (SpendingCategory) -> Unit,
  onViewAllTransactions: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accountMap = data.accounts.associateBy { it.id }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Month Switcher Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onPrevMonth,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("prev_month_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = "Previous month",
          )
        }
        Text(
          text = FinanceFormatters.formatMonthLabel(data.selectedYear, data.selectedMonth),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        IconButton(
          onClick = onNextMonth,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("next_month_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Next month",
          )
        }
      }
    }

    // Net Worth & Monthly Cashflow Hero Card
    item {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
              Brush.linearGradient(
                colors = listOf(HeroGradientStart, HeroGradientEnd)
              )
            )
            .padding(22.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "Net Worth (${data.accounts.size} accounts)",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = FinanceFormatters.formatCents(data.netWorthCents),
                style = MonoFigureLarge,
                color = Color.White,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Surface(
              color = Color.White.copy(alpha = 0.14f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "Monthly Spent",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.78f),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = FinanceFormatters.formatCents(data.monthlySpentCents),
                  style = MonoFigureMedium,
                  color = Color.White,
                )
              }
            }

            Surface(
              color = Color.White.copy(alpha = 0.14f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "Monthly Income",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.78f),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = FinanceFormatters.formatCents(data.monthlyIncomeCents),
                  style = MonoFigureMedium,
                  color = Color(0xFF6EE7B7),
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = onSyncBankClick,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = Color.White,
                  contentColor = HeroGradientStart,
                ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).height(46.dp).testTag("overview_sync_bank_button"),
            ) {
              Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sync Bank")
            }

            OutlinedButton(
              onClick = onAddTransactionClick,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).height(46.dp).testTag("overview_add_tx_button"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Log Spend")
            }
          }
        }
      }
    }

    // Empty onboarding state when no accounts or transactions exist yet
    if (data.accounts.isEmpty() && data.transactions.isEmpty()) {
      item {
        EmptyFinanceStateCard(
          title = "Connect your first bank feed",
          message = "Sync a bank account via Plaid, import a CSV bank statement, or add an account manually to begin categorizing your spending.",
          primaryActionLabel = "Sync Bank or CSV",
          onPrimaryAction = onSyncBankClick,
        )
      }
    } else {
      // Unreviewed auto-categorized banner
      if (data.unreviewedTransactions.isNotEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(18.dp),
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onTransactionClick(data.unreviewedTransactions.first()) },
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${data.unreviewedTransactions.size} synced transactions to review",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                  text = "Tap to confirm or refine auto-assigned categories",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                )
              }
            }
          }
        }
      }

      // Donut Chart Card
      item {
        SpendingDonutCard(
          breakdown = data.categoryBreakdown,
          totalSpentCents = data.monthlySpentCents,
          onCategoryClick = onCategoryFilterClick,
        )
      }

      // Top Category Progress Bars
      if (data.categoryBreakdown.isNotEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = "Top Spending Categories",
                style = MaterialTheme.typography.titleMedium,
              )
              data.categoryBreakdown.take(5).forEach { summary ->
                Column(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(10.dp))
                      .clickable { onCategoryFilterClick(summary.category) }
                      .padding(vertical = 4.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = "${summary.category.displayName} (${summary.transactionCount})",
                      style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                      text = FinanceFormatters.formatCents(summary.totalCents),
                      style = MonoFigureMedium,
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  LinearProgressIndicator(
                    progress = { summary.shareFraction.coerceIn(0f, 1f) },
                    color = summary.category.accentColor(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                  )
                }
              }
            }
          }
        }
      }

      // Recent Transactions
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.titleMedium,
          )
          TextButton(onClick = onViewAllTransactions) {
            Text("See all")
          }
        }
      }

      items(data.transactions.take(6), key = { it.id }) { tx ->
        TransactionRowItem(
          transaction = tx,
          accountLabel = accountMap[tx.accountId]?.name,
          onClick = { onTransactionClick(tx) },
        )
      }
    }
  }
}
