/**
 * Incident lifecycle states (State pattern).
 *
 * <pre>
 * NEW -> CLASSIFIED -> ASSIGNED -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED -> VERIFIED -> CLOSED
 *                        ^  (re-assign / escalate)    |    ^          |
 *                        +----------------------------+    |          v (reopen)
 *                                         AWAITING_INFO <--+      IN_PROGRESS
 * NEW / CLASSIFIED / ASSIGNED -> CANCELLED
 * </pre>
 *
 * Concrete states are package-private: the rest of the system only sees {@link
 * com.servicedesk.domain.incident.state.IncidentState}.
 */
package com.servicedesk.domain.incident.state;
