package com.vodafone.ivr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end coverage of {@code POST /balance/recharge}.
 *
 * <p>Expiry dates in these fixtures are absolute rather than computed, so the tests assert against
 * the exact JSON the IVR will see. The business-rule boundary cases live in
 * {@code RechargeServiceTest}, which drives them against a fixed clock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("POST /balance/recharge")
class BalanceControllerIntegrationTest {

    private static final String VALID_BODY = """
            {
              "phoneNumber": "01234567890",
              "cardNumber": "4242424242424242",
              "expiryDate": "12/34",
              "securityCode": "567",
              "amount": 50.0
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    private String bodyWith(String field, String jsonValue) {
        return VALID_BODY.replaceAll(
                "\"" + field + "\": [^,\\n]+", "\"" + field + "\": " + jsonValue);
    }

    @Test
    @DisplayName("accepts a valid recharge and returns the success envelope")
    void acceptsValidRecharge() throws Exception {
        String body = mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Balance has been recharged successfully"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank();
    }

    @Test
    @DisplayName("accepts exactly the maximum amount of 1000")
    void acceptsAmountAtCeiling() throws Exception {
        mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("amount", "1000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("rejects an amount above the ceiling")
    void rejectsAmountAboveCeiling() throws Exception {
        mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("amount", "1000.01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @Test
    @DisplayName("rejects an expired card")
    void rejectsExpiredCard() throws Exception {
        mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("expiryDate", "\"01/20\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @ParameterizedTest(name = "rejects expiryDate {0}")
    @ValueSource(strings = {"\"13/34\"", "\"1/34\"", "\"12-34\"", "\"\"", "null"})
    @DisplayName("rejects a malformed expiry date at the validation layer")
    void rejectsMalformedExpiryDate(String jsonValue) throws Exception {
        mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("expiryDate", jsonValue)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @ParameterizedTest(name = "rejects cardNumber {0}")
    @ValueSource(strings = {
            "\"424242424242424\"",       // 15 digits - one short
            "\"42424242424242421\"",     // 17 digits - one long
            "\"424242\"",                // far too short
            "\"4242-4242-4242-4242\"",   // 16 digits but separators are not digits
            "\"424242424242424a\"",      // 16 characters, one not a digit
            "\"\"",
            "null"})
    @DisplayName("rejects a card number that is not exactly 16 digits")
    void rejectsInvalidCardNumber(String jsonValue) throws Exception {
        mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("cardNumber", jsonValue)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @ParameterizedTest(name = "rejects securityCode {0}")
    @ValueSource(strings = {
            "\"56\"",      // 2 digits
            "\"5678\"",    // 4 digits - accepted by some schemes, but the spec says 3
            "\"56a\"",
            "\"\"",
            "null"})
    @DisplayName("rejects a security code that is not exactly 3 digits")
    void rejectsInvalidSecurityCode(String jsonValue) throws Exception {
        mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("securityCode", jsonValue)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @Test
    @DisplayName("rejects a body with a missing required field")
    void rejectsMissingField() throws Exception {
        String withoutPhoneNumber = """
                {
                  "cardNumber": "4242424242424242",
                  "expiryDate": "12/34",
                  "securityCode": "567",
                  "amount": 50.0
                }
                """;

        mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withoutPhoneNumber))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @Test
    @DisplayName("rejects a malformed JSON body with the same envelope, not a parser dump")
    void rejectsMalformedJson() throws Exception {
        String body = mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"0123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .isNotBlank()
                .doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("com.fasterxml");
    }

    @Test
    @DisplayName("rejects an empty body with JSON rather than an empty response")
    void rejectsEmptyBody() throws Exception {
        String body = mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank().contains("\"success\":false");
    }

    @Test
    @DisplayName("answers the wrong HTTP verb with JSON, not an empty 405")
    void rejectsWrongHttpMethod() throws Exception {
        String body = mockMvc.perform(get("/balance/recharge"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank().contains("\"success\":false");
    }

    @Test
    @DisplayName("never echoes the card number or security code back to the caller")
    void neverEchoesCardDetails() throws Exception {
        String body = mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("4242424242424242").doesNotContain("567");
    }
}
