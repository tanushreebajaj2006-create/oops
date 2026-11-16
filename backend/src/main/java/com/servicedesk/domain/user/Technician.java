package com.servicedesk.domain.user;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.exception.TechnicianUnavailableException;
import com.servicedesk.domain.exception.WorkloadExceededException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A support engineer who works on incidents.
 *
 * <p>Holds a set of {@link Skill}s (<b>aggregation</b>: skills are values that describe the
 * technician) keyed by category code in a {@link LinkedHashMap} for O(1) lookup while keeping a
 * stable display order. The workload counter can only change through {@link #takeOn()} and {@link
 * #release()}, which enforce availability and the maximum workload (<b>encapsulation</b>).
 */
public class Technician extends User {

  public static final int DEFAULT_MAX_WORKLOAD = 5;

  private static final Set<Permission> PERMISSIONS =
      EnumSet.of(
          Permission.REPORT_INCIDENT, Permission.WORK_ON_INCIDENTS, Permission.CLASSIFY_INCIDENTS);

  private final Map<String, Skill> skills = new LinkedHashMap<>();
  private int experienceYears;
  private int maxWorkload;
  private int activeWorkload;
  private boolean available = true;

  public Technician(
      UserId id,
      String fullName,
      String email,
      String passwordHash,
      Collection<Skill> skills,
      int experienceYears,
      int maxWorkload) {
    super(id, fullName, email, passwordHash);
    setExperienceYears(experienceYears);
    setMaxWorkload(maxWorkload);
    if (skills != null) {
      skills.forEach(this::addSkill);
    }
  }

  @Override
  public Role role() {
    return Role.TECHNICIAN;
  }

  @Override
  protected Set<Permission> permissions() {
    return PERMISSIONS;
  }

  // ---- skills ---------------------------------------------------------------------------

  /** Adds a skill, replacing any existing skill for the same category. */
  public void addSkill(Skill skill) {
    Require.notNull(skill, "Skill");
    skills.put(skill.categoryCode(), skill);
  }

  public void removeSkill(String categoryCode) {
    skills.remove(categoryCode.toUpperCase());
  }

  public Optional<Skill> skillFor(String categoryCode) {
    return Optional.ofNullable(skills.get(categoryCode.toUpperCase()));
  }

  public boolean hasSkill(String categoryCode) {
    return skills.containsKey(categoryCode.toUpperCase());
  }

  public Collection<Skill> skills() {
    return Collections.unmodifiableCollection(skills.values());
  }

  // ---- workload -------------------------------------------------------------------------

  /** Whether this technician can accept another incident right now. */
  public boolean canTakeMoreWork() {
    return isActive() && available && activeWorkload < maxWorkload;
  }

  /**
   * Records that a new incident has been given to this technician.
   *
   * @throws TechnicianUnavailableException if inactive or marked unavailable
   * @throws WorkloadExceededException if already at maximum workload
   */
  public void takeOn() {
    if (!isActive() || !available) {
      throw new TechnicianUnavailableException("Technician " + id() + " is not available");
    }
    if (activeWorkload >= maxWorkload) {
      throw new WorkloadExceededException(
          "Technician "
              + id()
              + " already has "
              + activeWorkload
              + "/"
              + maxWorkload
              + " incidents");
    }
    activeWorkload++;
  }

  /**
   * Takes back an incident this technician already owned (e.g. a reopened incident). The maximum is
   * not enforced because the work was already theirs.
   */
  public void resume() {
    activeWorkload++;
  }

  /** Records that an incident no longer counts towards this technician's workload. */
  public void release() {
    if (activeWorkload > 0) {
      activeWorkload--;
    }
  }

  /** Used by persistence to restore the counter computed from stored incidents. */
  public void restoreWorkload(int openIncidents) {
    this.activeWorkload = Math.max(0, openIncidents);
  }

  public void markAvailable() {
    this.available = true;
  }

  public void markUnavailable() {
    this.available = false;
  }

  public final void setMaxWorkload(int maxWorkload) {
    this.maxWorkload = Require.inRange(maxWorkload, 1, 50, "Max workload");
  }

  public final void setExperienceYears(int years) {
    this.experienceYears = Require.inRange(years, 0, 60, "Experience years");
  }

  public int activeWorkload() {
    return activeWorkload;
  }

  public int maxWorkload() {
    return maxWorkload;
  }

  public int experienceYears() {
    return experienceYears;
  }

  public boolean isAvailable() {
    return available;
  }

  /** Fraction of capacity in use, 0.0 to 1.0+. Handy for load-based assignment. */
  public double utilisation() {
    return (double) activeWorkload / maxWorkload;
  }
}
