package com.servicedesk.domain.notification;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.event.IncidentAssigned;
import com.servicedesk.domain.incident.event.IncidentEscalated;
import com.servicedesk.domain.incident.event.IncidentEvent;
import com.servicedesk.domain.incident.event.IncidentEventListener;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserDirectory;
import com.servicedesk.domain.user.UserId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Observer that turns incident events into notifications.
 *
 * <p>For each event: the {@link NotificationPolicy} says which audiences to tell on which channels;
 * audiences are resolved to real users through the {@link UserDirectory}; the {@link
 * NotificationFactory} writes each message; each {@link NotificationChannel} delivers it.
 *
 * <p>Rules applied here, the same for every policy:
 *
 * <ul>
 *   <li>Each person is notified at most once per channel per event, even if they fall into two
 *       audiences (e.g. a team lead who is also the assignee).
 *   <li>The person who caused the event is not notified, unless the rule says so.
 *   <li>Deactivated users are never notified.
 *   <li>A failing channel produces a FAILED result for that one delivery; everything else still
 *       goes out.
 * </ul>
 *
 * Channels are held in a {@link LinkedHashMap} by name, so a new mechanism is added with {@link
 * #register} and is immediately usable by any policy that names it.
 */
public class NotificationDispatcher implements IncidentEventListener {

  private final NotificationPolicy policy;
  private final NotificationFactory factory;
  private final UserDirectory users;
  private final Map<String, NotificationChannel> channels = new LinkedHashMap<>();

  public NotificationDispatcher(
      NotificationPolicy policy,
      NotificationFactory factory,
      UserDirectory users,
      Collection<? extends NotificationChannel> channels) {
    this.policy = Require.notNull(policy, "Notification policy");
    this.factory = Require.notNull(factory, "Notification factory");
    this.users = Require.notNull(users, "User directory");
    Require.notNull(channels, "Channels").forEach(this::register);
  }

  public synchronized NotificationDispatcher register(NotificationChannel channel) {
    Require.notNull(channel, "Channel");
    channels.put(channel.name().toUpperCase(), channel);
    return this;
  }

  public synchronized Set<String> channelNames() {
    return Set.copyOf(channels.keySet());
  }

  @Override
  public void onEvent(IncidentEvent event) {
    dispatch(event);
  }

  /** Sends everything the policy asks for and reports what happened to each delivery. */
  public List<DeliveryResult> dispatch(IncidentEvent event) {
    Require.notNull(event, "Event");
    Map<UserId, Target> targets = new LinkedHashMap<>();
    for (NotificationRule rule : policy.rulesFor(event)) {
      for (User user : resolve(rule.audience(), event)) {
        if (!user.isActive()) {
          continue;
        }
        if (user.id().equals(event.actorId()) && !rule.includeActor()) {
          continue;
        }
        targets
            .computeIfAbsent(user.id(), id -> new Target(user, rule.audience()))
            .channels
            .addAll(rule.channels());
      }
    }
    List<DeliveryResult> results = new ArrayList<>();
    for (Target target : targets.values()) {
      for (String channelName : target.channels) {
        results.add(deliver(event, target, channelName));
      }
    }
    return results;
  }

  private DeliveryResult deliver(IncidentEvent event, Target target, String channelName) {
    UserId to = target.user.id();
    NotificationChannel channel;
    synchronized (this) {
      channel = channels.get(channelName.toUpperCase());
    }
    if (channel == null) {
      return DeliveryResult.skipped(to, channelName, "channel not configured");
    }
    if (!channel.canReach(target.user)) {
      return DeliveryResult.skipped(to, channel.name(), "recipient not reachable on this channel");
    }
    try {
      Notification n = factory.create(event, target.user, target.audience, channel.name());
      channel.deliver(target.user, n);
      return DeliveryResult.sent(to, channel.name());
    } catch (RuntimeException e) {
      return DeliveryResult.failed(to, channel.name(), String.valueOf(e.getMessage()));
    }
  }

  private List<User> resolve(Audience audience, IncidentEvent event) {
    return switch (audience) {
      case REPORTER -> lookup(Optional.of(event.reporterId()));
      case ASSIGNEE -> lookup(event.assigneeId());
      case PREVIOUS_ASSIGNEE ->
          event instanceof IncidentAssigned a ? lookup(a.previousTechnicianId()) : List.of();
      case ESCALATION_TARGET ->
          event instanceof IncidentEscalated x ? lookup(Optional.of(x.escalatedTo())) : List.of();
      case SUPERVISORS -> users.withPermission(Permission.RECEIVE_ESCALATIONS);
    };
  }

  private List<User> lookup(Optional<UserId> id) {
    return id.flatMap(users::find).map(List::of).orElse(List.of());
  }

  /** One recipient and the union of channels they should get for this event. */
  private static final class Target {
    final User user;
    final Audience audience;
    final Set<String> channels = new LinkedHashSet<>();

    Target(User user, Audience audience) {
      this.user = user;
      this.audience = audience;
    }
  }
}
