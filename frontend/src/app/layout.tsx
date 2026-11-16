import { Providers } from "@/components/Providers";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "ServiceDesk | Incident Resolution System",
  description: "Intelligent IT Service Management & Incident Resolution System (ServiceDesk)",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
