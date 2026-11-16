package com.servicedesk.api;

import com.servicedesk.api.dto.Views;
import com.servicedesk.application.InboxService;
import com.servicedesk.domain.exception.EntityNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's in-app notifications. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final InboxService inbox;

  public NotificationController(InboxService inbox) {
    this.inbox = inbox;
  }

  @GetMapping
  public List<Views.NotificationView> list(Authentication auth) {
    return inbox.items(CurrentUser.id(auth)).stream().map(ApiMapper::notification).toList();
  }

  @GetMapping("/unread-count")
  public Views.Count unread(Authentication auth) {
    return new Views.Count(inbox.unread(CurrentUser.id(auth)));
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRead(@PathVariable long id, Authentication auth) {
    if (!inbox.markRead(CurrentUser.id(auth), id)) {
      throw new EntityNotFoundException("Notification " + id + " not found");
    }
  }
}
