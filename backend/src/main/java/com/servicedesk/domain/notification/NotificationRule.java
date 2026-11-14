package com.servicedesk.domain.notification;

import com.servicedesk.domain.common.Require;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Tell this audience through these channels".
 *
 * @param audience who to tell
 * @param channels channel names, e.g. {@code IN_APP}, {@code EMAIL}
 * @param includeActor also notify the person who caused the event (normally they are skipped -
 *     nobody needs an e-mail saying they just clicked a button; creation confirmations are the
 *     exception)
 */
public record NotificationRule(Audience audience, Set<String> channels, boolean includeActor) {

  public NotificationRule {
    Require.notNull(audience, "Audience");
    Require.that(channels != null && !channels.isEmpty(), "At least one channel is required");
    // keep the order given (in-app first, then e-mail, then SMS) and make it read-only
    channels = Collections.unmodifiableSet(new LinkedHashSet<>(channels));
  }

  public static NotificationRule to(Audience audience, String... channels) {
    return new NotificationRule(audience, new LinkedHashSet<>(List.of(channels)), false);
  }

  public NotificationRule includingActor() {
    return new NotificationRule(audience, channels, true);
  }
}
