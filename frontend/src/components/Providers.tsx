"use client";

import { AuthProvider } from "@/lib/auth";
import type { ReactNode } from "react";
import { AppShell } from "./AppShell";

/** Client-side providers wrapped around every page. */
export function Providers({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <AppShell>{children}</AppShell>
    </AuthProvider>
  );
}
