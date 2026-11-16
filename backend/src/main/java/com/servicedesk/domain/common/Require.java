package com.servicedesk.domain.common;

import com.servicedesk.domain.exception.InvalidInputException;
import java.util.Objects;

/** Small guard helpers so every domain constructor validates its input the same way. */
public final class Require {

  private Require() {}

  public static String notBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new InvalidInputException(field + " must not be blank");
    }
    return value.strip();
  }

  public static String maxLength(String value, int max, String field) {
    if (value != null && value.length() > max) {
      throw new InvalidInputException(field + " must be at most " + max + " characters");
    }
    return value;
  }

  public static <T> T notNull(T value, String field) {
    if (value == null) {
      throw new InvalidInputException(field + " is required");
    }
    return value;
  }

  public static int inRange(int value, int min, int max, String field) {
    if (value < min || value > max) {
      throw new InvalidInputException(field + " must be between " + min + " and " + max);
    }
    return value;
  }

  public static void that(boolean condition, String message) {
    if (!condition) {
      throw new InvalidInputException(Objects.requireNonNull(message));
    }
  }
}
