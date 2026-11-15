package com.example

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.SpendingCategory
import com.example.data.model.SpendingTransaction
import com.example.data.model.UiState
import com.example.data.repository.FinanceRepository
import com.example.data.repository.PlaidSyncRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.components.AddAccountSheet
import com.example.ui.components.AddTransactionSheet
import com.example.ui.components.BankSyncHubSheet
import com.example.ui.components.CsvImportPreviewSheet
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.BudgetsAndRulesScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.LedgerFlowTheme
import com.example.ui.viewmodel.FinanceDashboardData
import com.example.ui.viewmodel.FinanceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_OVERVIEW = "overview"
private const val ROUTE_TRANSACTIONS = "transactions"
private const val ROUTE_ACCOUNTS = "accounts"
private const val ROUTE_BUDGETS = "budgets"

private data class NavDestination(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

private val appDestinations =
  listOf(
    NavDestination(ROUTE_OVERVIEW, R.string.nav_overview, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    NavDestination(ROUTE_TRANSACTIONS, R.string.nav_transactions, Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    NavDestination(ROUTE_ACCOUNTS, R.string.nav_accounts, Icons.Rounded.AccountBalance, Icons.Outlined.AccountBalance),
    NavDestination(ROUTE_BUDGETS, R.string.nav_budgets, Icons.Rounded.PieChart, Icons.Outlined.PieChart),
  )

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      LedgerFlowTheme {
        AppRoot()
      }
    }
  }
}

