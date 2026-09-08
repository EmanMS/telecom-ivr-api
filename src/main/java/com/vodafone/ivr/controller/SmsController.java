package com.vodafone.ivr.controller;

import com.vodafone.ivr.dto.request.SmsRequest;
import com.vodafone.ivr.dto.response.ApiResponse;
import com.vodafone.ivr.dto.response.ResponseMessages;
import com.vodafone.ivr.service.SensitiveDataMasker;
import com.vodafone.ivr.service.SmsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbound SMS for the IVR flow.
 *
 * <p>A separate controller from {@code BalanceController} because it is a different resource, not
 * a variation on balance. The path is {@code /sms} exactly as the IVR contract specifies.
 *
 * <p>Thin by the same convention as the other controllers: bind, log, delegate, wrap.
 */
@RestController
@RequestMapping(value = "/sms", produces = MediaType.APPLICATION_JSON_VALUE)
public class SmsController {

    private static final Logger log = LoggerFactory.getLogger(SmsController.class);

    private final SmsService smsService;

    public SmsController(SmsService smsService) {
        this.smsService = smsService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> sendSms(@Valid @RequestBody SmsRequest request) {
        log.info("POST /sms for subscriber {} using template {}",
                SensitiveDataMasker.maskPhoneNumber(request.phoneNumber()),
                request.templateCode());

        smsService.send(request);

        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.SMS_SENT));
    }
}
