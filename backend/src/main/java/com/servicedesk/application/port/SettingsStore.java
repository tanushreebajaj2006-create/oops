package com.servicedesk.application.port;

import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;

/** Port: persistent key/value settings chosen by administrators (e.g. the active strategies). */
public interface SettingsStore {

  String ASSIGNMENT_STRATEGY = "assignment.strategy";
  String PRIORITY_STRATEGY = "priority.strategy";

  Optional<String> get(String key);

  void put(String key, String value, UserId changedBy, Instant at);
}
