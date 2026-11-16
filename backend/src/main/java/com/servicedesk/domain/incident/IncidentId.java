package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import java.util.regex.Pattern;

/** Identity of an incident in the form {@code INC} followed by digits, e.g. {@code INC1024}. */
public record IncidentId(String value) {

  private static final Pattern FORMAT = Pattern.compile("^INC\\d{1,12}$");

  public IncidentId {
    value = Require.notBlank(value, "Incident id").toUpperCase();
    Require.that(FORMAT.matcher(value).matches(), "Incident id must look like INC1024: " + value);
  }

  public static IncidentId of(long sequence) {
    return new IncidentId("INC" + sequence);
  }

  @Override
  public String toString() {
    return value;
  }
}
