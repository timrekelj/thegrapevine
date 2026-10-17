import type { Metadata, Viewport } from "next";
import "./globals.css";
import Telemetry from "@/components/telemetry";

export const metadata: Metadata = {
  title: "The Grapevine",
  description: "A private newspaper curated by friends.",
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
