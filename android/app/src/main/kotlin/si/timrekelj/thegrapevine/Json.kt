package si.timrekelj.thegrapevine

import kotlinx.serialization.json.Json

/**
 * Matches the iOS decoder's behaviour: unknown keys are ignored, and null
 * optionals are omitted rather than sent as `null` (Swift's JSONEncoder omits
 * them, so the Postgres defaults apply). Non-null defaults such as
 * `role = "owner"` are still sent, as Swift's synthesized Encodable does.
 */
val SupabaseJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
