import type { Metadata, Viewport } from "next";
import "./globals.css";
import NativeBridge from "@/components/native-bridge";

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
      <body>
        <NativeBridge />
        {children}
      </body>
    </html>
  );
}
