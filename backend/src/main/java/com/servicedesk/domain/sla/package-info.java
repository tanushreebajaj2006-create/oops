/**
 * Service Level Agreement (SLA) domain package.
 *
 * <ul>
 *   <li>{@link com.servicedesk.domain.sla.SlaPolicy} / {@link
 *       com.servicedesk.domain.sla.SlaPolicyCatalog} - configurable targets per priority, per
 *       service with a default fallback.
 *   <li>{@link com.servicedesk.domain.sla.SlaCalculator} - deadlines and ON_TRACK / AT_RISK /
 *       BREACHED / MET status for the response and resolution clocks.
 *   <li>{@link com.servicedesk.domain.sla.SlaMonitor} - periodic scan that raises each warning and
 *       violation exactly once, remembered in an {@link com.servicedesk.domain.sla.SlaAlertLog}.
 * </ul>
 */
package com.servicedesk.domain.sla;
