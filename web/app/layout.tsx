import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "The Grapevine",
  description: "A private newspaper curated by friends.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
