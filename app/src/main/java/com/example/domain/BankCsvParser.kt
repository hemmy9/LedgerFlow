package com.example.domain

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

data class ParsedCsvTransaction(
  val dateMillis: Long,
  val rawDescription: String,
  val amountCents: Long,
  val isInflow: Boolean,
  val categoryHint: String?,
  val externalId: String,
)

data class CsvParseSummary(
  val transactions: List<ParsedCsvTransaction>,
  val skippedRows: Int,
)

object BankCsvParser {

  private val dateFormatters =
    listOf(
      "yyyy-MM-dd",
      "MM/dd/yyyy",
      "M/d/yyyy",
      "MM-dd-yyyy",
      "yyyy/MM/dd",
      "MM/dd/yy",
      "M/d/yy",
      "dd-MMM-yyyy",
      "MMM dd, yyyy",
    ).map { pattern ->
      SimpleDateFormat(pattern, Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
      }
    }

  fun parse(csvContent: String): CsvParseSummary {
    val lines =
      csvContent
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (lines.size < 2) {
      return CsvParseSummary(emptyList(), 0)
    }

    val delimiter = detectDelimiter(lines.first())
    val headers = parseCsvRow(lines.first(), delimiter).map { it.trim().lowercase(Locale.US) }

    val dateIdx = findColumnIndex(headers, listOf("transaction date", "posting date", "post date", "posted date", "date"))
    val descIdx = findColumnIndex(headers, listOf("description", "original description", "merchant", "payee", "name", "memo", "details"))
    val amountIdx = findColumnIndex(headers, listOf("amount", "transaction amount", "amt"))
    val debitIdx = findColumnIndex(headers, listOf("debit", "withdrawal", "outflow"))
    val creditIdx = findColumnIndex(headers, listOf("credit", "deposit", "inflow"))
    val categoryIdx = findColumnIndex(headers, listOf("category", "type"))

    if (dateIdx == -1 || descIdx == -1 || (amountIdx == -1 && debitIdx == -1 && creditIdx == -1)) {
      return CsvParseSummary(emptyList(), lines.size - 1)
    }

    val parsed = mutableListOf<ParsedCsvTransaction>()
    var skipped = 0

    for (lineIdx in 1 until lines.size) {
      val cols = parseCsvRow(lines[lineIdx], delimiter)
      if (cols.size <= maxOf(dateIdx, descIdx)) {
        skipped++
        continue
      }

      val dateStr = cols.getOrNull(dateIdx)?.trim().orEmpty()
      val rawDesc = cols.getOrNull(descIdx)?.trim().orEmpty()
      if (dateStr.isEmpty() || rawDesc.isEmpty()) {
        skipped++
        continue
      }

      val dateMillis = parseDateMillis(dateStr)
      if (dateMillis == null) {
        skipped++
        continue
      }

      val parsedAmount =
        if (amountIdx != -1) {
          parseSignedCents(cols.getOrNull(amountIdx).orEmpty())
        } else {
          val debitCents = cols.getOrNull(debitIdx)?.let { parseSignedCents(it) }?.let { abs(it) } ?: 0L
          val creditCents = cols.getOrNull(creditIdx)?.let { parseSignedCents(it) }?.let { abs(it) } ?: 0L
          when {
            debitCents > 0L -> -debitCents
            creditCents > 0L -> creditCents
            else -> null
          }
        }

      if (parsedAmount == null || parsedAmount == 0L) {
        skipped++
        continue
      }

      val isInflow = parsedAmount > 0L
      val absCents = abs(parsedAmount)
      val categoryHint = if (categoryIdx != -1) cols.getOrNull(categoryIdx)?.trim()?.takeIf { it.isNotEmpty() } else null
      val extId = "csv_" + sha256Hex("$dateMillis|$rawDesc|$parsedAmount|$lineIdx").take(24)

      parsed +=
        ParsedCsvTransaction(
          dateMillis = dateMillis,
          rawDescription = rawDesc.take(240),
          amountCents = absCents,
          isInflow = isInflow,
          categoryHint = categoryHint,
          externalId = extId,
        )
    }

    return CsvParseSummary(parsed, skipped)
  }

  private fun detectDelimiter(headerLine: String): Char {
    val commaCount = headerLine.count { it == ',' }
    val semiCount = headerLine.count { it == ';' }
    val tabCount = headerLine.count { it == '\t' }
    return when {
      tabCount > commaCount && tabCount > semiCount -> '\t'
      semiCount > commaCount -> ';'
      else -> ','
    }
  }

  private fun findColumnIndex(headers: List<String>, candidates: List<String>): Int {
    for (candidate in candidates) {
      val exact = headers.indexOfFirst { it == candidate }
      if (exact != -1) return exact
    }
    for (candidate in candidates) {
      val partial = headers.indexOfFirst { it.contains(candidate) }
      if (partial != -1) return partial
    }
    return -1
  }

  internal fun parseCsvRow(line: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
      val ch = line[i]
      when {
        ch == '"' -> {
          if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
            current.append('"')
            i++
          } else {
            inQuotes = !inQuotes
          }
        }
        ch == delimiter && !inQuotes -> {
          result.add(current.toString())
          current.clear()
        }
        else -> current.append(ch)
      }
      i++
    }
    result.add(current.toString())
    return result
  }

  private fun parseDateMillis(value: String): Long? {
    val clean = value.trim().removeSurrounding("\"")
    for (formatter in dateFormatters) {
      try {
        val date = synchronized(formatter) { formatter.parse(clean) }
        if (date != null) return date.time
      } catch (_: Exception) {
        // Try next format
      }
    }
    return null
  }

  internal fun parseSignedCents(raw: String): Long? {
    var trimmed = raw.trim().removeSurrounding("\"")
    if (trimmed.isEmpty()) return null
    var negative = false
    if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
      negative = true
      trimmed = trimmed.substring(1, trimmed.length - 1)
    }
    if (trimmed.startsWith("-")) {
      negative = true
      trimmed = trimmed.substring(1)
    } else if (trimmed.startsWith("+")) {
      trimmed = trimmed.substring(1)
    }
    val numeric = trimmed.replace(Regex("[^0-9.]"), "")
    val value = numeric.toDoubleOrNull() ?: return null
    val cents = (value * 100.0).roundToLong()
    return if (negative) -cents else cents
  }

  private fun sha256Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
