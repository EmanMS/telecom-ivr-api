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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end coverage of {@code POST /balance/transfer}.
 *
 * <p>Lives in its own class rather than inside {@code BalanceControllerIntegrationTest} because it
 * is a distinct endpoint with its own fixture; the boundary cases for its rules are driven at unit
 * speed in {@code TransferServiceTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("POST /balance/transfer")
class TransferControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String bodyOf(String fromNumber, String toNumber, String amount) {
        return """
                {
                  "fromNumber": "%s",
                  "toNumber": "%s",
                  "amount": %s
                }
                """.formatted(fromNumber, toNumber, amount);
    }

    @Test
    @DisplayName("accepts a valid transfer and returns the success envelope")
    void acceptsValidTransfer() throws Exception {
        String body = mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf("01234567890", "01234567899", "25.0")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Balance has been transferred successfully"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank();
    }

    @Test
    @DisplayName("accepts exactly the maximum amount of 1000")
    void acceptsAmountAtCeiling() throws Exception {
        mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf("01234567890", "01234567899", "1000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("rejects an amount above the ceiling")
    void rejectsAmountAboveCeiling() throws Exception {
        mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf("01234567890", "01234567899", "1000.01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @ParameterizedTest(name = "rejects amount {0}")
    @ValueSource(strings = {"0", "-1", "-0.01", "null"})
    @DisplayName("rejects an amount that is absent or not strictly positive")
    void rejectsNonPositiveAmount(String amount) throws Exception {
        mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf("01234567890", "01234567899", amount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @ParameterizedTest(name = "rejects {0} -> {1}")
    @CsvSource({
            "01234567890, 09934567890",
            "01234567890, 01134567890",
            "01234567890, 11234567890"
    })
    @DisplayName("rejects a recipient whose first three digits differ")
    void rejectsMismatchedPrefix(String fromNumber, String toNumber) throws Exception {
        mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(fromNumber, toNumber, "25.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @ParameterizedTest(name = "rejects recipient \"{0}\"")
    @ValueSource(strings = {"0123456789", "012345678901", "0123abc7899", ""})
    @DisplayName("rejects a recipient number that is not exactly 11 digits")
    void rejectsMalformedRecipient(String toNumber) throws Exception {
        mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf("01234567890", toNumber, "25.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @Test
    @DisplayName("rejects a body with a missing required field")
    void rejectsMissingField() throws Exception {
        String withoutToNumber = """
                {
                  "fromNumber": "01234567890",
                  "amount": 25.0
                }
                """;

        mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withoutToNumber))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("rejects a malformed JSON body with the same envelope, not a parser dump")
    void rejectsMalformedJson() throws Exception {
        String body = mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromNumber\": \"0123456"))
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
        String body = mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank().contains("\"success\":false");
    }

    @Test
    @DisplayName("answers the wrong HTTP verb with JSON, not an empty 405")
    void rejectsWrongHttpMethod() throws Exception {
        String body = mockMvc.perform(get("/balance/transfer"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank().contains("\"success\":false");
    }

    @Test
    @DisplayName("never echoes either subscriber number back to the caller")
    void neverEchoesSubscriberNumbers() throws Exception {
        String body = mockMvc.perform(post("/balance/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf("01234567890", "01234567899", "25.0")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("01234567890").doesNotContain("01234567899");
    }
}
