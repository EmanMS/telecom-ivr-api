package com.vodafone.ivr.controller;

import com.vodafone.ivr.dto.request.RechargeRequest;
import com.vodafone.ivr.dto.request.TransferRequest;
import com.vodafone.ivr.dto.response.ApiResponse;
import com.vodafone.ivr.dto.response.ResponseMessages;
import com.vodafone.ivr.service.RechargeService;
import com.vodafone.ivr.service.SensitiveDataMasker;
import com.vodafone.ivr.service.TransferService;
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
 * Balance operations for the IVR flow: recharging from a card, and transferring between
 * subscribers.
 *
 * <p>Both operations share this controller because they share a resource - {@code /balance} - not
 * merely a prefix. Splitting them would mean two classes with identical dependencies-of-shape and
 * no clearer boundary between them.
 *
 * <p>The controller is intentionally thin: bind, log, delegate, wrap the outcome. It holds no
 * rules of its own, so the persistence phase can change what a recharge or transfer <em>does</em>
 * without this class being touched. Failures are not handled here either - the services raise them
 * and {@code GlobalExceptionHandler} renders them, which is what keeps the error contract
 * identical across every endpoint.
 */
@RestController
@RequestMapping(value = "/balance", produces = MediaType.APPLICATION_JSON_VALUE)
public class BalanceController {

    private static final Logger log = LoggerFactory.getLogger(BalanceController.class);

    private final RechargeService rechargeService;
    private final TransferService transferService;

    public BalanceController(RechargeService rechargeService, TransferService transferService) {
        this.rechargeService = rechargeService;
        this.transferService = transferService;
    }

    @PostMapping(value = "/recharge", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> recharge(@Valid @RequestBody RechargeRequest request) {
        log.info("POST /balance/recharge for subscriber {}",
                SensitiveDataMasker.maskPhoneNumber(request.phoneNumber()));

        rechargeService.recharge(request);

        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.RECHARGE_SUCCESSFUL));
    }

    @PostMapping(value = "/transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> transfer(@Valid @RequestBody TransferRequest request) {
        log.info("POST /balance/transfer from subscriber {} to subscriber {}",
                SensitiveDataMasker.maskPhoneNumber(request.fromNumber()),
                SensitiveDataMasker.maskPhoneNumber(request.toNumber()));

        transferService.transfer(request);

        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.TRANSFER_SUCCESSFUL));
    }
}
