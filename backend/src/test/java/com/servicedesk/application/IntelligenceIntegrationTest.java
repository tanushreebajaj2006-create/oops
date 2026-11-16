package com.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.audit.AuditEntry;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.intelligence.RelatedIncident;
import com.servicedesk.domain.intelligence.ResolutionSuggestion;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.user.UserId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Intelligent features through the real services, database and REST API. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Intelligent features (integration)")
class IntelligenceIntegrationTest {

  static final UserId ASHA = new UserId("E101");
  static final UserId KARAN = new UserId("E104");
  static final UserId MEERA = new UserId("L301");
  static final UserId ADMIN = new UserId("A001");

  @Autowired IncidentService incidents;
  @Autowired IntelligenceService intelligence;
  @Autowired AdminService admin;
  @Autowired IncidentHistoryProvider history;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void freshHistory() {
    history.invalidate();
  }

  @AfterEach
  void resetStrategy() {
    admin.changeStrategies(ADMIN, "SKILL_BASED", "MATRIX");
    history.invalidate();
  }

  private Incident report(
      UserId who, String title, String description, String service, String category) {
    return incidents.report(
        who,
        new IncidentService.ReportCommand(
            title, description, service, Level.HIGH, Level.HIGH, 20, category));
  }

  private Incident paymentOutage() {
    return report(
        ASHA,
        "Payment application unavailable",
        "The payment application shows a 503 error and users are unable to complete transactions.",
        "PAYMENT",
        "APPLICATION");
  }

