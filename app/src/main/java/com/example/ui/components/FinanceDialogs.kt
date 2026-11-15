package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountType
import com.example.data.model.BankAccount
import com.example.data.model.SpendingCategory
import com.example.data.model.SpendingTransaction
import com.example.data.model.TransactionDirection
import com.example.data.remote.PlaidInstitutionDto
import com.example.domain.CategorizationEngine
import com.example.ui.theme.MonoFigureMedium
import com.example.ui.theme.accentColor
import com.example.ui.util.FinanceFormatters
import com.example.ui.util.icon
import com.example.ui.viewmodel.CsvImportPreview
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
  accounts: List<BankAccount>,
  onDismiss: () -> Unit,
  onSave: (
    accountId: String,
    merchant: String,
    amountCents: Long,
    direction: TransactionDirection,
    explicitCategory: SpendingCategory?,
    note: String,
  ) -> Unit,
) {
  var merchant by remember { mutableStateOf("") }
  var amountText by remember { mutableStateOf("") }
  var direction by remember { mutableStateOf(TransactionDirection.EXPENSE) }
  var selectedAccountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
  var autoCategorize by remember { mutableStateOf(true) }
  var manualCategory by remember { mutableStateOf(SpendingCategory.DINING) }
  var note by remember { mutableStateOf("") }

  val suggestedCategory =
    remember(merchant, direction) {
      if (merchant.isBlank()) SpendingCategory.OTHER
      else {
        val res =
          CategorizationEngine.categorize(
            rawDescription = merchant,
            merchantName = merchant,
            isInflow = direction == TransactionDirection.INCOME,
          )
        SpendingCategory.fromId(res.categoryId)
      }
    }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Add Transaction",
        style = MaterialTheme.typography.headlineSmall,
      )

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TransactionDirection.entries.forEachIndexed { idx, dir ->
          SegmentedButton(
            selected = direction == dir,
            onClick = { direction = dir },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = TransactionDirection.entries.size),
          ) {
            Text(dir.displayName)
          }
        }
      }

      OutlinedTextField(
        value = merchant,
        onValueChange = { merchant = it },
        label = { Text("Merchant or Description") },
        placeholder = { Text("e.g., Trader Joe's, Starbucks, Payroll") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tx_merchant_input"),
      )

      OutlinedTextField(
        value = amountText,
        onValueChange = { input ->
          if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
            amountText = input
          }
        },
        label = { Text("Amount (USD)") },
        prefix = { Text("$") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tx_amount_input"),
      )

      if (accounts.isNotEmpty()) {
        Text("Account", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          accounts.forEach { acc ->
            FilterChip(
              selected = selectedAccountId == acc.id,
              onClick = { selectedAccountId = acc.id },
              label = { Text(acc.name) },
            )
          }
        }
      } else {
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Create or sync an account first in the Accounts tab to link transactions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Auto-categorize merchant", style = MaterialTheme.typography.titleMedium)
          Text(
            text =
              if (autoCategorize && merchant.isNotBlank()) {
                "Detected: ${suggestedCategory.displayName}"
              } else {
                "Choose a category manually"
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(checked = autoCategorize, onCheckedChange = { autoCategorize = it })
      }

      if (!autoCategorize) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SpendingCategory.entries.forEach { cat ->
            FilterChip(
              selected = manualCategory == cat,
              onClick = { manualCategory = cat },
              label = { Text(cat.displayName) },
            )
          }
        }
      }

      OutlinedTextField(
        value = note,
        onValueChange = { note = it.take(200) },
        label = { Text("Note (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      val amountCents = ((amountText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
      val canSave = merchant.isNotBlank() && amountCents > 0L && selectedAccountId.isNotBlank()

      Button(
        onClick = {
          onSave(
            selectedAccountId,
            merchant,
            amountCents,
            direction,
            if (autoCategorize) null else manualCategory,
            note,
          )
        },
        enabled = canSave,
        modifier =
          Modifier.fillMaxWidth()
            .height(50.dp)
            .testTag("save_transaction_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text("Save Transaction")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailSheet(
  transaction: SpendingTransaction,
  accountLabel: String?,
  onDismiss: () -> Unit,
  onSaveUpdate: (
    newCategory: SpendingCategory,
    newDirection: TransactionDirection,
    note: String,
    createRuleForMerchant: Boolean,
  ) -> Unit,
  onDelete: () -> Unit,
) {
  var selectedCategory by remember(transaction.id) {
    mutableStateOf(SpendingCategory.fromId(transaction.categoryId))
  }
  var selectedDirection by remember(transaction.id) {
    mutableStateOf(TransactionDirection.fromId(transaction.direction))
  }
  var note by remember(transaction.id) { mutableStateOf(transaction.note) }
  var createRule by remember(transaction.id) { mutableStateOf(false) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = transaction.merchantName,
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text =
              "${FinanceFormatters.formatFullDate(transaction.dateMillis)} • ${accountLabel ?: "Account"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = FinanceFormatters.formatCents(transaction.amountCents),
          style = MonoFigureMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      if (transaction.rawDescription.isNotBlank() && transaction.rawDescription != transaction.merchantName) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Bank memo: ${transaction.rawDescription}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Text("Type", style = MaterialTheme.typography.labelLarge)
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TransactionDirection.entries.forEachIndexed { idx, dir ->
          SegmentedButton(
            selected = selectedDirection == dir,
            onClick = { selectedDirection = dir },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = TransactionDirection.entries.size),
          ) {
            Text(dir.displayName)
          }
        }
      }

      Text("Category", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        SpendingCategory.entries.forEach { cat ->
          FilterChip(
            selected = selectedCategory == cat,
            onClick = { selectedCategory = cat },
            leadingIcon = {
              Icon(
                imageVector = cat.icon(),
                contentDescription = null,
                tint = cat.accentColor(),
                modifier = Modifier.size(16.dp),
              )
            },
            label = { Text(cat.displayName) },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Always categorize \"${transaction.merchantName}\" as ${selectedCategory.displayName}",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text = "Saves a rule and updates matching unreviewed activity",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(checked = createRule, onCheckedChange = { createRule = it })
      }

      OutlinedTextField(
        value = note,
        onValueChange = { note = it.take(200) },
        label = { Text("Note") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onDelete,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.height(50.dp),
        ) {
          Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete")
          Spacer(modifier = Modifier.width(6.dp))
          Text("Delete")
        }
        Button(
          onClick = { onSaveUpdate(selectedCategory, selectedDirection, note, createRule) },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).height(50.dp).testTag("confirm_recategorize_button"),
        ) {
          Text("Confirm Category")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAccountSheet(
  onDismiss: () -> Unit,
  onSave: (
    name: String,
    institution: String,
    type: AccountType,
    mask: String,
    balanceCents: Long,
  ) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var institution by remember { mutableStateOf("") }
  var mask by remember { mutableStateOf("") }
  var balanceText by remember { mutableStateOf("") }
  var selectedType by remember { mutableStateOf(AccountType.CHECKING) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("Add Account", style = MaterialTheme.typography.headlineSmall)

      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccountType.entries.forEach { type ->
          FilterChip(
            selected = selectedType == type,
            onClick = { selectedType = type },
            label = { Text(type.displayName) },
          )
        }
      }

      OutlinedTextField(
        value = institution,
        onValueChange = { institution = it },
        label = { Text("Institution / Bank") },
        placeholder = { Text("e.g., Chase, Mercury, Fidelity") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("account_institution_input"),
      )

      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Account Name") },
        placeholder = { Text("e.g., Sapphire Preferred, Everyday Checking") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("account_name_input"),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = mask,
          onValueChange = { mask = it.filter { ch -> ch.isDigit() }.take(4) },
          label = { Text("Last 4") },
          placeholder = { Text("4821") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = balanceText,
          onValueChange = { input ->
            if (input.matches(Regex("^-?\\d*(\\.\\d{0,2})?$"))) {
              balanceText = input
            }
          },
          label = { Text("Current Balance") },
          prefix = { Text("$") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(2f).testTag("account_balance_input"),
        )
      }

      val cents = ((balanceText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
      Button(
        onClick = { onSave(name, institution, selectedType, mask, cents) },
        enabled = name.isNotBlank() && institution.isNotBlank(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_account_button"),
      ) {
        Text("Save Account")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSyncHubSheet(
  isPlaidConfigured: Boolean,
  isSearchingInstitutions: Boolean,
  isSyncing: Boolean,
  institutions: List<PlaidInstitutionDto>,
  onSearchInstitutions: (String) -> Unit,
  onConnectPlaidInstitution: (PlaidInstitutionDto) -> Unit,
  onImportCsvClicked: () -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("Sync Bank Accounts", style = MaterialTheme.typography.headlineSmall)

      // Channel 1: Real Bank CSV / Statement Import (always works, zero permissions)
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.FileUpload,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Import Bank Statement (CSV)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text = "Upload an exported CSV from Chase, BofA, Amex, Capital One, or any bank. Auto-deduplicated & categorized.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Button(
            onClick = onImportCsvClicked,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pick_csv_button"),
          ) {
            Text("Choose CSV File")
          }
        }
      }

      HorizontalDivider()

      // Channel 2: Plaid Open Banking API Sync
      Text("Plaid Open Banking Sync", style = MaterialTheme.typography.titleMedium)

      if (!isPlaidConfigured) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Plaid API Credentials Not Set",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "To enable live Plaid Open Banking institution search and /transactions/sync, set PLAID_CLIENT_ID and PLAID_SECRET in the AI Studio Secrets panel and rebuild.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search bank (e.g., Chase, Platypus)") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            modifier = Modifier.weight(1f),
          )
          Button(
            onClick = { onSearchInstitutions(searchQuery) },
            enabled = !isSearchingInstitutions && !isSyncing,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(54.dp),
          ) {
            if (isSearchingInstitutions) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
              Text("Find")
            }
          }
        }

        if (isSyncing) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Syncing accounts & transactions via Plaid...")
          }
        }

        if (institutions.isNotEmpty()) {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(institutions, key = { it.institutionId }) { inst ->
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !isSyncing) { onConnectPlaidInstitution(inst) },
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = inst.name,
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = inst.institutionId,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = "Connect",
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CsvImportPreviewSheet(
  preview: CsvImportPreview,
  accounts: List<BankAccount>,
  isSyncing: Boolean,
  onConfirmImport: (
    targetAccountId: String?,
    newAccountName: String,
    newInstitutionName: String,
    newAccountType: AccountType,
    newBalanceCents: Long,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  var useExisting by remember(accounts) { mutableStateOf(accounts.isNotEmpty()) }
  var selectedAccountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
  var newAccountName by remember { mutableStateOf("Imported Checking") }
  var newInstitution by remember { mutableStateOf("Bank Statement") }
  var newType by remember { mutableStateOf(AccountType.CHECKING) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Confirm Statement Sync", style = MaterialTheme.typography.headlineSmall)
      Text(
        text =
          "${preview.parsedTransactions.size} transactions detected from ${preview.rawFileName}" +
            if (preview.skippedRows > 0) " (${preview.skippedRows} non-transaction rows skipped)" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (accounts.isNotEmpty()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = useExisting,
            onClick = { useExisting = true },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
          ) {
            Text("Existing Account")
          }
          SegmentedButton(
            selected = !useExisting,
            onClick = { useExisting = false },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
          ) {
            Text("New Account")
          }
        }
      }

      if (useExisting && accounts.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          accounts.forEach { acc ->
            FilterChip(
              selected = selectedAccountId == acc.id,
              onClick = { selectedAccountId = acc.id },
              label = { Text(acc.name) },
            )
          }
        }
      } else {
        OutlinedTextField(
          value = newInstitution,
          onValueChange = { newInstitution = it },
          label = { Text("Bank Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = newAccountName,
          onValueChange = { newAccountName = it },
          label = { Text("Account Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AccountType.entries.forEach { type ->
            FilterChip(
              selected = newType == type,
              onClick = { newType = type },
              label = { Text(type.displayName) },
            )
          }
        }
      }

      Text("Preview (Auto-Categorized)", style = MaterialTheme.typography.labelLarge)
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(preview.parsedTransactions.take(25)) { tx ->
          val cat = SpendingCategory.fromId(tx.categoryId)
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = tx.merchantName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${cat.displayName} • ${FinanceFormatters.formatShortDate(tx.dateMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = FinanceFormatters.formatCents(tx.amountCents),
              style = MonoFigureMedium,
            )
          }
        }
      }

      Button(
        onClick = {
          onConfirmImport(
            if (useExisting) selectedAccountId else null,
            newAccountName,
            newInstitution,
            newType,
            0L,
          )
        },
        enabled = !isSyncing && (if (useExisting) selectedAccountId.isNotBlank() else newAccountName.isNotBlank()),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp),
      ) {
        if (isSyncing) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Text("Import ${preview.parsedTransactions.size} Transactions")
        }
      }
    }
  }
}

@Composable
fun BudgetEditorDialog(
  category: SpendingCategory,
  currentLimitCents: Long?,
  onDismiss: () -> Unit,
  onSave: (Long) -> Unit,
  onDelete: (() -> Unit)?,
) {
  var limitText by remember(category) {
    mutableStateOf(
      if (currentLimitCents != null && currentLimitCents > 0L) {
        (currentLimitCents / 100.0).toString().removeSuffix(".0")
      } else ""
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("${category.displayName} Budget") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Set a monthly spending target for ${category.displayName}.",
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = limitText,
          onValueChange = { input ->
            if (input.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) {
              limitText = input
            }
          },
          label = { Text("Monthly Limit (USD)") },
          prefix = { Text("$") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("budget_limit_input"),
        )
      }
    },
    confirmButton = {
      val cents = ((limitText.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong()
      Button(
        onClick = { onSave(cents) },
        enabled = cents >= 100L,
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      Row {
        if (onDelete != null) {
          TextButton(onClick = onDelete) {
            Text("Remove", color = MaterialTheme.colorScheme.error)
          }
        }
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRuleDialog(
  onDismiss: () -> Unit,
  onSave: (keyword: String, category: SpendingCategory) -> Unit,
) {
  var keyword by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(SpendingCategory.GROCERIES) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Categorization Rule") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = keyword,
          onValueChange = { keyword = it.take(80) },
          label = { Text("Merchant Keyword") },
          placeholder = { Text("e.g., TRADER JOE, CHEVRON") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("rule_keyword_input"),
        )
        Text("Assign Category", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          SpendingCategory.entries.forEach { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { selectedCategory = cat },
              label = { Text(cat.displayName) },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(keyword, selectedCategory) },
        enabled = keyword.trim().length >= 2,
      ) {
        Text("Save Rule")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