@Composable
fun AppRoot() {
  val auth = Firebase.auth
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      LedgerFlowHome(
        currentUserId = user.uid,
        userEmail = user.email,
        userDisplayName = user.displayName,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerFlowHome(
  currentUserId: String,
  userEmail: String?,
  userDisplayName: String?,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: FinanceViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val financeRepo = FinanceRepository(db)
            val plaidRepo = PlaidSyncRepository(financeRepository = financeRepo)
            FinanceViewModel(financeRepo, plaidRepo, currentUserId)
          }
        },
    )

  val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
  val actionState by viewModel.actionState.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_OVERVIEW
  val snackbarHostState = remember { SnackbarHostState() }

  var showAddTxSheet by remember { mutableStateOf(false) }
  var showAddAccountSheet by remember { mutableStateOf(false) }
  var showSyncHubSheet by remember { mutableStateOf(false) }
  var selectedTxForDetail by remember { mutableStateOf<SpendingTransaction?>(null) }
  var pendingCategoryFilter by remember { mutableStateOf<SpendingCategory?>(null) }

  val currentData = (dashboardState as? UiState.Success<FinanceDashboardData>)?.data

  val csvPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      if (uri != null) {
        val fileName = resolveDisplayName(context, uri) ?: "statement.csv"
        val csvText =
          try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
          } catch (_: Exception) {
            ""
          }
        if (csvText.isNotBlank()) {
          showSyncHubSheet = false
          viewModel.prepareCsvPreview(
            fileName = fileName,
            csvContent = csvText,
            accountId = currentData?.accounts?.firstOrNull()?.id.orEmpty(),
            customRules = currentData?.categoryRules.orEmpty(),
          )
        }
      }
    }

  LaunchedEffect(actionState.statusMessage, actionState.errorMessage) {
    val msg = actionState.errorMessage ?: actionState.statusMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearMessages()
    }
  }

  val navigateToTab: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (useNavigationRail) {
        NavigationRail(
          containerColor = MaterialTheme.colorScheme.surface,
          modifier = Modifier.fillMaxHeight(),
        ) {
          Spacer(modifier = Modifier.height(16.dp))
          appDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationRailItem(
              selected = selected,
              onClick = { navigateToTab(dest.route) },
              icon = {
                Icon(
                  imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                  contentDescription = stringResource(dest.labelRes),
                )
              },
              label = { Text(stringResource(dest.labelRes)) },
            )
          }
        }
      }

      Scaffold(
        modifier = Modifier.weight(1f),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
          TopAppBar(
            title = {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
              )
            },
            actions = {
              IconButton(
                onClick = {
                  showSyncHubSheet = true
                  if (viewModel.isPlaidConfigured && actionState.plaidInstitutions.isEmpty()) {
                    viewModel.searchPlaidInstitutions("")
                  }
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_sync_button"),
              ) {
                Icon(Icons.Rounded.Sync, contentDescription = stringResource(R.string.action_sync_bank))
              }
            },
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.primary,
              ),
          )
        },
        floatingActionButton = {
          if (currentRoute == ROUTE_OVERVIEW || currentRoute == ROUTE_TRANSACTIONS) {
            FloatingActionButton(
              onClick = { showAddTxSheet = true },
              shape = RoundedCornerShape(16.dp),
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.testTag("fab_add_transaction"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_add_transaction))
            }
          }
        },
        bottomBar = {
          if (!useNavigationRail) {
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
              windowInsets = WindowInsets.navigationBars,
            ) {
              appDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateToTab(dest.route) },
                  icon = {
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = stringResource(dest.labelRes),
                    )
                  },
                  label = { Text(stringResource(dest.labelRes)) },
                  modifier = Modifier.testTag("nav_${dest.route}"),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.TopCenter,
        ) {
          Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
            when (val state = dashboardState) {
              UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator()
                }
              }
              is UiState.Error -> {
                Column(
                  modifier = Modifier.fillMaxSize().padding(24.dp),
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                  )
                  Spacer(modifier = Modifier.height(12.dp))
                  Button(onClick = { viewModel.shiftMonth(0) }) {
                    Text(stringResource(R.string.action_retry))
                  }
                }
              }
              is UiState.Success -> {
                val data = state.data
                NavHost(
                  navController = navController,
                  startDestination = ROUTE_OVERVIEW,
                ) {
                  composable(ROUTE_OVERVIEW) {
                    OverviewScreen(
                      data = data,
                      onPrevMonth = { viewModel.shiftMonth(-1) },
                      onNextMonth = { viewModel.shiftMonth(1) },
                      onSyncBankClick = {
                        showSyncHubSheet = true
                        if (viewModel.isPlaidConfigured && actionState.plaidInstitutions.isEmpty()) {
                          viewModel.searchPlaidInstitutions("")
                        }
                      },
                      onAddTransactionClick = { showAddTxSheet = true },
                      onTransactionClick = { selectedTxForDetail = it },
                      onCategoryFilterClick = { cat ->
                        pendingCategoryFilter = cat
                        navigateToTab(ROUTE_TRANSACTIONS)
                      },
                      onViewAllTransactions = { navigateToTab(ROUTE_TRANSACTIONS) },
                    )
                  }
                  composable(ROUTE_TRANSACTIONS) {
                    TransactionsScreen(
                      data = data,
                      initialCategoryFilter = pendingCategoryFilter,
                      onClearInitialCategory = { pendingCategoryFilter = null },
                      onTransactionClick = { selectedTxForDetail = it },
                      onAddTransactionClick = { showAddTxSheet = true },
                    )
                  }
                  composable(ROUTE_ACCOUNTS) {
                    AccountsScreen(
                      data = data,
                      isSyncing = actionState.isSyncing,
                      onSyncBankClick = {
                        showSyncHubSheet = true
                        if (viewModel.isPlaidConfigured && actionState.plaidInstitutions.isEmpty()) {
                          viewModel.searchPlaidInstitutions("")
                        }
                      },
                      onRefreshPlaidClick = {
                        viewModel.syncAllPlaidConnections(data.plaidConnections, data.categoryRules)
                      },
                      onAddManualAccountClick = { showAddAccountSheet = true },
                      onUpdateBalance = { id, cents -> viewModel.updateAccountBalance(id, cents) },
                      onDeleteAccount = { id -> viewModel.deleteAccount(id) },
                    )
                  }
                  composable(ROUTE_BUDGETS) {
                    BudgetsAndRulesScreen(
                      data = data,
                      userEmail = userEmail,
                      userDisplayName = userDisplayName,
                      onSaveBudget = { cat, cents -> viewModel.upsertBudget(cat, cents) },
                      onDeleteBudget = { id -> viewModel.deleteBudget(id) },
                      onCreateRule = { kw, cat ->
                        viewModel.createCategoryRule(kw, cat, data.transactions, data.categoryRules)
                      },
                      onDeleteRule = { id -> viewModel.deleteCategoryRule(id) },
                      onReapplyRules = {
                        viewModel.reapplyAllRules(data.transactions, data.categoryRules)
                      },
                      onSignOut = {
                        signOut(credentialManager = credentialManager, scope = scope)
                      },
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

  // Modal Sheets
  if (showAddTxSheet && currentData != null) {
    AddTransactionSheet(
      accounts = currentData.accounts,
      onDismiss = { showAddTxSheet = false },
      onSave = { accountId, merchant, amountCents, direction, explicitCategory, note ->
        viewModel.addManualTransaction(
          accountId = accountId,
          merchantInput = merchant,
          amountCents = amountCents,
          direction = direction,
          explicitCategory = explicitCategory,
          dateMillis = System.currentTimeMillis(),
          note = note,
          customRules = currentData.categoryRules,
        )
        showAddTxSheet = false
      },
    )
  }

  selectedTxForDetail?.let { tx ->
    if (currentData != null) {
      val accountName = currentData.accounts.firstOrNull { it.id == tx.accountId }?.name
      TransactionDetailSheet(
        transaction = tx,
        accountLabel = accountName,
        onDismiss = { selectedTxForDetail = null },
        onSaveUpdate = { newCat, newDir, note, createRule ->
          viewModel.recategorizeTransaction(
            tx = tx,
            newCategory = newCat,
            newDirection = newDir,
            note = note,
            createRuleForMerchant = createRule,
            allTransactions = currentData.transactions,
            existingRules = currentData.categoryRules,
          )
          selectedTxForDetail = null
        },
        onDelete = {
          viewModel.deleteTransaction(tx.id)
          selectedTxForDetail = null
        },
      )
    }
  }

  if (showAddAccountSheet) {
    AddAccountSheet(
      onDismiss = { showAddAccountSheet = false },
      onSave = { name, institution, type, mask, balanceCents ->
        viewModel.createManualAccount(name, institution, type, mask, balanceCents)
        showAddAccountSheet = false
      },
    )
  }

  if (showSyncHubSheet) {
    BankSyncHubSheet(
      isPlaidConfigured = viewModel.isPlaidConfigured,
      isSearchingInstitutions = actionState.isSearchingInstitutions,
      isSyncing = actionState.isSyncing,
      institutions = actionState.plaidInstitutions,
      onSearchInstitutions = { query -> viewModel.searchPlaidInstitutions(query) },
      onConnectPlaidInstitution = { inst ->
        viewModel.connectPlaidInstitution(inst, currentData?.categoryRules.orEmpty())
        showSyncHubSheet = false
      },
      onImportCsvClicked = {
        csvPickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
      },
      onDismiss = { showSyncHubSheet = false },
    )
  }

  actionState.csvPreview?.let { preview ->
    CsvImportPreviewSheet(
      preview = preview,
      accounts = currentData?.accounts.orEmpty(),
      isSyncing = actionState.isSyncing,
      onConfirmImport = { targetAccountId, newName, newInst, newType, newBalance ->
        viewModel.commitCsvImport(targetAccountId, newName, newInst, newType, newBalance)
      },
      onDismiss = { viewModel.dismissCsvPreview() },
    )
  }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
  return try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
  } catch (_: Exception) {
    null
  }
}
