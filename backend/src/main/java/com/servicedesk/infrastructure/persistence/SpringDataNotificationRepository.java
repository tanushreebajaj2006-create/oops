package com.servicedesk.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code notifications}. */
public interface SpringDataNotificationRepository extends JpaRepository<NotificationEntity, Long> {

  List<NotificationEntity> findByRecipientIdOrderByIdDesc(String recipientId);

  long countByRecipientIdAndReadFalse(String recipientId);

  Optional<NotificationEntity> findByIdAndRecipientId(Long id, String recipientId);
}
