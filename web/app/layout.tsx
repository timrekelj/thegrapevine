import type { Metadata, Viewport } from "next";
import { IBM_Plex_Mono, Inter, Newsreader, UnifrakturMaguntia } from "next/font/google";
import "./globals.css";
import Telemetry from "@/components/telemetry";
import { sitePreview } from "@/lib/metadata";

// The paper's type: a blackletter nameplate, a news serif with optical sizes for everything you
// read, a typewriter mono for datelines and counts, and a plain sans for controls.
// `subsets` only picks what is preloaded: every subset stays in the CSS, so Slovenian letters
// (latin-ext) still load on demand when a page uses them. The sans only sets buttons, so it isn't preloaded.
// The mono is only ever set at 500 and 600, so those are the only weights fetched.
const nameplate = UnifrakturMaguntia({ weight: "400", subsets: ["latin"], variable: "--font-nameplate" });
const news = Newsreader({ subsets: ["latin"], style: ["normal", "italic"], axes: ["opsz"], variable: "--font-news" });
const mono = IBM_Plex_Mono({ weight: ["500", "600"], subsets: ["latin"], variable: "--font-mono" });
const ui = Inter({ subsets: ["latin"], variable: "--font-ui", preload: false });

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || "https://thegrapevine.si"),
  ...sitePreview,
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#F4F1EA" },
    { media: "(prefers-color-scheme: dark)", color: "#121110" },
  ],
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${nameplate.variable} ${news.variable} ${mono.variable} ${ui.variable}`}>
      <body><Telemetry />{children}</body>
    </html>
  );
}
