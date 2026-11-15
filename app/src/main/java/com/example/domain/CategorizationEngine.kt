package com.example.domain

import com.example.data.model.CategoryRule
import com.example.data.model.SpendingCategory
import com.example.data.model.TransactionDirection
import java.util.Locale

data class CategorizationResult(
  val categoryId: String,
  val direction: String,
  val normalizedMerchant: String,
  val matchedRule: String? = null,
)

object CategorizationEngine {

  private val builtInRules: List<Pair<List<String>, SpendingCategory>> =
    listOf(
      listOf(
        "TRADER JOE", "WHOLE FOODS", "KROGER", "SAFEWAY", "ALDI", "PUBLIX",
        "WEGMANS", "HEB", "SPROUTS", "COSTCO", "SAM'S CLUB", "INSTACART",
        "FRESH MARKET", "GROCERY", "MARKET", "SUPERMARKET", "EREWHON",
      ) to SpendingCategory.GROCERIES,
      listOf(
        "STARBUCKS", "BLUE BOTTLE", "PEET'S", "PHILZ", "DUNKIN", "CHIPOTLE",
        "SWEETGREEN", "CAVA", "PANERA", "CHICK-FIL-A", "MCDONALD", "SHAKE SHACK",
        "IN-N-OUT", "UBER EATS", "DOORDASH", "GRUBHUB", "POSTMATES", "CAFE",
        "COFFEE", "BAKERY", "PIZZA", "SUSHI", "RAMEN", "TACOS", "BISTRO",
        "RESTAURANT", "DINER", "BAR & GRILL", "BREWERY", "TST*", "TOAST",
      ) to SpendingCategory.DINING,
      listOf(
        "UBER", "LYFT", "WAYMO", "SHELL", "CHEVRON", "EXXON", "MOBIL", "BP ",
        "ARCO", "VALERO", "76 ", "GAS", "FUEL", "PARKING", "CLIPPER", "MTA",
        "BART", "METRO", "TRANSIT", "TOLL", "FASTRAK", "EZPASS", "ZIPCAR",
        "CAR WASH", "AUTOZONE", "JIFFY LUBE",
      ) to SpendingCategory.TRANSPORT,
      listOf(
        "DELTA", "UNITED", "AMERICAN AIR", "SOUTHWEST", "JETBLUE", "ALASKA AIR",
        "AIRBNB", "VRBO", "MARRIOTT", "HILTON", "HYATT", "EXPEDIA", "BOOKING.COM",
        "HOTEL", "AIRLINE", "FLIGHT", "TSA PRE", "CLEARME",
      ) to SpendingCategory.TRAVEL,
      listOf(
        "NETFLIX", "SPOTIFY", "APPLE.COM/BILL", "APPLE ONE", "ICLOUD", "GOOGLE ONE",
        "YOUTUBE", "HULU", "DISNEY", "MAX.COM", "HBO", "PRIME VIDEO", "ADOBE",
        "CHATGPT", "OPENAI", "GITHUB", "NOTION", "DROPBOX", "NYTIMES", "WSJ",
        "SUBSTACK", "PATREON", "MEMBERSHIP", "SUBSCRIPTION",
      ) to SpendingCategory.SUBSCRIPTIONS,
      listOf(
        "PG&E", "PGE", "CONED", "CON EDISON", "LADWP", "SCE ", "DUKE ENERGY",
        "VERIZON", "AT&T", "T-MOBILE", "TMOBILE", "COMCAST", "XFINITY", "SPECTRUM",
        "WATER", "ELECTRIC", "UTILITY", "RENT", "MORTGAGE", "HOA", "APARTMENT",
        "AVALON", "EQUITY RESIDENTIAL", "PROPERTY", "INSURANCE", "GEICO",
        "PROGRESSIVE", "STATE FARM", "LEMONADE",
      ) to SpendingCategory.HOUSING,
      listOf(
        "CVS", "WALGREENS", "RITE AID", "KAISER", "ONE MEDICAL", "QUEST DIAG",
        "LABCORP", "DENTAL", "OPTOMETRY", "PHARMACY", "CLINIC", "HOSPITAL",
        "EQUINOX", "ORANGETHEORY", "BARRY'S", "CLASSPASS", "PLANET FITNESS",
        "YMCA", "PELOTON", "THERAPY", "MEDICAL",
      ) to SpendingCategory.HEALTH,
      listOf(
        "AMC", "REGAL", "ALAMO DRAFTHOUSE", "TICKETMASTER", "STUBHUB", "AXS",
        "LIVE NATION", "BOWLING", "MUSEUM", "CONCERT", "CINEMA", "THEATER",
        "NINTENDO", "PLAYSTATION", "STEAM", "XBOX", "ARCADE",
      ) to SpendingCategory.ENTERTAINMENT,
      listOf(
        "AMAZON", "AMZN", "TARGET", "WALMART", "BEST BUY", "APPLE STORE",
        "IKEA", "HOME DEPOT", "LOWE'S", "LOWES", "NORDSTROM", "MACY'S",
        "UNIQLO", "ZARA", "GAP", "NIKE", "LULULEMON", "SEPHORA", "ULTA",
        "ETSY", "WAYFAIR", "CRATE & BARREL", "REI", "BOOKSHOP",
      ) to SpendingCategory.SHOPPING,
      listOf(
        "PAYROLL", "DIRECT DEP", "SALARY", "GUSTO", "ADP", "RIPPLING",
        "TAX REFUND", "IRS TREAS", "DIVIDEND", "INTEREST PAID", "CASHBACK",
        "REWARD", "VENMO CASHOUT", "REFUND",
      ) to SpendingCategory.INCOME,
      listOf(
        "TRANSFER", "ONLINE TRANSFER", "INTERNAL XFER", "ZELLE", "ACH PULL",
        "ACH DEPOSIT", "CREDIT CARD PMT", "AUTOPAY", "PAYMENT THANK YOU",
        "CHASE CARD SERV", "AMEX EPAYMENT", "CITI AUTOPAY", "SAVINGS TRANSFER",
      ) to SpendingCategory.TRANSFER,
    )

