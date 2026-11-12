package com.servicedesk.domain.user;

import com.servicedesk.domain.common.Require;

/**
 * A technician's competence in one incident category (e.g. {@code NETWORK}) on a 1-5 scale.
 *
 * @param categoryCode code of the {@code Category} this skill covers
 * @param proficiency 1 (beginner) to 5 (expert)
 */
public record Skill(String categoryCode, int proficiency) {

  public static final int MIN = 1;
  public static final int MAX = 5;

  public Skill {
    categoryCode = Require.notBlank(categoryCode, "Skill category").toUpperCase();
    Require.inRange(proficiency, MIN, MAX, "Skill proficiency");
  }
}
