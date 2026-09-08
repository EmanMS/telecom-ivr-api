package com.vodafone.ivr.controller;

import com.vodafone.ivr.dto.response.VipStatusResponse;
import com.vodafone.ivr.service.SensitiveDataMasker;
import com.vodafone.ivr.service.VipStatusService;
import com.vodafone.ivr.validation.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subscriber lookups for the IVR flow.
 *
 * <p>The phone number arrives as an ordinary path variable. The IVR captures it via ANI before
 * this API is called; from the API's point of view it is simply an input to be validated like any
 * other, and nothing here depends on how the caller obtained it.
 *
 * <p>The {@link PhoneNumber} constraint is the same annotation {@code RechargeRequest} uses, so
 * this endpoint and the recharge endpoint cannot drift apart on what a valid number looks like.
 *
 * <p>{@code @Validated} on the class is what activates constraint annotations on method
 * parameters - {@code @Valid} alone only covers request bodies.
 */
@RestController
@RequestMapping(value = "/customer", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final VipStatusService vipStatusService;

    public CustomerController(VipStatusService vipStatusService) {
        this.vipStatusService = vipStatusService;
    }

    @GetMapping("/{phoneNumber}/vip-status")
    public ResponseEntity<VipStatusResponse> getVipStatus(
            @PathVariable @PhoneNumber String phoneNumber) {

        log.info("GET /customer/{}/vip-status", SensitiveDataMasker.maskPhoneNumber(phoneNumber));
        boolean vip = vipStatusService.isVip(phoneNumber);
        return ResponseEntity.ok(new VipStatusResponse(vip));
    }
}
