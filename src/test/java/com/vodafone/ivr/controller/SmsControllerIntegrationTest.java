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
 * End-to-end coverage of {@code POST /sms}, including the misspelled wire field the IVR contract
 * mandates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("POST /sms")
class SmsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /** Builds a body  {@code templateCode}. */
    private String bodyOf(String phoneNumber, String templateCode) {
        return """
                {
                  "phoneNumber": "%s",
                  "templateCode": %s
                }
                """.formatted(phoneNumber, templateCode);
    }

    private String quoted(String value) {
        return "\"" + value + "\"";
    }

    @ParameterizedTest(name = "sends {0}")
    @ValueSource(strings = {"INTERNET_PACKAGES", "CALL_TONES", "PROMOTIONS"})
    @DisplayName("accepts every allowed template and returns the success envelope")
    void acceptsEveryAllowedTemplate(String templateCode) throws Exception {
        mockMvc.perform(post("/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf("01234567890", quoted(templateCode))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("The SMS has been sent successfully."));
    }

   
   @Test
    @DisplayName("successfully sends SMS with valid templateCode")
    void successfullySendsSms() throws Exception {
        mockMvc.perform(post("/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "01234567890",
                                  "templateCode": "INTERNET_PACKAGES"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("The SMS has been sent successfully."));
    }

    @ParameterizedTest(name = "rejects template {0}")
    @ValueSource(strings = {
            "\"ROAMING\"",
            "\"internet_packages\"",
            "\"INTERNET_PACKAGE\"",
            "\"\"",
            "null"})
    @DisplayName("rejects a template outside the allowed set")
    void rejectsInvalidTemplate(String templateCode) throws Exception {
        mockMvc.perform(post("/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf("01234567890", templateCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @Test
    @DisplayName("rejects a body with the template field missing entirely")
    void rejectsMissingTemplateField() throws Exception {
        mockMvc.perform(post("/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"01234567890\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @ParameterizedTest(name = "rejects phoneNumber \"{0}\"")
    @ValueSource(strings = {"0123456789", "012345678901", "0123abc8901", ""})
    @DisplayName("rejects a phone number that is not exactly 11 digits")
    void rejectsMalformedPhoneNumber(String phoneNumber) throws Exception {
        mockMvc.perform(post("/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(phoneNumber, quoted("INTERNET_PACKAGES"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @Test
    @DisplayName("rejects a malformed JSON body with the same envelope, not a parser dump")
    void rejectsMalformedJson() throws Exception {
        String body = mockMvc.perform(post("/sms")
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
        String body = mockMvc.perform(post("/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank().contains("\"success\":false");
    }

    @Test
    @DisplayName("answers the wrong HTTP verb with JSON, not an empty 405")
    void rejectsWrongHttpMethod() throws Exception {
        String body = mockMvc.perform(get("/sms"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank().contains("\"success\":false");
    }
}
