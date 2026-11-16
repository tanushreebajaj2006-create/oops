package com.servicedesk.domain.incident;

import com.servicedesk.domain.exception.ServiceNotFoundException;
import java.util.List;
import java.util.Optional;

/** <b>Repository</b> of the IT services the organisation runs (managed by administrators). */
public interface ItServiceRepository {

  ItService save(ItService service);

  Optional<ItService> find(String code);

  /**
   * @throws ServiceNotFoundException if there is no such service
   */
  default ItService get(String code) {
    return find(code)
        .orElseThrow(() -> new ServiceNotFoundException("Service " + code + " not found"));
  }

  /** All services, ordered by name. */
  List<ItService> findAll();
}
