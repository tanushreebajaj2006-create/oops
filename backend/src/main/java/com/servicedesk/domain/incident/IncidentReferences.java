package com.servicedesk.domain.incident;

import com.servicedesk.domain.exception.EntityNotFoundException;
import com.servicedesk.domain.exception.ServiceNotFoundException;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;

/**
 * Turns the ids and codes in an {@link IncidentSnapshot} back into objects when an incident is
 * restored. Implemented by the persistence layer (usually with a per-load cache, so the same user
 * appearing as reporter and note author becomes one object).
 */
public interface IncidentReferences {

  /**
   * @throws EntityNotFoundException if the user does not exist
   */
  User user(UserId id);

  /**
   * @throws ServiceNotFoundException if the service does not exist
   */
  ItService service(String code);

  /**
   * @throws EntityNotFoundException if the category does not exist
   */
  Category category(String code);
}
