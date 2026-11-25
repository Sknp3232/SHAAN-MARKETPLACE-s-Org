package com.example

import android.app.Activity
import com.example.data.local.CachedSheetRowEntity
import com.example.data.remote.GoogleDriveSheetsClient
import com.example.data.remote.GoogleOAuthErrorMapper
import com.example.model.Invitation
import com.example.model.SheetConnection
import com.example.model.SheetPermissionLevel
import com.example.model.SheetRecord
import com.example.model.UserRole
import com.example.util.AnalyticsCategoryDimension
import com.example.util.AnalyticsEngine
import com.example.util.InvitationValidator
import com.example.util.SearchEngine
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun imei15Validation_checksExactLengthAndDigits() {
    assertTrue(SearchEngine.isValidImei15("356789012345678"))
    assertFalse(SearchEngine.isValidImei15("35678901234567"))
    assertFalse(SearchEngine.isValidImei15("3567890123456789"))
    assertFalse(SearchEngine.isValidImei15("35678901234567A"))
  }

  @Test
  fun aadhaarMasking_masksLastFourDigitsUnlessRevealed() {
    val record =
      SheetRecord(
        id = "r1",
        workspaceId = "ws1",
        sheetConnectionId = "s1",
        spreadsheetId = "sp1",
        spreadsheetName = "Ledger",
        worksheetTitle = "Sales",
        rowNumber = 2,
        headers = listOf("Customer", "Aadhaar"),
        cells = listOf("Rohan", "123456789012"),
        customerName = "Rohan",
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
    assertEquals("XXXX-XXXX-9012", record.displayAadhaar(maskByDefault = true, revealFull = false))
    assertEquals("123456789012", record.displayAadhaar(maskByDefault = true, revealFull = true))
  }

  @Test
  fun oauthErrorMapper_coversAllRequiredFailureCategories() {
    assertTrue(
      GoogleOAuthErrorMapper.mapStatusOrMessage(
        rawMessage = "Access Blocked: This app has not completed the Google verification process"
      ).contains("awaiting Google OAuth verification")
    )
    assertTrue(
      GoogleOAuthErrorMapper.mapStatusOrMessage(rawMessage = "unverified app org_internal")
        .contains("OAuth Access Blocked")
    )
    assertTrue(
      GoogleOAuthErrorMapper.mapStatusOrMessage(statusCode = CommonStatusCodes.DEVELOPER_ERROR)
        .contains("Invalid Client / SHA-1")
    )
    assertTrue(
      GoogleOAuthErrorMapper.mapStatusOrMessage(rawMessage = "redirect_uri_mismatch")
        .contains("Redirect URI Mismatch")
    )
    assertTrue(
      GoogleOAuthErrorMapper.mapStatusOrMessage(
        statusCode = CommonStatusCodes.CANCELED,
        rawMessage = "access_denied",
      ).contains("Access Denied")
    )
    assertTrue(
      GoogleOAuthErrorMapper.mapStatusOrMessage(resultCode = Activity.RESULT_CANCELED)
        .contains("cancelled or blocked")
    )
    assertTrue(
      GoogleOAuthErrorMapper.mapStatusOrMessage(rawMessage = "invalid_grant: token has been expired or revoked")
        .contains("expired or was revoked")
    )
    assertTrue(
      GoogleOAuthErrorMapper.mapStatusOrMessage(rawMessage = "accessNotConfigured insufficient permissions")
        .contains("Missing Google API Permissions")
    )
    assertTrue(
      GoogleOAuthErrorMapper.mapStatusOrMessage(rawMessage = "rateLimitExceeded quota")
        .contains("quota exceeded")
    )
  }

  @Test
  fun oauthErrorMapper_redactsTokensInLogsAndErrors() {
    val leaked = "Request failed with Bearer ya29.a0AfH6SMBxYz123_tokenValue and ya29.secret_99"
    val sanitized = GoogleOAuthErrorMapper.sanitizeErrorMessage(leaked)
    assertFalse(sanitized.contains("ya29."))
    assertFalse(sanitized.contains("a0AfH6SMBxYz123_tokenValue"))
    assertTrue(sanitized.contains("[REDACTED"))
  }

  @Test
  fun sheetsClient_helpersExtractIdsParseRangesAndPaginateLargeDatasets() {
    assertEquals(
      "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms",
      GoogleDriveSheetsClient.extractSpreadsheetId(
        "https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit#gid=0"
      ),
    )
    assertEquals(
      "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms",
      GoogleDriveSheetsClient.extractSpreadsheetId("  1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms  "),
    )

    assertEquals(42, GoogleDriveSheetsClient.parseRowNumberFromA1Range("'Sales Ledger'!A42:H42"))
    assertEquals("'Sales Ledger'!A1:ZZ2500", GoogleDriveSheetsClient.calculateRowChunkRange("Sales Ledger", 0))
    assertEquals("'Sales Ledger'!A2501:ZZ5000", GoogleDriveSheetsClient.calculateRowChunkRange("Sales Ledger", 1))
  }

  @Test
  fun sheetsClient_deleteRowProtectsHeaderRow() = runTest {
    val client = GoogleDriveSheetsClient()
    val result = client.deleteRow(accessToken = "dummy", spreadsheetId = "sp1", sheetGid = 0L, rowNumber = 1)
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("Header row #1") == true)
  }

  @Test
  fun sheetPicker_filtersByQueryAndLinkedStatus() {
    val files =
      listOf(
        com.example.model.DriveSpreadsheetItem("id_1", "Sales Ledger 2026", "2026-11-20T04:00:00Z", ""),
        com.example.model.DriveSpreadsheetItem("id_2", "Inventory Master", "2026-11-19T12:00:00Z", ""),
        com.example.model.DriveSpreadsheetItem("id_3", "Warranty Claims", "", ""),
      )
    val connected = setOf("id_1")

    val onlyUnlinked =
      com.example.ui.components.filterDriveSpreadsheets(
        files = files,
        query = "",
        onlyUnlinked = true,
        connectedSpreadsheetIds = connected,
      )
    assertEquals(listOf("id_2", "id_3"), onlyUnlinked.map { it.id })

    val queryFiltered =
      com.example.ui.components.filterDriveSpreadsheets(
        files = files,
        query = "inventory",
        onlyUnlinked = false,
        connectedSpreadsheetIds = connected,
      )
    assertEquals(listOf("id_2"), queryFiltered.map { it.id })

    assertEquals(
      "Recently updated",
      com.example.ui.components.formatDriveModifiedTime(""),
    )
    assertTrue(
      com.example.ui.components.formatDriveModifiedTime("2026-11-20T04:00:00Z")
        .startsWith("Modified ")
    )
  }

  @Test
  fun appLinksConfig_buildsCanonicalPublicRoutesWithoutPlayInternalUrl() {
    val links =
      com.example.util.AppLinksConfig.buildPublicLinks(
        "https://gen-lang-client-0057690374.firebaseapp.com/"
      )
    assertEquals("https://gen-lang-client-0057690374.firebaseapp.com/", links.homePageUrl)
    assertEquals(
      "https://gen-lang-client-0057690374.firebaseapp.com/privacy-policy",
      links.privacyPolicyUrl,
    )
    assertEquals(
      "https://gen-lang-client-0057690374.firebaseapp.com/terms-of-service",
      links.termsOfServiceUrl,
    )
    assertEquals(
      "https://gen-lang-client-0057690374.firebaseapp.com/account-deletion",
      links.accountDeletionUrl,
    )
    assertFalse(links.homePageUrl.contains("play.google.com/apps/internaltest"))

    assertEquals(
      com.example.ui.screens.LegalDocumentType.PRIVACY_POLICY,
      com.example.ui.screens.LegalDocumentType.fromKey("privacy"),
    )
    assertEquals(
      com.example.ui.screens.LegalDocumentType.TERMS_OF_SERVICE,
      com.example.ui.screens.LegalDocumentType.fromKey("terms"),
    )
    assertEquals(
      com.example.ui.screens.LegalDocumentType.HOME_PAGE,
      com.example.ui.screens.LegalDocumentType.fromKey("home"),
    )
  }

  @Test
  fun invitationValidator_normalizesAndRejectsBlankOrInvalidEmails() {
    assertEquals("employee@company.com", InvitationValidator.normalizeEmail("  Employee@Company.COM \n"))
    assertTrue(InvitationValidator.isValidEmail("  worker.1+ops@sub.domain.co "))
    assertFalse(InvitationValidator.isValidEmail(""))
    assertFalse(InvitationValidator.isValidEmail("   "))
    assertFalse(InvitationValidator.isValidEmail("not-an-email"))
    assertFalse(InvitationValidator.isValidEmail("user@domain"))
    assertEquals(
      "Employee email address is required.",
      InvitationValidator.validateEmailOrError("   "),
    )
    assertEquals(null, InvitationValidator.validateEmailOrError("Staff@Store.com"))
  }

  @Test
  fun invitationModel_resolvesEmployeeRoleAndPerSheetPermissions() {
    val inv =
      Invitation(
        inviteCode = "INV12345",
        targetEmail = "  Emp1@Example.COM ",
        role = "EMPLOYEE",
        defaultPermission = SheetPermissionLevel.SEARCH_ONLY.name,
        assignedSheetIds = listOf("s1", "s2"),
        sheetPermissions = mapOf("s2" to SheetPermissionLevel.SEARCH_EDIT.name),
      )
    assertEquals("emp1@example.com", inv.normalizedTargetEmail())
    assertEquals(UserRole.EMPLOYEE, inv.resolvedRole())
    assertEquals(SheetPermissionLevel.SEARCH_ONLY, inv.permissionForSheet("s1"))
    assertEquals(SheetPermissionLevel.SEARCH_EDIT, inv.permissionForSheet("s2"))
  }

  @Test
  fun analyticsEngine_computesDailyRecordEntryCountsAndTopCategories() {
    val fixedNow = 1794873600000L // 2026-11-17T00:00:00Z
    val rows =
      listOf(
        CachedSheetRowEntity(
          id = "r1",
          workspaceId = "ws1",
          sheetConnectionId = "s1",
          spreadsheetId = "sp1",
          spreadsheetName = "Sales Ledger",
          worksheetTitle = "Nov",
          rowNumber = 2,
          headersJson = """["Customer","Model","Retailer","Date"]""",
          cellsJson = """["Amit","Galaxy S24","Metro Hub","2026-11-17"]""",
          searchableText = "amit galaxy s24 metro hub",
          customerName = "Amit",
          mobileNumber = "9999999991",
          imei1 = "",
          imei2 = "",
          aadhaarNumber = "",
          retailerName = "Metro Hub",
          invoiceNumber = "",
          productModel = "Galaxy S24",
          financeInfo = "",
          recordDate = "2026-11-17",
          syncedAtMillis = fixedNow,
          isFromCache = false,
        ),
        CachedSheetRowEntity(
          id = "r2",
          workspaceId = "ws1",
          sheetConnectionId = "s1",
          spreadsheetId = "sp1",
          spreadsheetName = "Sales Ledger",
          worksheetTitle = "Nov",
          rowNumber = 3,
          headersJson = """["Customer","Model","Retailer","Date"]""",
          cellsJson = """["Priya","Galaxy S24","City Store","2026-11-16"]""",
          searchableText = "priya galaxy s24 city store",
          customerName = "Priya",
          mobileNumber = "9999999992",
          imei1 = "",
          imei2 = "",
          aadhaarNumber = "",
          retailerName = "City Store",
          invoiceNumber = "",
          productModel = "Galaxy S24",
          financeInfo = "",
          recordDate = "2026-11-16",
          syncedAtMillis = fixedNow,
          isFromCache = false,
        ),
        CachedSheetRowEntity(
          id = "r3",
          workspaceId = "ws1",
          sheetConnectionId = "s2",
          spreadsheetId = "sp2",
          spreadsheetName = "Inventory",
          worksheetTitle = "Stock",
          rowNumber = 2,
          headersJson = """["Customer","Model","Retailer","Date"]""",
          cellsJson = """["Karan","iPhone 16","Metro Hub","2026-11-17"]""",
          searchableText = "karan iphone 16 metro hub",
          customerName = "Karan",
          mobileNumber = "9999999993",
          imei1 = "",
          imei2 = "",
          aadhaarNumber = "",
          retailerName = "Metro Hub",
          invoiceNumber = "",
          productModel = "iPhone 16",
          financeInfo = "",
          recordDate = "2026-11-17",
          syncedAtMillis = fixedNow,
          isFromCache = false,
        ),
      )

    val summary =
      AnalyticsEngine.computeAnalyticsSummary(
        rows = rows,
        audits = emptyList(),
        sheets = listOf(SheetConnection(id = "s1", customName = "Sales Ledger")),
        windowDays = 7,
        nowMillis = fixedNow,
      )

    assertEquals(7, summary.dailyEntries.size)
    assertEquals(3, summary.totalEntriesInWindow)
    assertEquals(2, summary.todayCount)
    assertEquals(2, summary.peakDayCount)
    assertEquals("Galaxy S24", summary.categoriesFor(AnalyticsCategoryDimension.MODELS).first().name)
    assertEquals(2, summary.categoriesFor(AnalyticsCategoryDimension.MODELS).first().count)
    assertEquals("Metro Hub", summary.categoriesFor(AnalyticsCategoryDimension.RETAILERS).first().name)
    assertEquals("Sales Ledger", summary.categoriesFor(AnalyticsCategoryDimension.SHEETS).first().name)
  }

  @Test
  fun appLinksConfig_buildsAndParsesBothHttpsAndAppSchemeInviteUrls() {
    val httpsUrl =
      com.example.util.AppLinksConfig.buildInviteUrlFromBase(
        "https://gen-lang-client-0057690374.firebaseapp.com/",
        " sm-ab12cd34 ",
      )
    assertEquals(
      "https://gen-lang-client-0057690374.firebaseapp.com/invite?code=SM-AB12CD34",
      httpsUrl,
    )
    assertEquals("SM-AB12CD34", com.example.util.AppLinksConfig.extractInviteCodeFromUriString(httpsUrl))
    assertEquals(
      "SM-XY99ZZ11",
      com.example.util.AppLinksConfig.extractInviteCodeFromUriString("smartsheet://invite?code=sm-xy99zz11"),
    )
    assertEquals(
      null,
      com.example.util.AppLinksConfig.extractInviteCodeFromUriString("https://gen-lang-client-0057690374.firebaseapp.com/privacy-policy"),
    )
  }
}