  private String token(String email) throws Exception {
    record Login(String email, String password) {}
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new Login(email, "Demo@12345"))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("token").asText();
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r, String token) {
    return r.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  @Test
  @DisplayName(
      "TC-108 a report without a category is auto-categorised when the classifier is confident,"
          + " then prioritised and assigned")
  void autoCategorisation() {
    Incident vpn =
        report(
            ASHA,
            "Cannot connect to VPN from home",
            "The VPN client drops the connection every few minutes",
            "VPN",
            null);
    assertThat(vpn.category()).map(Category::code).contains("NETWORK");
    assertThat(vpn.priority()).isPresent();
    assertThat(vpn.status()).isEqualTo(IncidentStatus.ASSIGNED);
    assertThat(incidents.history(ASHA, vpn.id()))
        .filteredOn(e -> e.action().equals("CLASSIFIED"))
        .singleElement()
        .extracting(AuditEntry::description)
        .asString()
        .contains("auto-categorised")
        .contains("confidence")
        .contains("vpn");

    Incident vague =
        report(
            ASHA,
            "Something odd happened",
            "Things look strange today, not sure why",
            "WIKI",
            null);
    assertThat(vague.status()).isEqualTo(IncidentStatus.NEW); // left for a person to triage
    assertThat(vague.category()).isEmpty();
  }

  @Test
  @DisplayName(
      "TC-109 Workflow 3b: a second reporter is warned about the likely duplicate before"
          + " submitting")
  void duplicateWarning() throws Exception {
    Incident first = paymentOutage();
    List<RelatedIncident> found =
        intelligence.duplicateCheck(
            KARAN,
            "Unable to make payment",
            "I am unable to make a payment, the page keeps loading",
            "PAYMENT");
    assertThat(found)
        .first()
        .satisfies(
            r -> {
              assertThat(r.incident().id()).isEqualTo(first.id());
              assertThat(r.verdict()).isEqualTo(RelatedIncident.Verdict.LIKELY_DUPLICATE);
            });

    record Draft(String title, String description, String serviceCode) {}
    mvc.perform(
            as(
                    post("/api/v1/intelligence/duplicate-check"),
                    token("karan.mehta@servicedesk.example"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new Draft(
                            "Payment transaction failing",
                            "Every card transaction fails at the confirmation step",
                            "PAYMENT"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(first.id().value()))
        .andExpect(jsonPath("$[0].verdict").value("LIKELY_DUPLICATE"))
        .andExpect(jsonPath("$[0].reporter").doesNotExist()); // no personal data of other reporters

    Incident second =
        report(
            KARAN,
            "Payment transaction failing",
            "Every card transaction fails at the confirmation step",
            "PAYMENT",
            "APPLICATION");
    assertThat(intelligence.related(MEERA, second.id()))
        .extracting(r -> r.incident().id())
        .contains(first.id());
    assertThatThrownBy(() -> intelligence.related(new UserId("E102"), second.id()))
        .isInstanceOf(UnauthorizedOperationException.class);
  }

  @Test
  @DisplayName(
      "TC-110 resolution suggestions: a fix becomes a suggestion for the next similar incident")
  void resolutionSuggestions() {
    Incident first =
        report(
            ASHA,
            "VPN disconnects every 30 minutes",
            "Remote staff are dropped from the VPN every half hour",
            "VPN",
            "NETWORK");
    UserId tech = first.assignee().orElseThrow().id();
    incidents.acknowledge(tech, first.id());
    incidents.startWork(tech, first.id());
    incidents.resolve(tech, first.id(), "Increased VPN session timeout", "Idle timeout too short");

    Incident next =
        report(
            KARAN,
            "VPN keeps disconnecting",
            "I am dropped from the VPN every half hour",
            "VPN",
            "NETWORK");
    List<ResolutionSuggestion> s =
        intelligence.resolutionSuggestions(next.assignee().orElseThrow().id(), next.id());
    assertThat(s).isNotEmpty();
    assertThat(s.get(0).source().id()).isEqualTo(first.id());
    assertThat(s.get(0).summary()).isEqualTo("Increased VPN session timeout");
    assertThatThrownBy(() -> intelligence.resolutionSuggestions(KARAN, next.id()))
        .isInstanceOf(UnauthorizedOperationException.class);
  }

  @Test
  @DisplayName(
      "TC-111 the SMART strategy can be switched on at runtime and explains each assignment")
  void smartStrategy() {
    assertThat(admin.strategies().assignmentOptions()).extracting(s -> s.name()).contains("SMART");
    admin.changeStrategies(ADMIN, "SMART", null);
    Incident i = paymentOutage();
    assertThat(i.assignee()).isPresent();
    assertThat(incidents.history(ASHA, i.id()))
        .filteredOn(e -> e.action().equals("ASSIGNED"))
        .singleElement()
        .extracting(AuditEntry::description)
        .asString()
        .contains("SMART: score");
  }

  @Test
  @DisplayName(
      "TC-112 dashboards: correlation flags the payment outage; SLA-risk and correlations are for"
          + " supervisors only")
  void dashboards() throws Exception {
    paymentOutage();
    report(
        KARAN,
        "Payment transaction failing",
        "Card transactions fail at confirmation",
        "PAYMENT",
        "APPLICATION");
    report(
        new UserId("E106"),
        "Unable to make payment",
        "The payment page keeps loading forever",
        "PAYMENT",
        "APPLICATION");

    String lead = token("meera.lal@servicedesk.example");
    mvc.perform(as(get("/api/v1/reports/correlations"), lead))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].pattern").value("SERVICE_OUTAGE"))
        .andExpect(jsonPath("$[0].key").value("PAYMENT"))
        .andExpect(jsonPath("$[0].count").value(3))
        .andExpect(jsonPath("$[0].commonTerms", hasItem("payment")));
    mvc.perform(as(get("/api/v1/reports/sla-risk"), lead)).andExpect(status().isOk());

    String employee = token("asha.rao@servicedesk.example");
    mvc.perform(as(get("/api/v1/reports/correlations"), employee))
        .andExpect(status().isForbidden());
    mvc.perform(as(get("/api/v1/reports/sla-risk"), employee)).andExpect(status().isForbidden());

    record Text(String title, String description) {}
    mvc.perform(
            as(post("/api/v1/intelligence/category-suggestions"), employee)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new Text(
                            "Laptop battery drains fast",
                            "My laptop battery is empty after an hour"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("HARDWARE"))
        .andExpect(jsonPath("$[0].confident").value(true));
  }
}
