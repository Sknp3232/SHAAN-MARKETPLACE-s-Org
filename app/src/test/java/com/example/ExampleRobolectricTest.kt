package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CachedSheetRowEntity
import com.example.data.remote.GoogleWorkspaceAuthManager
import com.example.model.DriveSpreadsheetItem
import com.example.model.RemoteSpreadsheetPreview
import com.example.model.RemoteWorksheetPreview
import com.example.model.SearchFilters
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import com.example.model.Invitation
import com.example.model.SheetConnection
import com.example.model.SheetPermissionLevel
import com.example.ui.components.AccountManagementSection
import com.example.ui.components.DeleteAccountConfirmDialog
import com.example.ui.components.GoogleSheetPickerContent
import com.example.ui.components.LegalImportantLinksSection
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreateInviteDialog
import com.example.ui.screens.DailyEntriesChartCard
import com.example.ui.screens.InviteDispatchDialog
import com.example.ui.screens.LegalDocumentScreen
import com.example.ui.screens.LegalDocumentType
import com.example.ui.screens.TopCategoriesChartCard
import com.example.ui.theme.SmartSheetTheme
import com.example.util.AnalyticsSummary
import com.example.util.AppLinksConfig
import com.example.util.CategoryPerformanceItem
import com.example.util.DailyEntryPoint
import com.example.util.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Smart Sheet", appName)
  }

  @Test
  fun `searchAndFilter matches multi-keyword tokens and highlights matched columns`() {
    val row1 =
      CachedSheetRowEntity(
        id = "row_1",
        workspaceId = "ws_1",
        sheetConnectionId = "sheet_1",
        spreadsheetId = "sp_1",
        spreadsheetName = "Main Ledger",
        worksheetTitle = "Sales",
        rowNumber = 2,
        headersJson = """["Customer Name","Mobile","IMEI 1","Aadhaar","Model"]""",
        cellsJson = """["Rohan Sharma","9876543210","356789012345678","123456789012","Galaxy S24"]""",
        searchableText = "rohan sharma 9876543210 356789012345678 123456789012 galaxy s24",
        customerName = "Rohan Sharma",
        mobileNumber = "9876543210",
        imei1 = "356789012345678",
        imei2 = "",
        aadhaarNumber = "123456789012",
        retailerName = "",
        invoiceNumber = "",
        productModel = "Galaxy S24",
        financeInfo = "",
        recordDate = "2026-11-17",
        syncedAtMillis = 1000L,
        isFromCache = false,
      )

    val results =
      SearchEngine.searchAndFilter(
        rows = listOf(row1),
        query = "  rohan   s24 ",
        filters = SearchFilters(),
        audits = emptyList(),
      )

    assertEquals(1, results.size)
    assertEquals(listOf("Customer Name", "Model"), results.first().matchedColumns)
    assertEquals("XXXX-XXXX-9012", results.first().displayAadhaar(maskByDefault = true, revealFull = false))
  }

  @Test
  fun `oauthScopes use exact drive_file and spreadsheets scopes only`() {
    val scopeUris = GoogleWorkspaceAuthManager.REQUIRED_SCOPES.map { it.scopeUri }
    assertEquals(
      setOf(
        "https://www.googleapis.com/auth/drive.file",
        "https://www.googleapis.com/auth/spreadsheets",
      ),
      scopeUris.toSet(),
    )
    assertEquals(2, scopeUris.size)

    // Must never request Restricted full Drive or broad Drive readonly scopes
    assertFalse(scopeUris.contains("https://www.googleapis.com/auth/drive"))
    assertFalse(scopeUris.contains("https://www.googleapis.com/auth/drive.readonly"))
  }

  @Test
  fun `authManager multiUserIsolation clears previous user token and email on switch`() {
    val manager = GoogleWorkspaceAuthManager()
    manager.setToken("ya29.user_a_token", "userA@gmail.com")
    assertTrue(manager.isAuthorized.value)
    assertEquals("userA@gmail.com", manager.connectedEmail.value)

    // User A disconnects or signs out
    manager.clearToken()
    assertFalse(manager.isAuthorized.value)
    assertNull(manager.accessToken.value)
    assertNull(manager.connectedEmail.value)

    // User B signs in
    manager.setToken("ya29.user_b_token", "userB@gmail.com")
    assertTrue(manager.isAuthorized.value)
    assertEquals("userB@gmail.com", manager.connectedEmail.value)
    assertEquals("ya29.user_b_token", manager.accessToken.value)
  }

  @Test
  fun `oauth scope descriptions match narrowest drive file and sheets scopes`() {
    val keys = GoogleWorkspaceAuthManager.SCOPE_DESCRIPTIONS.map { it.first }
    assertEquals(listOf("drive.file", "spreadsheets"), keys)
    assertFalse(keys.any { it.contains("drive.readonly") })
  }

  @Test
  fun `oauthScopeCheck requires both drive_file and spreadsheets`() {
    assertTrue(
      GoogleWorkspaceAuthManager.hasAllRequiredScopes(
        listOf(
          "https://www.googleapis.com/auth/drive.file",
          "https://www.googleapis.com/auth/spreadsheets",
        )
      )
    )
    assertFalse(
      GoogleWorkspaceAuthManager.hasAllRequiredScopes(
        listOf("https://www.googleapis.com/auth/drive.file")
      )
    )
    assertFalse(
      GoogleWorkspaceAuthManager.hasAllRequiredScopes(
        listOf("https://www.googleapis.com/auth/spreadsheets")
      )
    )
  }

  @Test
  fun `googleSheetPicker shows authorize prompt when unauthenticated`() {
    var authorizeClicks = 0
    composeRule.setContent {
      SmartSheetTheme {
        GoogleSheetPickerContent(
          isGoogleAuthorized = false,
          connectedEmail = null,
          authError = null,
          isLoadingDrive = false,
          driveFiles = emptyList(),
          connectedSpreadsheetIds = emptySet(),
          preview = null,
          onAuthorizeGoogle = { authorizeClicks++ },
          onSearchDrive = {},
          onInspectIdOrUrl = {},
          onClearPreview = {},
          onConnectConfirmed = { _, _, _ -> },
          onDismiss = {},
        )
      }
    }

    composeRule.onNodeWithText("Authorize Google Sheets").assertIsDisplayed()
    composeRule.onNodeWithTag("picker_authorize_google_button").performClick()
    assertEquals(1, authorizeClicks)
  }

  @Test
  fun `googleSheetPicker filters and selects authenticated spreadsheet`() {
    var inspectedId: String? = null
    val files =
      listOf(
        DriveSpreadsheetItem("sp_1", "Sales Ledger", "2026-11-20T04:00:00Z", ""),
        DriveSpreadsheetItem("sp_2", "Stock Audit", "2026-11-18T10:00:00Z", ""),
      )
    composeRule.setContent {
      SmartSheetTheme {
        GoogleSheetPickerContent(
          isGoogleAuthorized = true,
          connectedEmail = "tester@gmail.com",
          authError = null,
          isLoadingDrive = false,
          driveFiles = files,
          connectedSpreadsheetIds = setOf("sp_1"),
          preview = null,
          onAuthorizeGoogle = {},
          onSearchDrive = {},
          onInspectIdOrUrl = { inspectedId = it },
          onClearPreview = {},
          onConnectConfirmed = { _, _, _ -> },
          onDismiss = {},
        )
      }
    }

    composeRule.onNodeWithText("Pick a Google Sheet").assertIsDisplayed()
    composeRule.onNodeWithText("Google Account: tester@gmail.com").assertIsDisplayed()
    composeRule.onNodeWithTag("picker_search_input").performTextInput("Stock")
    composeRule.onNodeWithTag("drive_sheet_item_sp_2").assertIsDisplayed().performClick()
    assertEquals("sp_2", inspectedId)
  }

  @Test
  fun `googleSheetPicker selects worksheets and confirms connection`() {
    var connectedTabs: List<String> = emptyList()
    val preview =
      RemoteSpreadsheetPreview(
        spreadsheetId = "sp_2",
        title = "Stock Audit",
        spreadsheetUrl = "",
        worksheets =
          listOf(
            RemoteWorksheetPreview(0L, "Records", 120, 8),
            RemoteWorksheetPreview(1L, "Returns", 15, 6),
          ),
      )

    composeRule.setContent {
      SmartSheetTheme {
        GoogleSheetPickerContent(
          isGoogleAuthorized = true,
          connectedEmail = "tester@gmail.com",
          authError = null,
          isLoadingDrive = false,
          driveFiles = emptyList(),
          connectedSpreadsheetIds = emptySet(),
          preview = preview,
          onAuthorizeGoogle = {},
          onSearchDrive = {},
          onInspectIdOrUrl = {},
          onClearPreview = {},
          onConnectConfirmed = { _, _, tabs -> connectedTabs = tabs },
          onDismiss = {},
        )
      }
    }

    composeRule.onNodeWithTag("picker_worksheet_item_Returns").performScrollTo().assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("picker_connect_confirm_button").assertIsDisplayed().performClick()
    assertEquals(listOf("Records", "Returns"), connectedTabs)
  }

  @Test
  fun `legalImportantLinks renders all three options and fires view share copy actions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val links = AppLinksConfig.resolve(context)
    var viewed: LegalDocumentType? = null
    var sharedUrl: String? = null
    var copiedUrl: String? = null

    composeRule.setContent {
      SmartSheetTheme {
        LegalImportantLinksSection(
          links = links,
          onViewDocument = { viewed = it },
          onShareLink = { _, url -> sharedUrl = url },
          onCopyLink = { _, url -> copiedUrl = url },
        )
      }
    }

    composeRule.onNodeWithText("LEGAL & IMPORTANT LINKS").assertIsDisplayed()
    composeRule.onNodeWithText("Privacy Policy").assertIsDisplayed()
    composeRule.onNodeWithText("Terms of Service").assertIsDisplayed()
    composeRule.onNodeWithText("App Home Page").assertIsDisplayed()

    composeRule.onNodeWithTag("legal_privacy_view_button").performClick()
    assertEquals(LegalDocumentType.PRIVACY_POLICY, viewed)

    composeRule.onNodeWithTag("legal_terms_share_button").performClick()
    assertEquals("https://gen-lang-client-0057690374.firebaseapp.com/terms-of-service", sharedUrl)

    composeRule.onNodeWithTag("legal_home_copy_button").performClick()
    assertEquals("https://gen-lang-client-0057690374.firebaseapp.com/", copiedUrl)
  }

  @Test
  fun `copyLinkToClipboard writes exact link and LegalDocumentScreen copies cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ok =
      AppLinksConfig.copyLinkToClipboard(
        context,
        "Privacy Policy",
        "https://gen-lang-client-0057690374.firebaseapp.com/privacy-policy",
      )
    assertTrue(ok)
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    assertEquals(
      "https://gen-lang-client-0057690374.firebaseapp.com/privacy-policy",
      cm.primaryClip?.getItemAt(0)?.text?.toString(),
    )

    composeRule.setContent {
      SmartSheetTheme {
        LegalDocumentScreen(
          documentType = LegalDocumentType.PRIVACY_POLICY,
          onBack = {},
        )
      }
    }

    composeRule.onAllNodesWithText("Smart Sheet Privacy Policy")[0].assertIsDisplayed()
    composeRule.onNodeWithText("1. Information Collected").assertIsDisplayed()
    composeRule.onNodeWithTag("legal_viewer_copy_button").performClick()
    composeRule.onNodeWithText("Link copied successfully").assertIsDisplayed()
  }

  @Test
  fun `authScreen allows opening legal pages and home page without login`() {
    var openedDoc: LegalDocumentType? = null
    composeRule.setContent {
      SmartSheetTheme {
        AuthScreen(
          inviteCodeInput = "",
          onInviteCodeChange = {},
          isSigningIn = false,
          authError = null,
          isBlockedState = false,
          onSignInClicked = {},
          onRetryClicked = {},
          onSignOutClicked = {},
          onOpenLegalDocument = { openedDoc = it },
        )
      }
    }

    composeRule.onNodeWithTag("auth_privacy_policy_link").performScrollTo().performClick()
    assertEquals(LegalDocumentType.PRIVACY_POLICY, openedDoc)

    composeRule.onNodeWithTag("auth_terms_link").performScrollTo().performClick()
    assertEquals(LegalDocumentType.TERMS_OF_SERVICE, openedDoc)

    composeRule.onNodeWithTag("auth_home_page_link").performScrollTo().performClick()
    assertEquals(LegalDocumentType.HOME_PAGE, openedDoc)
  }

  @Test
  fun `accountManagement triggers signOut and confirmed account deletion`() {
    var deleteRequestedFlag = false
    var deleteConfirmed = false

    composeRule.setContent {
      var showDialog by remember { mutableStateOf(false) }
      SmartSheetTheme {
        AccountManagementSection(
          isDeletingAccount = false,
          accountDeletionUrl = "https://gen-lang-client-0057690374.firebaseapp.com/account-deletion",
          onSignOut = {},
          onRequestDeleteAccount = {
            deleteRequestedFlag = true
            showDialog = true
          },
          onOpenDeletionGuide = {},
        )
        if (showDialog) {
          DeleteAccountConfirmDialog(
            isAdmin = false,
            onConfirmDelete = {
              deleteConfirmed = true
              showDialog = false
            },
            onDismiss = { showDialog = false },
          )
        }
      }
    }

    composeRule.onNodeWithTag("profile_delete_account_button").performClick()
    assertTrue(deleteRequestedFlag)
    composeRule.onNodeWithTag("delete_account_confirm_button").assertIsDisplayed().performClick()
    assertTrue(deleteConfirmed)
  }

  @Test
  fun `createInviteDialog requires valid email and normalizes before submitting perSheetPermissions`() {
    var submittedEmail: String? = null
    var submittedSheets: List<String> = emptyList()
    var submittedPerms: Map<String, String> = emptyMap()

    val sheets =
      listOf(
        SheetConnection(id = "s1", customName = "Main Ledger", active = true),
        SheetConnection(id = "s2", customName = "Returns", active = true),
      )

    composeRule.setContent {
      SmartSheetTheme {
        CreateInviteDialog(
          sheets = sheets,
          onCreateInvite = { email, _, sheetIds, perms, _, _ ->
            submittedEmail = email
            submittedSheets = sheetIds
            submittedPerms = perms
          },
          onDismiss = {},
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("send_invitation_button").assertIsNotEnabled()

    composeRule.onNodeWithTag("invite_email_input").performTextInput("invalid-email")
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("send_invitation_button").assertIsNotEnabled()

    composeRule.onNodeWithTag("invite_email_input").performTextInput("@company.com")
    composeRule.waitForIdle()
    // Now "invalid-email@company.com" or let's check enabled
    composeRule.onNodeWithTag("send_invitation_button").assertIsEnabled().performClick()
    composeRule.waitForIdle()

    assertEquals("invalid-email@company.com", submittedEmail)
    assertEquals(listOf("s1", "s2"), submittedSheets)
    assertEquals(
      mapOf(
        "s1" to SheetPermissionLevel.SEARCH_ADD.name,
        "s2" to SheetPermissionLevel.SEARCH_ADD.name,
      ),
      submittedPerms,
    )
  }

  @Test
  fun `analyticsCharts display daily record entry counts and switch top categories`() {
    val summary =
      AnalyticsSummary(
        dailyEntries =
          listOf(
            DailyEntryPoint("2026-11-16", "16 Nov", 3),
            DailyEntryPoint("2026-11-17", "17 Nov", 7),
          ),
        totalEntriesInWindow = 10,
        peakDayLabel = "17 Nov",
        peakDayCount = 7,
        todayCount = 7,
        topModels = listOf(CategoryPerformanceItem("Galaxy S24", 6, 60)),
        topRetailers = listOf(CategoryPerformanceItem("Metro Hub", 8, 80)),
        topSheets = listOf(CategoryPerformanceItem("Main Ledger", 10, 100)),
      )
    var selectedWindow = 7

    composeRule.setContent {
      SmartSheetTheme {
        androidx.compose.foundation.layout.Column(
          modifier =
            androidx.compose.ui.Modifier.verticalScroll(
              androidx.compose.foundation.rememberScrollState()
            )
        ) {
          DailyEntriesChartCard(
            summary = summary,
            windowDays = 7,
            onWindowDaysChange = { selectedWindow = it },
          )
          TopCategoriesChartCard(summary = summary)
        }
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("analytics_daily_entries_chart").assertIsDisplayed()
    composeRule.onNodeWithText("Daily Record Entry Counts").assertIsDisplayed()
    composeRule.onNodeWithText("Peak: 17 Nov (7)").assertIsDisplayed()

    composeRule.onNodeWithTag("analytics_window_14d").performClick()
    assertEquals(14, selectedWindow)

    composeRule.onNodeWithTag("analytics_top_categories_chart").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("1. Galaxy S24").performScrollTo().assertIsDisplayed()

    composeRule.onNodeWithTag("analytics_cat_retailers").performScrollTo().performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithText("1. Metro Hub").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `inviteDispatchDialog renders HTTPS link and triggers email copy share actions`() {
    val inv =
      Invitation(
        inviteCode = "SM-ABCD1234",
        targetEmail = "staff@gmail.com",
        assignedSheetIds = listOf("s1", "s2"),
      )
    var sentEmail = false
    var copied = false
    var shared = false

    composeRule.setContent {
      SmartSheetTheme {
        InviteDispatchDialog(
          invitation = inv,
          onSendEmail = { sentEmail = true },
          onShareLink = { shared = true },
          onCopyLink = { copied = true },
          onDismiss = {},
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithText("Invitation Ready").assertIsDisplayed()
    composeRule.onNodeWithText("https://gen-lang-client-0057690374.firebaseapp.com/invite?code=SM-ABCD1234").assertIsDisplayed()

    composeRule.onNodeWithTag("dispatch_send_email_button").performClick()
    composeRule.onNodeWithTag("dispatch_copy_button").performClick()
    composeRule.onNodeWithTag("dispatch_share_button").performClick()

    assertTrue(sentEmail)
    assertTrue(copied)
    assertTrue(shared)

    val parsedHttps =
      AppLinksConfig.extractInviteCodeFromUri(
        android.net.Uri.parse("https://gen-lang-client-0057690374.firebaseapp.com/invite?code=sm-abcd1234")
      )
    val parsedApp =
      AppLinksConfig.extractInviteCodeFromUri(
        android.net.Uri.parse("smartsheet://invite?code=SM-XYZ999")
      )
    assertEquals("SM-ABCD1234", parsedHttps)
    assertEquals("SM-XYZ999", parsedApp)
  }
}
