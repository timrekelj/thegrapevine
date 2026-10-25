import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import "./globals.css";
import Telemetry from "@/components/telemetry";
import { sitePreview } from "@/lib/metadata";

const display = localFont({ src: "./fonts/BricolageGrotesque.ttf", variable: "--font-display", display: "swap", weight: "200 800" });

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || "https://thegrapevine.si"),
  ...sitePreview,
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#F0F4EB" },
    { media: "(prefers-color-scheme: dark)", color: "#0E1711" },
  ],
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={display.variable}>
      <body><Telemetry />{children}</body>
    </html>
  );
}
