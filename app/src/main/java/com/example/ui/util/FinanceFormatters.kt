package com.example.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.AccountType
import com.example.data.model.SpendingCategory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object FinanceFormatters {

  fun formatCents(cents: Long, showPlus: Boolean = false): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    val dollars = abs(cents) / 100.0
    val formatted = formatter.format(dollars)
    return when {
      cents < 0 -> "-$formatted"
      showPlus && cents > 0 -> "+$formatted"
      else -> formatted
    }
  }

  fun formatShortDate(millis: Long): String {
    if (millis <= 0L) return "Unknown date"
    val fmt = SimpleDateFormat("MMM d", Locale.US)
    return fmt.format(Date(millis))
  }

  fun formatFullDate(millis: Long): String {
    if (millis <= 0L) return "Unknown date"
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return fmt.format(Date(millis))
  }

  fun formatDateGroupLabel(millis: Long): String {
    if (millis <= 0L) return "Earlier"
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
      isSameDay(target, today) -> "Today"
      isSameDay(target, yesterday) -> "Yesterday"
      else -> SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(millis))
    }
  }

  fun formatMonthLabel(year: Int, monthZeroIndexed: Int): String {
    val cal = Calendar.getInstance().apply {
      set(Calendar.YEAR, year)
      set(Calendar.MONTH, monthZeroIndexed)
      set(Calendar.DAY_OF_MONTH, 1)
    }
    return SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
  }

  fun isInMonth(millis: Long, year: Int, monthZeroIndexed: Int): Boolean {
    if (millis <= 0L) return false
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == monthZeroIndexed
  }

  private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
      a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

fun SpendingCategory.icon(): ImageVector =
  when (this) {
    SpendingCategory.GROCERIES -> Icons.Rounded.ShoppingCart
    SpendingCategory.DINING -> Icons.Rounded.Restaurant
    SpendingCategory.TRANSPORT -> Icons.Rounded.DirectionsCar
    SpendingCategory.HOUSING -> Icons.Rounded.Home
    SpendingCategory.SHOPPING -> Icons.Rounded.ShoppingBag
    SpendingCategory.SUBSCRIPTIONS -> Icons.Rounded.Subscriptions
    SpendingCategory.ENTERTAINMENT -> Icons.Rounded.Movie
    SpendingCategory.HEALTH -> Icons.Rounded.LocalHospital
    SpendingCategory.TRAVEL -> Icons.Rounded.Flight
    SpendingCategory.INCOME -> Icons.Rounded.AttachMoney
    SpendingCategory.TRANSFER -> Icons.Rounded.SwapHoriz
    SpendingCategory.OTHER -> Icons.Rounded.MoreHoriz
  }

fun AccountType.icon(): ImageVector =
  when (this) {
    AccountType.CHECKING -> Icons.Rounded.AccountBalance
    AccountType.SAVINGS -> Icons.Rounded.Savings
    AccountType.CREDIT -> Icons.Rounded.CreditCard
    AccountType.INVESTMENT -> Icons.AutoMirrored.Rounded.TrendingUp
    AccountType.CASH -> Icons.Rounded.AccountBalanceWallet
  }
