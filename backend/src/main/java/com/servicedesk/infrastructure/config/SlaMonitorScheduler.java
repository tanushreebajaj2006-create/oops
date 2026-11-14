package com.servicedesk.infrastructure.config;

import com.servicedesk.application.SlaMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the SLA check in the background (default: every 60 seconds, first run 30 seconds after
 * start-up). Disabled with {@code app.sla.monitor.enabled=false}, which the tests do so they can
 * trigger the check themselves at a controlled time.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "app.sla.monitor.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SlaMonitorScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(SlaMonitorScheduler.class);

  private final SlaMonitoringService monitoring;

  public SlaMonitorScheduler(SlaMonitoringService monitoring) {
    this.monitoring = monitoring;
  }

  @Scheduled(
      initialDelayString = "${app.sla.monitor.initial-delay:PT30S}",
      fixedDelayString = "${app.sla.monitor.interval:PT60S}")
  public void run() {
    try {
      monitoring.runCheck();
    } catch (RuntimeException e) {
      // never let one failed run stop the schedule (e.g. database briefly unavailable)
      LOG.error("SLA monitor run failed", e);
    }
  }
}
