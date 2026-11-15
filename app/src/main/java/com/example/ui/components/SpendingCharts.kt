package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SpendingCategory
import com.example.data.model.SpendingTransaction
import com.example.data.model.TransactionDirection
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeEmerald
import com.example.ui.theme.MonoFigureMedium
import com.example.ui.theme.accentColor
import com.example.ui.util.FinanceFormatters
import com.example.ui.util.icon
import com.example.ui.viewmodel.CategorySpendSummary

@Composable
fun SpendingDonutCard(
  breakdown: List<CategorySpendSummary>,
  totalSpentCents: Long,
  onCategoryClick: (SpendingCategory) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Text(
        text = "Spending by Category",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(16.dp))

      if (breakdown.isEmpty() || totalSpentCents <= 0L) {
        Text(
          text = "No expenses recorded for this month yet.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          SpendingDonutRing(
            breakdown = breakdown,
            totalSpentCents = totalSpentCents,
            modifier = Modifier.size(144.dp),
          )

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            breakdown.take(5).forEach { summary ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCategoryClick(summary.category) }
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.size(10.dp)
                      .clip(CircleShape)
                      .background(summary.category.accentColor())
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = summary.category.displayName,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "${(summary.shareFraction * 100).toInt()}%",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SpendingDonutRing(
  breakdown: List<CategorySpendSummary>,
  totalSpentCents: Long,
  modifier: Modifier = Modifier,
) {
  val animatedSweepMultiplier by
    animateFloatAsState(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 450),
      label = "donutSweep",
    )
  val trackColor = MaterialTheme.colorScheme.surfaceVariant

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokeWidth = 18.dp.toPx()
      val inset = strokeWidth / 2f
      val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
      val topLeft = Offset(inset, inset)

      drawArc(
        color = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth),
      )

      var startAngle = -90f
      val gapDegrees = if (breakdown.size > 1) 3f else 0f
      breakdown.forEach { item ->
        val rawSweep = (item.shareFraction * 360f) * animatedSweepMultiplier
        val sweep = (rawSweep - gapDegrees).coerceAtLeast(1.5f)
        drawArc(
          color = item.category.accentColor(),
          startAngle = startAngle,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
        )
        startAngle += rawSweep
      }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "Spent",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = FinanceFormatters.formatCents(totalSpentCents),
        style = MonoFigureMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun TransactionRowItem(
  transaction: SpendingTransaction,
  accountLabel: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val category = SpendingCategory.fromId(transaction.categoryId)
  val direction = TransactionDirection.fromId(transaction.direction)
  val catColor = category.accentColor()

  val amountColor =
    when (direction) {
      TransactionDirection.INCOME -> IncomeEmerald
      TransactionDirection.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
      TransactionDirection.EXPENSE -> MaterialTheme.colorScheme.onSurface
    }
  val formattedAmount =
    when (direction) {
      TransactionDirection.INCOME -> FinanceFormatters.formatCents(transaction.amountCents, showPlus = true)
      TransactionDirection.EXPENSE -> "-${FinanceFormatters.formatCents(transaction.amountCents)}"
      TransactionDirection.TRANSFER -> FinanceFormatters.formatCents(transaction.amountCents)
    }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick),
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(16.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color = catColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(44.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = category.icon(),
            contentDescription = category.displayName,
            tint = catColor,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = transaction.merchantName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (!transaction.isReviewed) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              color = MaterialTheme.colorScheme.tertiaryContainer,
              shape = RoundedCornerShape(6.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Rounded.AutoAwesome,
                  contentDescription = "Auto-categorized",
                  tint = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.size(10.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = "Review",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(2.dp))
        val subtitle =
          buildString {
            append(category.displayName)
            if (!accountLabel.isNullOrBlank()) {
              append(" • ")
              append(accountLabel)
            }
            append(" • ")
            append(FinanceFormatters.formatShortDate(transaction.dateMillis))
          }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Text(
        text = formattedAmount,
        style = MonoFigureMedium,
        color = if (direction == TransactionDirection.EXPENSE) ExpenseCoral.copy(alpha = 0.88f) else amountColor,
      )
    }
  }
}

@Composable
fun EmptyFinanceStateCard(
  title: String,
  message: String,
  primaryActionLabel: String,
  onPrimaryAction: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_empty_ledger),
        contentDescription = stringResource(R.string.cd_empty_ledger),
        modifier =
          Modifier.fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Crop,
      )
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(
        onClick = onPrimaryAction,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Text(text = primaryActionLabel)
      }
    }
  }
}
