import type { Metadata, Viewport } from "next";
import { IBM_Plex_Mono, Inter, Newsreader, UnifrakturMaguntia } from "next/font/google";
import "./globals.css";
import Telemetry from "@/components/telemetry";
import { sitePreview } from "@/lib/metadata";

// The paper's type: a blackletter nameplate, a news serif with optical sizes for everything you
// read, a typewriter mono for datelines and counts, and a plain sans for controls.
const nameplate = UnifrakturMaguntia({ weight: "400", subsets: ["latin"], variable: "--font-nameplate" });
const news = Newsreader({ subsets: ["latin", "latin-ext"], style: ["normal", "italic"], axes: ["opsz"], variable: "--font-news" });
const mono = IBM_Plex_Mono({ weight: ["400", "500", "600"], subsets: ["latin", "latin-ext"], variable: "--font-mono" });
const ui = Inter({ subsets: ["latin", "latin-ext"], variable: "--font-ui" });

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
