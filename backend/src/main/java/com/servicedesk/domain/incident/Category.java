package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import java.util.regex.Pattern;

/**
 * An incident category such as {@code NETWORK} or {@code DATABASE}.
 *
 * <p>Deliberately a value object stored as data, <i>not</i> a Java enum: administrators can add a
 * new category at runtime without changing or redeploying code, which is what the brief asks for
 * ("new categories ... without major changes to unrelated parts").
 *
 * @param code stable upper-case key, e.g. {@code CLOUD}
 * @param name human-friendly name
 * @param active retired categories stay for history but cannot be chosen for new incidents
 */
public record Category(String code, String name, boolean active) {

  private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");

  public Category {
    code = Require.notBlank(code, "Category code").toUpperCase();
    Require.that(CODE.matcher(code).matches(), "Category code must be like NETWORK: " + code);
    name = Require.maxLength(Require.notBlank(name, "Category name"), 60, "Category name");
  }

  public Category(String code, String name) {
    this(code, name, true);
  }

  public Category retire() {
    return new Category(code, name, false);
  }
}
