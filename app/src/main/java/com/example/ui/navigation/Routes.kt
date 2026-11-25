package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
  @Serializable data object Dashboard : AppRoute
  @Serializable data class GlobalSearch(val initialQuery: String = "", val filterSheetId: String? = null) : AppRoute
  @Serializable data class RecordDetail(val recordId: String) : AppRoute
  @Serializable data class AddRecord(val preselectedSheetId: String? = null) : AppRoute
  @Serializable data object SheetsManagement : AppRoute
  @Serializable data object Employees : AppRoute
  @Serializable data class EmployeeDetail(val employeeUid: String) : AppRoute
  @Serializable data object PermissionsMatrix : AppRoute
  @Serializable data object Analytics : AppRoute
  @Serializable data object ActivityLogs : AppRoute
  @Serializable data object Settings : AppRoute
  @Serializable data object Profile : AppRoute
  @Serializable data object AdminHub : AppRoute

  // Clickable Dashboard Card Detail Destinations
  @Serializable data object WorksheetsDetail : AppRoute
  @Serializable data object RecordsBreakdownDetail : AppRoute
  @Serializable data object SyncDiagnosticsDetail : AppRoute
  @Serializable data object SearchHistoryDetail : AppRoute
  @Serializable data object RecordAuditsDetail : AppRoute
  @Serializable data class LegalDocument(val documentType: String) : AppRoute
}
