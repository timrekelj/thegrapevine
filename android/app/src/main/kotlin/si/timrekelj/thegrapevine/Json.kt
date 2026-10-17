package si.timrekelj.thegrapevine

import kotlinx.serialization.json.Json

/**
 * Matches the iOS decoder's behaviour: unknown keys are ignored, and null
 * optionals are omitted rather than sent as `null` (Swift's JSONEncoder omits
 * them, so the Postgres defaults apply).
 */
val SupabaseJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
