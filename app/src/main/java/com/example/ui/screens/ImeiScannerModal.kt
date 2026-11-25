package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.CachedSheetRowEntity
import com.example.ui.theme.LocalSmartSheetColors
import com.example.util.SearchEngine
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun ImeiScannerModal(
  title: String,
  initialValue: String = "",
  checkDuplicate: suspend (String) -> List<CachedSheetRowEntity>,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val extended = LocalSmartSheetColors.current

  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var cameraStarted by remember { mutableStateOf(hasPermission) }
  var imeiInput by remember { mutableStateOf(initialValue) }
  var detectedHistory by remember { mutableStateOf<List<String>>(emptyList()) }
  var duplicates by remember { mutableStateOf<List<CachedSheetRowEntity>>(emptyList()) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
      cameraStarted = granted
    }

  LaunchedEffect(imeiInput) {
    val clean = imeiInput.filter { it.isDigit() }
    duplicates =
      if (clean.length == 15) {
        checkDuplicate(clean)
      } else {
        emptyList()
      }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize().padding(12.dp),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
              text = "Scan 15-digit IMEI barcode or enter manually",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("close_scanner_button"),
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close scanner")
          }
        }

        // Camera Preview Area — activated only after explicit user action & permission
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(240.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFF0F172A)),
          contentAlignment = Alignment.Center,
        ) {
          if (hasPermission && cameraStarted) {
            val cameraController = remember {
              LifecycleCameraController(context).apply {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
              }
            }
            val barcodeScanner = remember {
              val options =
                BarcodeScannerOptions.Builder()
                  .setBarcodeFormats(
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_DATA_MATRIX,
                  )
                  .build()
              BarcodeScanning.getClient(options)
            }

            DisposableEffect(lifecycleOwner) {
              val executor = ContextCompat.getMainExecutor(context)
              cameraController.setImageAnalysisAnalyzer(
                executor,
                MlKitAnalyzer(
                  listOf(barcodeScanner),
                  COORDINATE_SYSTEM_VIEW_REFERENCED,
                  executor,
                ) { result ->
                  val barcodes = result?.getValue(barcodeScanner).orEmpty()
                  for (barcode in barcodes) {
                    val raw = barcode.rawValue?.trim().orEmpty()
                    val digits = raw.filter { it.isDigit() }
                    val candidate =
                      when {
                        digits.length == 15 -> digits
                        raw.length in 10..20 -> raw
                        else -> ""
                      }
                    if (candidate.isNotBlank()) {
                      if (candidate !in detectedHistory) {
                        detectedHistory = (listOf(candidate) + detectedHistory).take(4)
                      }
                      if (SearchEngine.isValidImei15(candidate)) {
                        imeiInput = candidate
                      } else if (imeiInput.isBlank()) {
                        imeiInput = candidate
                      }
                    }
                  }
                },
              )
              cameraController.bindToLifecycle(lifecycleOwner)
              onDispose {
                cameraController.clearImageAnalysisAnalyzer()
                barcodeScanner.close()
              }
            }

            AndroidView(
              factory = { ctx ->
                PreviewView(ctx).apply { controller = cameraController }
              },
              modifier = Modifier.fillMaxSize(),
            )
            Surface(
              modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
              color = Color.Black.copy(alpha = 0.65f),
              shape = RoundedCornerShape(50),
            ) {
              Text(
                text = "Align IMEI barcode inside frame",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.padding(16.dp),
            ) {
              Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(40.dp),
              )
              Text(
                text =
                  if (hasPermission) "Tap below to start camera scanner"
                  else "Camera permission is required to scan IMEI barcodes",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
              )
              Button(
                onClick = {
                  if (hasPermission) {
                    cameraStarted = true
                  } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                  }
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("start_camera_scan_button"),
              ) {
                Text(if (hasPermission) "Start Camera" else "Grant Camera & Scan")
              }
            }
          }
        }

        if (detectedHistory.isNotEmpty()) {
          Text(
            text = "Recent Scanned Barcodes",
            style = MaterialTheme.typography.labelLarge,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            detectedHistory.take(2).forEach { code ->
              OutlinedButton(
                onClick = { imeiInput = code },
                modifier = Modifier.weight(1f),
              ) {
                Text(
                  text = code,
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 1,
                )
              }
            }
          }
        }

        val cleanDigits = imeiInput.filter { it.isDigit() }
        val isValid15 = SearchEngine.isValidImei15(cleanDigits)

        OutlinedTextField(
          value = imeiInput,
          onValueChange = { imeiInput = it.filter { ch -> ch.isDigit() }.take(15) },
          label = { Text("15-Digit IMEI Number") },
          placeholder = { Text("Enter or scan 15 digits") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
          supportingText = {
            Text(
              text =
                when {
                  cleanDigits.isEmpty() -> "0 / 15 digits"
                  isValid15 -> "Valid 15-digit IMEI format"
                  else -> "${cleanDigits.length} / 15 digits (must be 15 digits)"
                },
              color =
                if (isValid15) extended.healthy else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          modifier = Modifier.fillMaxWidth().testTag("manual_imei_input"),
        )

        if (duplicates.isNotEmpty()) {
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = extended.warning.copy(alpha = 0.12f)
              ),
            border = BorderStroke(1.dp, extended.warning),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Duplicate IMEI warning",
                tint = extended.warning,
              )
              Column {
                Text(
                  text = "Duplicate IMEI Detected (${duplicates.size} match)",
                  style = MaterialTheme.typography.titleSmall,
                  color = extended.warning,
                )
                val first = duplicates.first()
                Text(
                  text =
                    "Found in ${first.spreadsheetName} / ${first.worksheetTitle} (Row #${first.rowNumber}) • ${first.customerName}",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
          ) {
            Text("Cancel")
          }
          Button(
            onClick = { onConfirm(cleanDigits.ifBlank { imeiInput.trim() }) },
            enabled = cleanDigits.isNotEmpty(),
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("confirm_imei_button"),
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Use IMEI")
          }
        }
      }
    }
  }
}
