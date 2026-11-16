"use client";

import { Spinner } from "@/components/ui";
import { homeFor, useAuth } from "@/lib/auth";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

/** Sends each user to the start page for their role. */
export default function HomePage() {
  const { user } = useAuth();
  const router = useRouter();
  useEffect(() => {
    if (user) {
      router.replace(homeFor(user));
    }
  }, [user, router]);
  return (
    <div className="center-page">
      <Spinner />
    </div>
  );
}
