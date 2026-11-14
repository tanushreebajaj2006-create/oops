package com.servicedesk.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthEndpointShouldReturnStatusUpAndUtcIsoTimestamp() throws Exception {
    mockMvc
        .perform(get("/api/v1/health").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.service").value("servicedesk-backend"))
        .andExpect(jsonPath("$.time").exists())
        .andExpect(
            jsonPath("$.time")
                .value(matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$")));
  }

  @Test
  void protectedEndpointsRequireAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/incidents"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void unknownPathsOutsideTheApiAreDenied() throws Exception {
    mockMvc.perform(get("/internal/secret")).andExpect(status().is4xxClientError());
  }
}
