package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalSmartSheetColors

@Composable
fun SearchBarHero(
  query: String,
  onQueryChange: (String) -> Unit,
  onSearchSubmit: () -> Unit,
  onScanBarcodeClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  placeholder: String = "Search Name, Mobile, IMEI, Aadhaar, Invoice or Any Record...",
) {
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier.fillMaxWidth().testTag("global_search_input"),
    placeholder = {
      Text(
        text = placeholder,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    leadingIcon = {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = "Search records",
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    trailingIcon = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (query.isNotEmpty()) {
          IconButton(
            onClick = { onQueryChange("") },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_search_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Clear search",
            )
          }
        }
        if (onScanBarcodeClick != null) {
          IconButton(
            onClick = onScanBarcodeClick,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("search_scan_imei_button"),
          ) {
            Icon(
              imageVector = Icons.Default.QrCodeScanner,
              contentDescription = "Scan IMEI barcode to search",
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }
    },
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
      ),
  )
}

@Composable
fun KpiMetricCard(
  title: String,
  value: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 112.dp)
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .testTag(testTag),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(accentColor.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(20.dp),
          )
        }
        Text(
          text = "View →",
          style = MaterialTheme.typography.labelMedium,
          color = accentColor,
        )
      }
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun StatusPill(
  label: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = containerColor,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = contentColor,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun HighlightedText(
  text: String,
  query: String,
  style: TextStyle,
  color: Color = MaterialTheme.colorScheme.onSurface,
  maxLines: Int = 2,
) {
  val extended = LocalSmartSheetColors.current
  val tokens =
    query
      .trim()
      .lowercase()
      .split(Regex("\\s+"))
      .filter { it.isNotBlank() }

  if (tokens.isEmpty() || text.isBlank()) {
    Text(
      text = text,
      style = style,
      color = color,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
    )
    return
  }

  val lower = text.lowercase()
  val ranges = mutableListOf<IntRange>()
  for (token in tokens) {
    var start = 0
    while (start < lower.length) {
      val idx = lower.indexOf(token, start)
      if (idx < 0) break
      ranges.add(idx until (idx + token.length))
      start = idx + token.length
    }
  }
  val merged =
    ranges.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
      if (acc.isEmpty() || r.first > acc.last().last + 1) {
        acc.add(r)
      } else {
        val last = acc.removeAt(acc.lastIndex)
        acc.add(last.first..maxOf(last.last, r.last))
      }
      acc
    }

  val annotated = buildAnnotatedString {
    var cursor = 0
    for (r in merged) {
      if (r.first > cursor) {
        append(text.substring(cursor, r.first))
      }
      withStyle(
        SpanStyle(
          background = extended.matchHighlight,
          fontWeight = FontWeight.Bold,
        )
      ) {
        append(text.substring(r.first, r.last + 1))
      }
      cursor = r.last + 1
    }
    if (cursor < text.length) {
      append(text.substring(cursor))
    }
  }

  Text(
    text = annotated,
    style = style,
    color = color,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
fun SyncProgressStrip(
  isSyncing: Boolean,
  label: String?,
  lastSyncLabel: String,
  onSyncClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = LocalSmartSheetColors.current
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier =
              Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isSyncing) extended.info else extended.healthy)
          )
          Text(
            text = if (isSyncing) (label ?: "Synchronizing...") else "Last sync: $lastSyncLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
          modifier =
            Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable(enabled = !isSyncing, onClick = onSyncClick)
              .testTag("manual_sync_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Sync,
              contentDescription = "Refresh sync",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = if (isSyncing) "Syncing" else "Refresh",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
      if (isSyncing) {
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
      }
    }
  }
}

@Composable
fun EmptyStateCard(
  title: String,
  message: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  showIllustration: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (showIllustration) {
        Image(
          painter = painterResource(id = R.drawable.img_empty_sheets),
          contentDescription = title,
          modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
          contentScale = ContentScale.Crop,
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.Button(
          onClick = onAction,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}
