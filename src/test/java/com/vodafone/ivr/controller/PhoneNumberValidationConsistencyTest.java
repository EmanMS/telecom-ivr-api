package com.vodafone.ivr.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Guards the shared {@code @PhoneNumber} constraint against drift.
 *
 * <p>Five request positions across four endpoints accept a subscriber number: the VIP lookup's
 * path variable, the recharge body, both ends of a transfer, and the SMS body. Extracting the rule
 * into one annotation makes them agree; this test <em>proves</em> they agree, by putting the same
 * value through every one of them and demanding the same verdict. If someone later loosens the
 * rule on one endpoint - or adds a sixth position and hand-writes its constraints instead of
 * reusing the annotation - these assertions are what fails.
 *
 * <p>Every fixture around the number under test is deliberately valid, so a 400 can only be caused
 * by the number itself. The transfer cases hold the <em>other</em> number to a value sharing the
 * valid number's prefix, so the cross-field rules never confound the result.
 *
 * <p>The empty string is not exercised here: as a path variable it collapses the URL to
 * {@code /customer//vip-status}, which is a routing miss (404) rather than a validation failure,
 * so the endpoints cannot be compared on it. Each endpoint's own test class covers it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("phoneNumber validation is identical on every endpoint")
class PhoneNumberValidationConsistencyTest {

    private static final String VALID_PHONE_NUMBER = "01234567890";
    private static final String VALID_COUNTERPARTY = "01234567899";

    @Autowired
    private MockMvc mockMvc;

    private RequestBuilder vipStatus(String phoneNumber) {
        return get("/customer/{phoneNumber}/vip-status", phoneNumber);
    }

    private RequestBuilder recharge(String phoneNumber) {
        return post("/balance/recharge")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "phoneNumber": "%s",
                          "cardNumber": "4242424242424242",
                          "expiryDate": "12/34",
                          "securityCode": "567",
                          "amount": 50.0
                        }
                        """.formatted(phoneNumber));
    }

    private RequestBuilder transferFrom(String phoneNumber) {
        return transfer(phoneNumber, VALID_COUNTERPARTY);
    }

    private RequestBuilder transferTo(String phoneNumber) {
        return transfer(VALID_PHONE_NUMBER, phoneNumber);
    }

    private RequestBuilder transfer(String fromNumber, String toNumber) {
        return post("/balance/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fromNumber": "%s",
                          "toNumber": "%s",
                          "amount": 25.0
                        }
                        """.formatted(fromNumber, toNumber));
    }

    private RequestBuilder sms(String phoneNumber) {
        return post("/sms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "phoneNumber": "%s",
                          "templateCode": "INTERNET_PACKAGES"
                        }
                        """.formatted(phoneNumber));
    }

    @ParameterizedTest(name = "every endpoint rejects \"{0}\"")
    @ValueSource(strings = {
            "0123456789",        // 10 digits - one short
            "012345678901",      // 12 digits - one long
            "123",               // far too short
            "0123456789012345",  // far too long
            "0123abc8901",       // 11 characters, three not digits
            "+201234567890",     // international prefix
            "0123 456 7890",     // spaces
            "0123-456-7890"      // separators
    })
    @DisplayName("rejected in all five request positions alike")
    void everyEndpointRejectsTheSameValues(String phoneNumber) throws Exception {
        expectRejected(vipStatus(phoneNumber));
        expectRejected(recharge(phoneNumber));
        expectRejected(transferFrom(phoneNumber));
        expectRejected(transferTo(phoneNumber));
        expectRejected(sms(phoneNumber));
    }

    @Test
    @DisplayName("an exactly-11-digit number is accepted in all five request positions")
    void everyEndpointAcceptsTheSameValidValue() throws Exception {
        mockMvc.perform(vipStatus(VALID_PHONE_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVip").exists());

        expectAccepted(recharge(VALID_PHONE_NUMBER));
        expectAccepted(transferFrom(VALID_PHONE_NUMBER));
        expectAccepted(transferTo(VALID_COUNTERPARTY));
        expectAccepted(sms(VALID_PHONE_NUMBER));
    }

    private void expectRejected(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid data received"));
    }

    private void expectAccepted(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
