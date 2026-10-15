import Foundation

enum Config {
    /// Local development values. Point these at a hosted Supabase project for production.
    static let supabaseURL = URL(string: "http://localhost:55321")!
    /// The public anon key — access is restricted by row level security.
    static let supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
    static let authCallbackURL = URL(string: "grapevine://auth/callback")!
}
