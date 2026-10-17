package si.timrekelj.thegrapevine

/**
 * Build-time configuration, mirroring `ios/Grapevine/Config.swift`.
 *
 * Debug builds talk to the local stack (`supabase start` on port 55321) on
 * localhost, forwarded to the host with `adb reverse`. Release builds point at
 * the hosted project.
 */
object Config {
    /** Always ends in `/`, so callers can append REST paths like `rest/v1/stories`. */
    val supabaseURL: String = BuildConfig.SUPABASE_URL.trimEnd('/') + "/"

    /** The publishable key — safe to ship; access is restricted by row level security. */
    val supabasePublishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    val webURL: String = BuildConfig.WEB_URL

    const val authCallback = "grapevine://auth/callback"
}
