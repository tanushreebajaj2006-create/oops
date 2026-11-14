package com.servicedesk.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The REST API as a client sees it: JSON in, JSON out, bearer tokens, HTTP status codes. Uses the
 * seeded demo accounts. Every test is rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("REST API: security and workflows")
class ApiSecurityAndWorkflowTest {

  /** Demo password from V3__seed_reference_data.sql (development data only). */
  static final String DEMO_PASSWORD = "Demo@12345";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String login(String email) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new Login(email, DEMO_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("token").asText();
  }

  record Login(String email, String password) {}

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req, String token) {
    return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private ResultActions send(MockHttpServletRequestBuilder req, String token, Object body)
      throws Exception {
    MockHttpServletRequestBuilder r = as(req, token);
    if (body != null) {
      r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }
    return mvc.perform(r);
  }

  private JsonNode read(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  static final String EMPLOYEE = "asha.rao@servicedesk.example";
  static final String OTHER_EMPLOYEE = "ravi.kumar@servicedesk.example";
  static final String TECHNICIAN = "neha.sharma@servicedesk.example";
  static final String LEAD = "meera.lal@servicedesk.example";
  static final String ADMIN = "admin@servicedesk.example";

  // ---------------------------------------------------------------------------------------

  @Test
  @DisplayName("TC-93 login returns a JWT; wrong password and unknown e-mail get the same 401")
  void login() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Login(EMPLOYEE, DEMO_PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.token").value(startsWith("eyJ")))
        .andExpect(jsonPath("$.user.id").value("E101"))
        .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

    for (Login bad :
        new Login[] {
          new Login(EMPLOYEE, "wrong-password1"), new Login("nobody@x.example", "x1234567")
        }) {
      mvc.perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(bad)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
          .andExpect(jsonPath("$.message").value("Invalid e-mail or password"));
    }
  }

  @Test
  @DisplayName(
      "TC-94 self-registration creates an employee; weak passwords and duplicate e-mails are"
          + " rejected")
  void register() throws Exception {
    record Register(String fullName, String email, String password, String department) {}
    JsonNode session =
        read(
            mvc.perform(
                    post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                new Register(
                                    "New Joiner",
                                    "joiner@servicedesk.example",
                                    "Welcome2026",
                                    "Legal"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE")));
    String token = session.get("token").asText();
    send(get("/api/v1/auth/me"), token, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("joiner@servicedesk.example"));

    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new Register("Weak", "weak@servicedesk.example", "password", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(startsWith("Password must be")));
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(new Register("Clash", EMPLOYEE, "Welcome2026", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(startsWith("E-mail")));
  }

  @Test
  @DisplayName("TC-95 role-based access: 401 without/with a forged token, 403 for the wrong role")
  void roleBasedAccess() throws Exception {
    String employee = login(EMPLOYEE);
    String lead = login(LEAD);
    String admin = login(ADMIN);

    mvc.perform(get("/api/v1/reports/overview")).andExpect(status().isUnauthorized());
    send(get("/api/v1/reports/overview"), employee + "x", null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    send(get("/api/v1/admin/users"), employee, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    send(get("/api/v1/reports/overview"), employee, null).andExpect(status().isForbidden());
    send(get("/api/v1/technicians"), employee, null).andExpect(status().isForbidden());
    send(get("/api/v1/admin/users"), lead, null).andExpect(status().isForbidden());

    send(get("/api/v1/reports/overview"), lead, null).andExpect(status().isOk());
    send(get("/api/v1/technicians"), lead, null).andExpect(status().isOk());
    send(get("/api/v1/admin/users"), admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem("T201")));
  }

  @Test
  @DisplayName("TC-96 a deactivated account's still-valid token is rejected immediately")
  void deactivatedAccount() throws Exception {
    String admin = login(ADMIN);
    String employee = login(OTHER_EMPLOYEE);
    send(get("/api/v1/auth/me"), employee, null).andExpect(status().isOk());

    record Update(Boolean active) {}
    send(patch("/api/v1/admin/users/E102"), admin, new Update(false))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    send(get("/api/v1/auth/me"), employee, null).andExpect(status().isUnauthorized());
    // and the account can no longer log in
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Login(OTHER_EMPLOYEE, DEMO_PASSWORD))))
        .andExpect(status().isUnauthorized());
    // admins cannot lock themselves out
    send(patch("/api/v1/admin/users/A001"), admin, new Update(false))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("TC-97 full lifecycle over HTTP with the right status codes for each mistake")
  void fullWorkflowOverHttp() throws Exception {
    String employee = login(EMPLOYEE);
    String other = login(OTHER_EMPLOYEE);
    String tech = login(TECHNICIAN);

    record Report(
        String title,
        String description,
        String serviceCode,
        String impact,
        String urgency,
        Integer affectedUsers,
        String categoryCode) {}
    JsonNode created =
        read(
            send(
                    post("/api/v1/incidents"),
                    employee,
                    new Report(
                        "Payment application unavailable",
                        "Users cannot complete transactions since 10:00",
                        "PAYMENT",
                        "CRITICAL",
                        "HIGH",
                        300,
                        "APPLICATION"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/incidents/INC")))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.priority").value("P1"))
                .andExpect(jsonPath("$.assignee.id").value("T201"))
                .andExpect(jsonPath("$.sla.policy").value("Payments Gold"))
                .andExpect(jsonPath("$.allowedActions", hasItem("ACKNOWLEDGE"))));
    String id = created.get("id").asText();

    // validation (400), unknown service (404), someone else's incident (403), bad transition (409)
    send(
            post("/api/v1/incidents"),
            employee,
            new Report("", "short", "PAYMENT", "LOW", "LOW", 1, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    send(
            post("/api/v1/incidents"),
            employee,
            new Report(
                "Printer broken",
                "The printer does not print at all",
                "NOPE",
                "LOW",
                "LOW",
                1,
                null))
        .andExpect(status().isNotFound());
    send(get("/api/v1/incidents/" + id), other, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED_OPERATION"));
    send(get("/api/v1/incidents/INC99999999"), employee, null).andExpect(status().isNotFound());
    send(post("/api/v1/incidents/" + id + "/close"), employee, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    send(get("/api/v1/incidents?status=NOT_A_STATUS"), employee, null)
        .andExpect(status().isBadRequest());

    // technician works the incident
    send(post("/api/v1/incidents/" + id + "/acknowledge"), tech, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    send(post("/api/v1/incidents/" + id + "/start"), tech, null).andExpect(status().isOk());
    record Note(String type, String text) {}
    send(post("/api/v1/incidents/" + id + "/notes"), tech, new Note("INVESTIGATION", "Pods OOM"))
        .andExpect(status().isOk());
    send(get("/api/v1/technicians/me/workload"), tech, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeIncidents").value(1))
        .andExpect(jsonPath("$.incidents[0].id").value(id));
    record Resolve(String summary, String rootCause) {}
    send(
            post("/api/v1/incidents/" + id + "/resolve"),
            tech,
            new Resolve("Raised memory limit", "Leak"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));

    // reporter sees progress, but not the internal note; gets notified; verifies, rates, closes
    send(get("/api/v1/incidents/" + id), employee, null)
        .andExpect(jsonPath("$.notes[*].text", not(hasItem("Pods OOM"))))
        .andExpect(jsonPath("$.resolution.summary").value("Raised memory limit"));
    send(get("/api/v1/notifications/unread-count"), employee, null)
        .andExpect(jsonPath("$.count").value(4)); // created, assigned, acknowledged, resolved
    send(post("/api/v1/incidents/" + id + "/verify"), employee, null).andExpect(status().isOk());
    record Feedback(Integer rating, String comment) {}
    send(post("/api/v1/incidents/" + id + "/feedback"), employee, new Feedback(6, null))
        .andExpect(status().isBadRequest());
    send(post("/api/v1/incidents/" + id + "/feedback"), employee, new Feedback(5, "Fast"))
        .andExpect(status().isOk());
    send(post("/api/v1/incidents/" + id + "/close"), employee, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.allowedActions").isEmpty());

    send(get("/api/v1/incidents/" + id + "/history"), employee, null)
        .andExpect(jsonPath("$[0].action").value("CREATED"))
        .andExpect(jsonPath("$[*].action", hasItem("CLOSED")));
    send(
            get("/api/v1/incidents?service=PAYMENT&status=CLOSED&priority=P1&q=payment"),
            employee,
            null)
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id));
  }

  @Test
  @DisplayName("TC-98 admin configuration over HTTP: new category, SLA policy and strategy switch")
  void adminConfiguration() throws Exception {
    String admin = login(ADMIN);
    record Category(String name, Boolean active) {}
    send(put("/api/v1/admin/categories/PRINTING_3D"), admin, new Category("3D Printing", true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PRINTING_3D"));
    send(get("/api/v1/categories"), login(EMPLOYEE), null)
        .andExpect(jsonPath("$[*].code", hasItem("PRINTING_3D")));

    record Target(long responseMinutes, long resolutionMinutes) {}
    record Policy(
        String name,
        String serviceCode,
        Double warningThreshold,
        java.util.Map<String, Target> targets) {}
    send(
            put("/api/v1/admin/sla-policies"),
            admin,
            new Policy("VPN Priority", "VPN", 0.8, java.util.Map.of("P1", new Target(3, 20))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targets.P1.resolutionMinutes").value(20));
    send(
            put("/api/v1/admin/sla-policies"),
            admin,
            new Policy("Broken", null, null, java.util.Map.of("P1", new Target(30, 10))))
        .andExpect(status().isBadRequest());

    record Strategies(String assignment, String priority) {}
    send(put("/api/v1/admin/strategies"), admin, new Strategies("ROUND_ROBIN", "WEIGHTED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignment.name").value("ROUND_ROBIN"))
        .andExpect(jsonPath("$.priority.name").value("WEIGHTED"));
    send(get("/api/v1/admin/audit?action=STRATEGY_CHANGED"), admin, null)
        .andExpect(jsonPath("$.length()").value(2));
    send(put("/api/v1/admin/strategies"), admin, new Strategies("SKILL_BASED", "MATRIX"))
        .andExpect(status().isOk());
  }
}
