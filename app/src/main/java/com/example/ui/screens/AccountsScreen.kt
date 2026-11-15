package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.SyncSource
import com.example.ui.components.EmptyFinanceStateCard
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeEmerald
import com.example.ui.theme.MonoFigureLarge
import com.example.ui.theme.MonoFigureMedium
import com.example.ui.util.FinanceFormatters
import com.example.ui.util.icon
import com.example.ui.viewmodel.FinanceDashboardData
import kotlin.math.roundToLong

@Composable
fun AccountsScreen(
  data: FinanceDashboardData,
  isSyncing: Boolean,
  onSyncBankClick: () -> Unit,
  onRefreshPlaidClick: () -> Unit,
  onAddManualAccountClick: () -> Unit,
  onUpdateBalance: (accountId: String, newBalanceCents: Long) -> Unit,
  onDeleteAccount: (accountId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingBalanceAccount by remember { mutableStateOf<BankAccount?>(null) }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Assets & Liabilities",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = FinanceFormatters.formatCents(data.netWorthCents),
            style = MonoFigureLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Total Assets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = FinanceFormatters.formatCents(data.assetsCents),
                style = MonoFigureMedium,
                color = IncomeEmerald,
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Credit & Debt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = FinanceFormatters.formatCents(data.liabilitiesCents),
                style = MonoFigureMedium,
                color = ExpenseCoral,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = onSyncBankClick,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("accounts_sync_button"),
            ) {
              Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sync Bank / CSV")
            }
            OutlinedButton(
              onClick = onAddManualAccountClick,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("accounts_add_manual_button"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Manual")
            }
          }

          if (data.plaidConnections.isNotEmpty()) {
            OutlinedButton(
              onClick = onRefreshPlaidClick,
              enabled = !isSyncing,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            ) {
              if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refreshing Plaid feeds...")
              } else {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Refresh ${data.plaidConnections.size} Connected Bank(s)")
              }
            }
          }
        }
      }
    }

    if (data.accounts.isEmpty()) {
      item {
        EmptyFinanceStateCard(
          title = "No accounts linked yet",
          message = "Link a bank account via Plaid, import a bank statement CSV, or add your checking, savings, and card balances.",
          primaryActionLabel = "Add or Sync Account",
          onPrimaryAction = onSyncBankClick,
        )
      }
    } else {
      items(data.accounts, key = { it.id }) { account ->
        AccountCard(
          account = account,
          onEditBalance = { editingBalanceAccount = account },
          onDelete = { onDeleteAccount(account.id) },
        )
      }
    }
  }

  editingBalanceAccount?.let { account ->
    EditAccountBalanceDialog(
      account = account,
      onDismiss = { editingBalanceAccount = null },
      onSave = { newCents ->
        onUpdateBalance(account.id, newCents)
        editingBalanceAccount = null
      },
    )
  }
}

@Composable
private fun AccountCard(
  account: BankAccount,
  onEditBalance: () -> Unit,
  onDelete: () -> Unit,
) {
  val type = AccountType.fromId(account.accountType)
  val source = SyncSource.fromId(account.syncSource)

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(44.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = type.icon(),
            contentDescription = type.displayName,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = account.name,
          style = MaterialTheme.typography.titleMedium,
        )
        val maskStr = if (account.mask.isNotBlank()) " ••••${account.mask}" else ""
        Text(
          text = "${account.institutionName}$maskStr • ${source.displayName}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (account.lastSyncedAtMillis > 0L) {
          Text(
            text = "Synced ${FinanceFormatters.formatFullDate(account.lastSyncedAtMillis)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = FinanceFormatters.formatCents(account.balanceCents),
          style = MonoFigureMedium,
          color = if (type.isLiability) ExpenseCoral else MaterialTheme.colorScheme.onSurface,
        )
        Row {
          IconButton(onClick = onEditBalance, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = Icons.Rounded.Edit,
              contentDescription = "Edit balance",
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = "Delete account",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EditAccountBalanceDialog(
  account: BankAccount,
  onDismiss: () -> Unit,
  onSave: (Long) -> Unit,
) {
  var balanceText by remember(account.id) {
    mutableStateOf((account.balanceCents / 100.0).toString())
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Update ${account.name}") },
    text = {
      OutlinedTextField(
        value = balanceText,
        onValueChange = { input ->
          if (input.matches(Regex("^-?\\d*(\\.\\d{0,2})?$"))) {
            balanceText = input
          }
        },
        label = { Text("Current Balance (USD)") },
        prefix = { Text("$") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      val cents = ((balanceText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
      Button(onClick = { onSave(cents) }) {
        Text("Update")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
