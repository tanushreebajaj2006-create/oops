package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.exception.ServiceNotFoundException;
import com.servicedesk.domain.priority.Level;

/**
 * An IT service the organisation runs (Payment Gateway, Email, VPN...). Its business criticality is
 * one of the inputs to incident priority.
 *
 * @param code stable key, e.g. {@code PAYMENT}
 * @param name display name
 * @param criticality how important the service is to the business
 * @param active retired services cannot receive new incidents
 */
public record ItService(String code, String name, Level criticality, boolean active) {

  public ItService {
    code = Require.notBlank(code, "Service code").toUpperCase();
    name = Require.maxLength(Require.notBlank(name, "Service name"), 80, "Service name");
    Require.notNull(criticality, "Business criticality");
  }

  public ItService(String code, String name, Level criticality) {
    this(code, name, criticality, true);
  }

  /**
   * @throws ServiceNotFoundException if the service has been retired
   */
  public void requireActive() {
    if (!active) {
      throw new ServiceNotFoundException("Service " + code + " is retired");
    }
  }
}
