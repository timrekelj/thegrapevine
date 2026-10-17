import Foundation

enum Config {
    #if DEBUG
    /// Local development: `supabase start` and the web dev server.
    static let supabaseURL = URL(string: "http://localhost:55321")!
    /// The publishable key — safe to ship; access is restricted by row level security.
    static let supabasePublishableKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
    static let webURL = URL(string: "http://localhost:3000")!
    #else
    /// Hosted: the production Supabase project and the deployed web app.
    static let supabaseURL = URL(string: "https://ivjaenrmhsqnsvzemoez.supabase.co")!
    static let supabasePublishableKey = "sb_publishable_oT-H6EAfuoNp8dcVdTHJow_l1-Skd1C"
    static let webURL = URL(string: "https://thegrapevine.si")!
    #endif
}
