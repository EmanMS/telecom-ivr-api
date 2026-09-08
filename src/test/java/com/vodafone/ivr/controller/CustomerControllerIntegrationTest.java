package com.vodafone.ivr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * End-to-end coverage of {@code GET /customer/{phoneNumber}/vip-status} through the real
 * application context: routing, path-variable validation, the service rule, and JSON rendering.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("GET /customer/{phoneNumber}/vip-status")
class CustomerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} -> isVip={1}")
    @CsvSource({
            "01234567892, true",
            "01234567890, false",
            "00000000001, false"
    })
    @DisplayName("returns the VIP verdict for a well-formed number")
    void returnsVipVerdict(String phoneNumber, boolean expected) throws Exception {
        mockMvc.perform(get("/customer/{phoneNumber}/vip-status", phoneNumber))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isVip").value(expected));
    }

    @Test
    @DisplayName("serialises the flag as isVip, not as vip")
    void usesTheContractedFieldName() throws Exception {
        String body = mockMvc.perform(get("/customer/01234567892/vip-status"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("{\"isVip\":true}");
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {
            "0123456789",          // 10 digits - one short
            "012345678901",        // 12 digits - one long
            "123",
            "0123456789012345678",
            "0123abc8901",
            "%20"})
    @DisplayName("rejects a number that is not exactly 11 digits, with the standard envelope")
    void rejectsMalformedNumbers(String phoneNumber) throws Exception {
        mockMvc.perform(get("/customer/{phoneNumber}/vip-status", phoneNumber))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    @Test
    @DisplayName("answers an unknown URL with JSON, never with an HTML error page")
    void unknownUrlStillReturnsJson() throws Exception {
        String body = mockMvc.perform(get("/customer/01234567890/loyalty-tier"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isNotBlank().startsWith("{");
    }
}
