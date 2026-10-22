/// The extension sends no analytics; `API` reports server errors through this.
enum Telemetry {
    static func httpError(_ status: Int) {}
}
