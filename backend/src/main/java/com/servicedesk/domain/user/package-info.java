/**
 * User domain package.
 *
 * <p>Abstract {@link com.servicedesk.domain.user.User} with subclasses {@code Employee}, {@code
 * Technician}, {@code TeamLead} (a technician who also assigns and receives escalations), {@code
 * Administrator} and the automated {@code SystemActor}. Each subclass declares its own permission
 * set, so authorization checks are polymorphic instead of long if/else chains on the role.
 * Instances are built through {@link com.servicedesk.domain.user.UserFactory}.
 */
package com.servicedesk.domain.user;
