package com.example.util

object InvitationValidator {
  private val EMAIL_REGEX =
    Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

  fun normalizeEmail(email: String?): String =
    email?.trim()?.lowercase().orEmpty()

  fun isValidEmail(email: String?): Boolean {
    val normalized = normalizeEmail(email)
    return normalized.length in 5..200 && EMAIL_REGEX.matches(normalized)
  }

  fun validateEmailOrError(email: String?): String? {
    val normalized = normalizeEmail(email)
    return when {
      normalized.isBlank() -> "Employee email address is required."
      !isValidEmail(normalized) -> "Enter a valid email address (e.g. employee@company.com)."
      else -> null
    }
  }

  fun emailDocumentKey(email: String?): String {
    val normalized = normalizeEmail(email)
    // Encode characters outside [a-zA-Z0-9_-] deterministically for Firestore doc IDs
    val sb = StringBuilder(normalized.length + 12)
    for (ch in normalized) {
      when {
        ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '-' -> sb.append(ch)
        ch == '@' -> sb.append("_at_")
        ch == '.' -> sb.append("_dot_")
        else -> sb.append('_').append(ch.code.toString(16))
      }
    }
    return sb.toString().take(128)
  }
}
