package com.servicedesk.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code users}; used only by {@link JpaUserRepository}. */
public interface SpringDataUserRepository extends JpaRepository<UserEntity, String> {

  Optional<UserEntity> findByEmailIgnoreCase(String email);

  List<UserEntity> findByRoleInAndActiveTrueOrderById(Collection<String> roles);

  List<UserEntity> findAllByOrderById();
}
