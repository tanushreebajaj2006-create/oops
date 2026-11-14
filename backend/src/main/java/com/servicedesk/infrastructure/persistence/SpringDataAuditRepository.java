package com.servicedesk.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data access to {@code audit_log}. */
public interface SpringDataAuditRepository
    extends JpaRepository<AuditEntryEntity, Long>, JpaSpecificationExecutor<AuditEntryEntity> {}
