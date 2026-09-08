package com.vodafone.ivr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vodafone.ivr.dto.request.RechargeRequest;
import com.vodafone.ivr.service.RechargeService;
import com.vodafone.ivr.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice test with the service replaced by a Mockito mock.
 *
 * <p>Where the {@code @SpringBootTest} classes prove the endpoint works for real, this one isolates
 * the controller so it can prove two things the full-stack tests cannot: that the controller passes
 * the caller's data through unaltered, and that an unexpected service failure still leaves the API
 * as a well-formed JSON document rather than a stack trace.
 */
@WebMvcTest(BalanceController.class)
@DisplayName("BalanceController (web layer)")
class BalanceControllerWebLayerTest {

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

    @MockBean
    private RechargeService rechargeService;

    /**
     * Not exercised by these tests, but {@code BalanceController} now depends on it, so the slice
     * needs it to instantiate. A missing collaborator here fails the whole context, not one test.
     */
    @MockBean
    private TransferService transferService;

    @Test
    @DisplayName("binds the JSON body and hands it to the service unchanged")
    void delegatesRequestToService() throws Exception {
        mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<RechargeRequest> captor = ArgumentCaptor.forClass(RechargeRequest.class);
        verify(rechargeService).recharge(captor.capture());

        RechargeRequest captured = captor.getValue();
        assertThat(captured.phoneNumber()).isEqualTo("01234567890");
        assertThat(captured.cardNumber()).isEqualTo("4242424242424242");
        assertThat(captured.expiryDate()).isEqualTo("12/34");
        assertThat(captured.securityCode()).isEqualTo("567");
        assertThat(captured.amount()).isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("does not reach the service when Bean Validation has already rejected the body")
    void doesNotCallServiceOnStructurallyInvalidBody() throws Exception {
        mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"01234567890\"", "\"abc\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(rechargeService, never()).recharge(any());
    }

    @Test
    @DisplayName("turns an unexpected service failure into a JSON 500, not a stack trace")
    void rendersUnexpectedFailureAsJson() throws Exception {
        doThrow(new IllegalStateException("payment gateway unreachable at 10.0.0.7"))
                .when(rechargeService).recharge(any());

        String body = mockMvc.perform(post("/balance/recharge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .isNotBlank()
                .doesNotContain("10.0.0.7")
                .doesNotContainIgnoringCase("IllegalStateException");
    }
}
