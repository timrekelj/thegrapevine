package si.timrekelj.thegrapevine

import kotlinx.serialization.Serializable

/** Body of `POST /functions/v1/delete-account`. Only `deleted: true` means the account is gone. */
@Serializable
internal data class DeleteAccountResponse(val deleted: Boolean = false)

internal fun accountWasDeleted(body: String): Boolean =
    runCatching { SupabaseJson.decodeFromString<DeleteAccountResponse>(body).deleted }.getOrDefault(false)

/** What Auth reported after `PUT auth/v1/user`. */
data class AuthEmailUpdate(
    val email: String? = null,
    val newEmail: String? = null,
)

/** The outcome the profile screen explains to the signed-in user. */
data class EmailChange(
    val requested: String,
    val appliedNow: Boolean,
    val appliedEmail: String? = null,
)

/**
 * A sign-in address Auth will accept: one @, a local part, and a dotted domain, with no spaces.
 * This is a filter for the field, not a full RFC parser.
 */
fun isSignInEmail(value: String): Boolean {
    val email = value.trim()
    if (email.length !in 3..320 || email.any { it.isWhitespace() }) return false
    val at = email.indexOf('@')
    if (at <= 0 || at != email.lastIndexOf('@') || at == email.lastIndex) return false
    val local = email.substring(0, at)
    val domain = email.substring(at + 1)
    if (local.length > 64 || domain.length > 255) return false
    if (!local.all { it.isLetterOrDigit() || it in "._%+-" }) return false
    if (local.startsWith('.') || local.endsWith('.') || ".." in local) return false
    val labels = domain.split('.')
    if (labels.size < 2) return false
    return labels.all { label ->
        label.isNotEmpty() && label.length <= 63 &&
            label.all { it.isLetterOrDigit() || it == '-' } &&
            !label.startsWith('-') && !label.endsWith('-')
    }
}

/**
 * Copy for a finished email change.
 * This project confirms a change from both inboxes (`double_confirm_changes`), so a pending change
 * says so. When Auth has already switched the address, the copy says that instead.
 */
fun emailChangeNotice(currentEmail: String, change: EmailChange): String {
    if (change.appliedNow) {
        val address = change.appliedEmail?.takeIf { it.isNotBlank() } ?: change.requested
        return "Your sign-in email is now $address."
    }
    val pending = change.requested.trim()
    val current = currentEmail.trim()
    if (current.isEmpty() || current.equals(pending, ignoreCase = true)) {
        return "Check your inbox. We sent a confirmation to $pending. Your sign-in email updates once you confirm it."
    }
    return "Check your inbox. We sent a confirmation to $pending and to $current. Your sign-in email stays $current until you confirm both."
}
