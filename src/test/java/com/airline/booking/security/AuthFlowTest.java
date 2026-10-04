package com.airline.booking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.airline.booking.support.AbstractIntegrationTest;
import com.airline.booking.support.TokenFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthFlowTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TokenFactory tokens;

  @Test
  @DisplayName("valid admin credentials return a token, role and future expiry")
  void loginValidAdmin() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                                {"username":"admin","password":"admin123"}
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.expiresAt").isNotEmpty());
  }

  @Test
  @DisplayName("wrong password and unknown user are indistinguishable to the client")
  void loginFailuresAreIndistinguishable() throws Exception {
    String wrongPassword = login("admin", "not-the-password");
    String unknownUser = login("no-such-person", "anything");

    JsonNode a = objectMapper.readTree(wrongPassword);
    JsonNode b = objectMapper.readTree(unknownUser);

    assertThat(a.get("code").asText()).isEqualTo("UNAUTHENTICATED");
    assertThat(b.get("code").asText()).isEqualTo("UNAUTHENTICATED");
    // The security property: an attacker cannot enumerate usernames from the
    // difference between these two responses.
    assertThat(a.get("message").asText()).isEqualTo(b.get("message").asText());
  }

  @Test
  @DisplayName("a protected endpoint without a token is 401 in ApiError shape")
  void protectedEndpointWithoutToken() throws Exception {
    mockMvc.perform(get("/api/airports"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  @DisplayName("a malformed bearer token is 401 in ApiError shape, not an HTML page")
  void malformedTokenIsApiError() throws Exception {
    mockMvc.perform(get("/api/airports").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  @DisplayName("an admin endpoint with a customer token is 403 in ApiError shape")
  void adminEndpointWithCustomerToken() throws Exception {
    mockMvc.perform(get("/api/aircraft").header("Authorization", tokens.customerToken()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("an admin endpoint with an admin token succeeds")
  void adminEndpointWithAdminToken() throws Exception {
    mockMvc.perform(get("/api/aircraft").header("Authorization", tokens.adminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].aircraftCode").isNotEmpty());
  }

  @Test
  @DisplayName("a customer token reaches customer endpoints")
  void customerTokenReachesCustomerEndpoints() throws Exception {
    mockMvc.perform(get("/api/airports").header("Authorization", tokens.customerToken()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("login and swagger stay public")
  void publicEndpointsStayPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  private String login(String username, String password) throws Exception {
    return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
        .andExpect(status().isUnauthorized())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