  fun normalizeMerchantName(rawDescription: String): String {
    var cleaned =
      rawDescription
        .trim()
        .replace(Regex("^(POS DEBIT|POS PURCHASE|DEBIT CARD|CHECKCARD|ACH|ONLINE|PURCHASE)\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^(TST\\*|SQ \\*|SP \\*|PP\\*|PAYPAL \\*|APLPAY )\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("#\\d+"), "")
        .replace(Regex("\\b\\d{5,}\\b"), "")
        .replace(Regex("\\b[A-Z]{2}\\s*\\d{5}$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (cleaned.isEmpty()) {
      cleaned = rawDescription.trim().ifEmpty { "Unknown Merchant" }
    }

    return cleaned
      .split(" ")
      .filter { it.isNotBlank() }
      .take(5)
      .joinToString(" ") { word ->
        val lower = word.lowercase(Locale.US)
        if (lower.length <= 2 && word.all { it.isLetter() } && word == word.uppercase(Locale.US)) {
          word
        } else {
          lower.replaceFirstChar { it.titlecase(Locale.US) }
        }
      }
      .take(100)
  }

  fun categorize(
    rawDescription: String,
    merchantName: String = "",
    isInflow: Boolean = false,
    plaidPrimaryCategory: String? = null,
    plaidDetailedCategory: String? = null,
    csvCategoryHint: String? = null,
    customRules: List<CategoryRule> = emptyList(),
  ): CategorizationResult {
    val normalizedMerchant =
      merchantName.trim().ifEmpty { normalizeMerchantName(rawDescription) }
    val searchSpace = "${rawDescription.uppercase(Locale.US)} ${normalizedMerchant.uppercase(Locale.US)}"

    // 1. User-defined custom rules take highest precedence
    for (rule in customRules) {
      val token = rule.keyword.trim().uppercase(Locale.US)
      if (token.isNotEmpty() && searchSpace.contains(token)) {
        val cat = SpendingCategory.fromId(rule.categoryId)
        val direction =
          when {
            cat == SpendingCategory.INCOME -> TransactionDirection.INCOME.id
            cat == SpendingCategory.TRANSFER -> TransactionDirection.TRANSFER.id
            isInflow -> TransactionDirection.INCOME.id
            else -> TransactionDirection.EXPENSE.id
          }
        return CategorizationResult(
          categoryId = cat.id,
          direction = direction,
          normalizedMerchant = normalizedMerchant,
          matchedRule = rule.keyword,
        )
      }
    }

    // 2. Built-in merchant keyword matcher
    for ((keywords, category) in builtInRules) {
      val hit = keywords.firstOrNull { kw -> searchSpace.contains(kw) }
      if (hit != null) {
        val direction =
          when {
            category == SpendingCategory.INCOME -> TransactionDirection.INCOME.id
            category == SpendingCategory.TRANSFER -> TransactionDirection.TRANSFER.id
            isInflow && category != SpendingCategory.SHOPPING -> TransactionDirection.INCOME.id
            isInflow -> TransactionDirection.INCOME.id
            else -> TransactionDirection.EXPENSE.id
          }
        return CategorizationResult(
          categoryId = category.id,
          direction = direction,
          normalizedMerchant = normalizedMerchant,
          matchedRule = hit,
        )
      }
    }

    // 3. Plaid personal_finance_category mapping
    val plaidMapped = mapPlaidCategory(plaidPrimaryCategory, plaidDetailedCategory)
    if (plaidMapped != null) {
      val direction =
        when {
          plaidMapped == SpendingCategory.INCOME || isInflow -> TransactionDirection.INCOME.id
          plaidMapped == SpendingCategory.TRANSFER -> TransactionDirection.TRANSFER.id
          else -> TransactionDirection.EXPENSE.id
        }
      return CategorizationResult(
        categoryId = plaidMapped.id,
        direction = direction,
        normalizedMerchant = normalizedMerchant,
        matchedRule = plaidPrimaryCategory,
      )
    }

    // 4. CSV category column hint
    if (!csvCategoryHint.isNullOrBlank()) {
      val hintMapped = mapCsvCategoryHint(csvCategoryHint)
      if (hintMapped != null) {
        val direction =
          when {
            hintMapped == SpendingCategory.INCOME || isInflow -> TransactionDirection.INCOME.id
            hintMapped == SpendingCategory.TRANSFER -> TransactionDirection.TRANSFER.id
            else -> TransactionDirection.EXPENSE.id
          }
        return CategorizationResult(
          categoryId = hintMapped.id,
          direction = direction,
          normalizedMerchant = normalizedMerchant,
          matchedRule = csvCategoryHint,
        )
      }
    }

    // 5. Fallback based on inflow/outflow
    return if (isInflow) {
      CategorizationResult(
        categoryId = SpendingCategory.INCOME.id,
        direction = TransactionDirection.INCOME.id,
        normalizedMerchant = normalizedMerchant,
      )
    } else {
      CategorizationResult(
        categoryId = SpendingCategory.OTHER.id,
        direction = TransactionDirection.EXPENSE.id,
        normalizedMerchant = normalizedMerchant,
      )
    }
  }

  private fun mapPlaidCategory(primary: String?, detailed: String?): SpendingCategory? {
    val det = detailed?.uppercase(Locale.US).orEmpty()
    if (det.contains("GROCER")) return SpendingCategory.GROCERIES
    if (det.contains("COFFEE") || det.contains("RESTAURANT") || det.contains("FAST_FOOD")) {
      return SpendingCategory.DINING
    }

    return when (primary?.uppercase(Locale.US)) {
      "FOOD_AND_DRINK" -> SpendingCategory.DINING
      "TRANSPORTATION" -> SpendingCategory.TRANSPORT
      "TRAVEL" -> SpendingCategory.TRAVEL
      "RENT_AND_UTILITIES", "HOME_IMPROVEMENT" -> SpendingCategory.HOUSING
      "GENERAL_MERCHANDISE" -> SpendingCategory.SHOPPING
      "ENTERTAINMENT" -> SpendingCategory.ENTERTAINMENT
      "MEDICAL", "PERSONAL_CARE" -> SpendingCategory.HEALTH
      "INCOME" -> SpendingCategory.INCOME
      "TRANSFER_IN", "TRANSFER_OUT", "LOAN_PAYMENTS" -> SpendingCategory.TRANSFER
      "GENERAL_SERVICES" -> SpendingCategory.SUBSCRIPTIONS
      else -> null
    }
  }

  private fun mapCsvCategoryHint(hint: String): SpendingCategory? {
    val upper = hint.uppercase(Locale.US)
    return when {
      upper.contains("GROCER") -> SpendingCategory.GROCERIES
      upper.contains("FOOD") || upper.contains("DINING") || upper.contains("RESTAURANT") -> SpendingCategory.DINING
      upper.contains("GAS") || upper.contains("AUTO") || upper.contains("TRANSIT") || upper.contains("TRANSPORT") -> SpendingCategory.TRANSPORT
      upper.contains("TRAVEL") || upper.contains("AIR") || upper.contains("HOTEL") -> SpendingCategory.TRAVEL
      upper.contains("UTIL") || upper.contains("RENT") || upper.contains("HOUSING") || upper.contains("BILLS") -> SpendingCategory.HOUSING
      upper.contains("SHOP") || upper.contains("MERCH") || upper.contains("RETAIL") -> SpendingCategory.SHOPPING
      upper.contains("SUBSCRIPT") || upper.contains("STREAM") || upper.contains("SOFTWARE") -> SpendingCategory.SUBSCRIPTIONS
      upper.contains("ENTERTAIN") || upper.contains("MEDIA") -> SpendingCategory.ENTERTAINMENT
      upper.contains("HEALTH") || upper.contains("MED") || upper.contains("PHARM") -> SpendingCategory.HEALTH
      upper.contains("INCOME") || upper.contains("PAYROLL") || upper.contains("SALARY") -> SpendingCategory.INCOME
      upper.contains("TRANSFER") || upper.contains("PAYMENT") -> SpendingCategory.TRANSFER
      else -> null
    }
  }
}
