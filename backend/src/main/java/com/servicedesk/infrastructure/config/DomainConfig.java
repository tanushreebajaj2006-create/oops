package com.servicedesk.infrastructure.config;

import com.servicedesk.application.IncidentHistoryProvider;
import com.servicedesk.application.port.SettingsStore;
import com.servicedesk.domain.assignment.AssignmentStrategy;
import com.servicedesk.domain.assignment.TechnicianAssigner;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.audit.AuditTrailListener;
import com.servicedesk.domain.common.NamedStrategy;
import com.servicedesk.domain.common.StrategyRegistry;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.event.IncidentEventPublisher;
import com.servicedesk.domain.intelligence.CosineTextSimilarity;
import com.servicedesk.domain.intelligence.DuplicateDetector;
import com.servicedesk.domain.intelligence.IncidentCorrelator;
import com.servicedesk.domain.intelligence.ResolutionSuggester;
import com.servicedesk.domain.intelligence.SlaRiskPredictor;
import com.servicedesk.domain.intelligence.TextSimilarity;
import com.servicedesk.domain.notification.DefaultNotificationPolicy;
import com.servicedesk.domain.notification.EmailChannel;
import com.servicedesk.domain.notification.InAppChannel;
import com.servicedesk.domain.notification.InAppInbox;
import com.servicedesk.domain.notification.NotificationDispatcher;
import com.servicedesk.domain.notification.NotificationFactory;
import com.servicedesk.domain.notification.SmsChannel;
import com.servicedesk.domain.priority.PriorityStrategies;
import com.servicedesk.domain.priority.PriorityStrategy;
import com.servicedesk.domain.sla.EscalationPolicy;
import com.servicedesk.domain.user.UserRepository;
import com.servicedesk.infrastructure.notification.MailServiceEmailAdapter;
import com.servicedesk.infrastructure.notification.SimulatedMailService;
import com.servicedesk.infrastructure.notification.SimulatedSmsGateway;
import com.servicedesk.infrastructure.notification.SmsGatewayAdapter;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free domain objects into the Spring container. This is the only place that
 * chooses concrete classes (which strategies exist, which notification channels and adapters are
 * used) - swapping an implementation is a one-line change here.
 */
@Configuration
public class DomainConfig {

  private static final Logger LOG = LoggerFactory.getLogger(DomainConfig.class);

  @Bean
  public StrategyRegistry<PriorityStrategy> priorityStrategies(SettingsStore settings) {
    return restore(PriorityStrategies.defaults(), settings, SettingsStore.PRIORITY_STRATEGY);
  }

  @Bean
  public StrategyRegistry<AssignmentStrategy> assignmentStrategies(
      SettingsStore settings, IncidentHistoryProvider history) {
    return restore(
        TechnicianAssigner.defaultStrategies(history), settings, SettingsStore.ASSIGNMENT_STRATEGY);
  }

  // ---- intelligent features (pure domain algorithms, swappable through interfaces) ------

  @Bean
  public TextSimilarity textSimilarity() {
    return new CosineTextSimilarity();
  }

  @Bean
  public DuplicateDetector duplicateDetector(TextSimilarity similarity) {
    return new DuplicateDetector(similarity);
  }

  @Bean
  public ResolutionSuggester resolutionSuggester(TextSimilarity similarity) {
    return new ResolutionSuggester(similarity);
  }

  @Bean
  public IncidentCorrelator incidentCorrelator() {
    return new IncidentCorrelator();
  }

  @Bean
  public SlaRiskPredictor slaRiskPredictor() {
    return new SlaRiskPredictor();
  }

  @Bean
  public TechnicianAssigner technicianAssigner(StrategyRegistry<AssignmentStrategy> strategies) {
    return new TechnicianAssigner(strategies);
  }

  @Bean
  public IncidentFactory incidentFactory(IncidentRepository incidents, Clock clock) {
    return new IncidentFactory(incidents, clock);
  }

  @Bean
  public EscalationPolicy escalationPolicy() {
    return new EscalationPolicy();
  }

  // ---- notifications (Adapter pattern around simulated providers) -----------------------

  @Bean
  public SimulatedMailService simulatedMailService() {
    return new SimulatedMailService();
  }

  @Bean
  public SimulatedSmsGateway simulatedSmsGateway() {
    return new SimulatedSmsGateway();
  }

  @Bean
  public NotificationDispatcher notificationDispatcher(
      UserRepository users,
      InAppInbox inbox,
      SimulatedMailService mail,
      SimulatedSmsGateway sms,
      @Value("${app.notifications.from-address:noreply@servicedesk.example}") String from,
      @Value("${app.notifications.sms-sender-id:SVCDSK}") String smsSender) {
    return new NotificationDispatcher(
        new DefaultNotificationPolicy(),
        new NotificationFactory(),
        users,
        List.of(
            new InAppChannel(inbox),
            new EmailChannel(new MailServiceEmailAdapter(mail, from)),
            new SmsChannel(new SmsGatewayAdapter(sms, smsSender))));
  }

  /** Observer pattern: every incident event goes to the audit trail and the notifier. */
  @Bean
  public IncidentEventPublisher incidentEventPublisher(
      AuditLog auditLog, NotificationDispatcher dispatcher, IncidentHistoryProvider history) {
    return new IncidentEventPublisher()
        .subscribe(new AuditTrailListener(auditLog))
        .subscribe(dispatcher)
        .subscribe(history);
  }

  /** Re-activates the strategy an administrator chose before the last restart. */
  private static <S extends NamedStrategy> StrategyRegistry<S> restore(
      StrategyRegistry<S> registry, SettingsStore settings, String key) {
    settings
        .get(key)
        .ifPresent(
            name -> {
              try {
                registry.activate(name);
              } catch (RuntimeException e) {
                LOG.warn(
                    "Stored {} '{}' is unknown; keeping {}", key, name, registry.active().name());
              }
            });
    return registry;
  }
}
