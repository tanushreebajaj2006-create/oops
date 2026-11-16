"use client";

import { Card, EmptyState, ErrorBanner, PageHeader, Spinner } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { formatDateTime, humanize } from "@/lib/format";
import type { NotificationItem } from "@/lib/types";
import Link from "next/link";
import { useEffect, useState } from "react";

/** The in-app notification channel (the bell). */
export default function NotificationsPage() {
  const [items, setItems] = useState<NotificationItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .notifications()
      .then(setItems)
      .catch((e) => setError(errorMessage(e)));
  }, []);

  async function markRead(id: number) {
    try {
      await api.markRead(id);
      setItems((list) => list?.map((n) => (n.id === id ? { ...n, read: true } : n)) ?? null);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function markAll() {
    const unread = items?.filter((n) => !n.read) ?? [];
    for (const n of unread) {
      await markRead(n.id);
    }
  }

  const unreadCount = items?.filter((n) => !n.read).length ?? 0;
  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle={`${unreadCount} unread`}
        actions={
          unreadCount > 0 && (
            <button type="button" className="btn" onClick={markAll}>
              Mark all as read
            </button>
          )
        }
      />
      <ErrorBanner message={error} />
      <Card>
        {!items ? (
          <Spinner />
        ) : items.length === 0 ? (
          <EmptyState>No notifications yet.</EmptyState>
        ) : (
          <ul className="list">
            {items.map((n) => (
              <li key={n.id} className={`list-item ${n.read ? "" : "unread"}`}>
                <div className="row space-between">
                  <span className="row gap-s">
                    {n.urgent && <span className="badge priority-p1">Urgent</span>}
                    <strong>{n.subject}</strong>
                  </span>
                  <span className="muted small">{formatDateTime(n.createdAt)}</span>
                </div>
                <p className="small">{n.body}</p>
                <div className="row gap-m small">
                  <Link
                    href={`/incidents/${n.incidentId}`}
                    onClick={() => !n.read && markRead(n.id)}
                  >
                    Open {n.incidentId}
                  </Link>
                  <span className="muted">{humanize(n.eventType)}</span>
                  {!n.read && (
                    <button type="button" className="link-button" onClick={() => markRead(n.id)}>
                      Mark as read
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}
