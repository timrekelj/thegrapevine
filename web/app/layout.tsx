import type { Metadata, Viewport } from "next";
import "./globals.css";
import Telemetry from "@/components/telemetry";
import { sitePreview } from "@/lib/metadata";

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || "https://thegrapevine.si"),
  ...sitePreview,
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#EFEEE9",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en">
      <body><Telemetry />{children}</body>
    </html>
  );
}
