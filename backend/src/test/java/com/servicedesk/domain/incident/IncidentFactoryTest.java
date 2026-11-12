package com.servicedesk.domain.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.exception.ServiceNotFoundException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Incident creation (Factory pattern)")
class IncidentFactoryTest {

  private final Fixtures f = new Fixtures();

  private NewIncident request(User reporter, String title, String desc, ItService svc, int users) {
    return new NewIncident(reporter, title, desc, svc, Level.MEDIUM, Level.HIGH, users);
  }

  @Test
  @DisplayName("TC-20 valid report creates a NEW incident with sequential ids")
  void createsIncident() {
    Incident a = f.newIncident();
    Incident b = f.newIncident();
    assertThat(a.status()).isEqualTo(IncidentStatus.NEW);
    assertThat(a.createdAt()).isEqualTo(Fixtures.T0);
    assertThat(a.reporter()).isEqualTo(f.employee);
    assertThat(b.id()).isEqualTo(new IncidentId("INC1025"));
    assertThat(a.category()).isEmpty();
    assertThat(a.priority()).isEmpty();
  }

  @ParameterizedTest(name = "title \"{0}\" is rejected")
  @ValueSource(strings = {"", "   ", "Bug"})
  @DisplayName("TC-21 blank or too-short titles are rejected")
  void invalidTitle(String title) {
    assertThatThrownBy(
            () ->
                f.factory.create(
                    request(f.employee, title, "Long enough description", f.payment, 1)))
        .isInstanceOf(InvalidInputException.class);
  }

  @Test
  @DisplayName("TC-22 boundary: title of exactly 5 and 120 characters is accepted, 121 is not")
  void titleBoundaries() {
    String desc = "Long enough description";
    f.factory.create(request(f.employee, "a".repeat(5), desc, f.payment, 1));
    f.factory.create(request(f.employee, "a".repeat(120), desc, f.payment, 1));
    assertThatThrownBy(
            () -> f.factory.create(request(f.employee, "a".repeat(121), desc, f.payment, 1)))
        .isInstanceOf(InvalidInputException.class);
  }

  @ParameterizedTest(name = "affected users = {0} is rejected")
  @ValueSource(ints = {0, -3, 1_000_001})
  @DisplayName("TC-23 affected users must be between 1 and 1,000,000")
  void invalidAffectedUsers(int users) {
    assertThatThrownBy(
            () ->
                f.factory.create(
                    request(
                        f.employee, "Printer down", "Printer on floor 3 jammed", f.payment, users)))
        .isInstanceOf(InvalidInputException.class);
  }

  @Test
  @DisplayName("TC-24 incidents cannot be raised against a retired service")
  void retiredService() {
    ItService retired = new ItService("FAX", "Fax", Level.LOW, false);
    assertThatThrownBy(
            () ->
                f.factory.create(
                    request(f.employee, "Fax broken", "Fax machine not sending", retired, 1)))
        .isInstanceOf(ServiceNotFoundException.class);
  }

  @Test
  @DisplayName("TC-25 the system actor is not allowed to report incidents")
  void systemCannotReport() {
    assertThatThrownBy(
            () ->
                f.factory.create(
                    request(f.system, "Auto ticket", "Created by a bot somehow", f.payment, 1)))
        .isInstanceOf(UnauthorizedOperationException.class);
  }

  @Test
  @DisplayName("TC-26 malformed incident ids are rejected")
  void incidentIdFormat() {
    assertThat(new IncidentId("inc42").value()).isEqualTo("INC42");
    assertThatThrownBy(() -> new IncidentId("TICKET-1")).isInstanceOf(InvalidInputException.class);
  }
}
