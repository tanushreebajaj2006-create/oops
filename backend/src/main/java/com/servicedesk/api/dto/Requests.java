package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.NoteType;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Request bodies of the REST API. Bean Validation annotations reject obviously bad input (blank,
 * too long, out of range) with a 400 before any business code runs; the domain then enforces the
 * full business rules.
 */
public final class Requests {

  private Requests() {}

  public record Login(@NotBlank @Email String email, @NotBlank String password) {}

  public record Register(
      @NotBlank @Size(max = 100) String fullName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @Size(max = 80) String department) {}

  public record ReportIncident(
      @NotBlank @Size(min = 5, max = 120) String title,
      @NotBlank @Size(min = 10, max = 4000) String description,
      @NotBlank String serviceCode,
      @NotNull Level impact,
      @NotNull Level urgency,
      @Min(1) @Max(1_000_000) Integer affectedUsers,
      String categoryCode) {}

  public record UpdateIncident(
      @NotBlank @Size(min = 5, max = 120) String title,
      @NotBlank @Size(min = 10, max = 4000) String description,
      @NotNull Level impact,
      @NotNull Level urgency,
      @NotNull @Min(1) @Max(1_000_000) Integer affectedUsers) {}

  public record Classify(@NotBlank String categoryCode) {}

  public record SetPriority(@NotNull Priority priority) {}

  /** Leave {@code technicianId} empty to let the active assignment strategy choose. */
  public record Assign(String technicianId) {}

  public record Text(@NotBlank @Size(max = 4000) String text) {}

  public record AddNote(@NotNull NoteType type, @NotBlank @Size(max = 4000) String text) {}

  public record Resolve(
      @NotBlank @Size(max = 2000) String summary, @Size(max = 2000) String rootCause) {}

  public record Close(@Size(max = 2000) String note) {}

  public record Feedback(
      @NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {}

  public record Availability(@NotNull Boolean available) {}

  public record CreateUser(
      @NotNull Role role,
      @NotBlank @Size(max = 100) String fullName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @Size(max = 80) String department,
      @Size(max = 20) String phone,
      Map<String, @Min(1) @Max(5) Integer> skills,
      @Min(0) @Max(60) Integer experienceYears,
      @Min(1) @Max(50) Integer maxWorkload) {}

  public record UpdateUser(
      @Size(max = 100) String fullName,
      @Size(max = 20) String phone,
      Boolean active,
      @Size(max = 80) String department,
      Map<String, @Min(1) @Max(5) Integer> skills,
      @Min(0) @Max(60) Integer experienceYears,
      @Min(1) @Max(50) Integer maxWorkload,
      Boolean available) {}

  /** The service code comes from the URL ({@code PUT /admin/services/{code}}). */
  public record SaveService(
      @NotBlank @Size(max = 80) String name, @NotNull Level criticality, Boolean active) {}

  /** The category code comes from the URL ({@code PUT /admin/categories/{code}}). */
  public record SaveCategory(@NotBlank @Size(max = 60) String name, Boolean active) {}

  public record SlaTargetMinutes(
      @Positive long responseMinutes, @Positive long resolutionMinutes) {}

  public record SaveSlaPolicy(
      @NotBlank @Size(max = 80) String name,
      String serviceCode,
      @DecimalMin("0.5") @DecimalMax("0.95") Double warningThreshold,
      @NotEmpty Map<Priority, @Valid SlaTargetMinutes> targets) {}

  public record ChangeStrategies(String assignment, String priority) {}

  /** Text the user has typed so far, for live category suggestions. */
  public record SuggestCategory(
      @NotBlank @Size(max = 120) String title, @Size(max = 4000) String description) {}

  /** A draft report, checked for duplicates before it is submitted. */
  public record DuplicateCheck(
      @NotBlank @Size(max = 120) String title,
      @Size(max = 4000) String description,
      @NotBlank String serviceCode) {}
}
