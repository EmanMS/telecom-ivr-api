package com.vodafone.ivr.service;

import com.vodafone.ivr.dto.request.SmsRequest;
import com.vodafone.ivr.entity.SmsLog;
import com.vodafone.ivr.exception.BusinessRuleViolationException;
import com.vodafone.ivr.repository.SmsLogRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends a templated SMS to a subscriber.
 *
 * <p>The template code is validated <em>here</em> rather than by a Bean Validation annotation on
 * the DTO, for the same reason the recharge ceiling is: the set of allowed templates is a business
 * catalogue, not a property of the string. It grows and shrinks with marketing campaigns, and in
 * the persistence phase it will come from a table. A {@code @Pattern} listing the codes, or a DTO
 * field typed directly as the enum, would freeze today's catalogue into the transport layer and
 * put the check somewhere no service-layer unit test can reach it.
 *

 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
     private final SmsLogRepository smsLogRepository;

    public SmsService(SmsLogRepository smsLogRepository) {
        this.smsLogRepository = smsLogRepository;
    }
    /**
     * Validates and sends a templated SMS.
     *
     * @throws BusinessRuleViolationException if the template code is not in the catalogue
     */
    public void send(SmsRequest request) {
        log.info("SMS requested for subscriber {} using template {}",
                SensitiveDataMasker.maskPhoneNumber(request.phoneNumber()),
                request.templateCode());

        SmsTemplate template = resolveTemplate(request.templateCode());

           // 2. Save SMS log to database
        SmsLog smsLog = new SmsLog(
                request.phoneNumber(),
                template.name()
        );
        smsLogRepository.save(smsLog);

        log.info("SMS accepted for subscriber {} using template {}",
                SensitiveDataMasker.maskPhoneNumber(request.phoneNumber()), template);
    }

    /**
     * Maps a wire code onto the catalogue.
     */
    SmsTemplate resolveTemplate(String templateCode) {
        return SmsTemplate.fromCode(templateCode)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Unknown SMS template code: " + templateCode));
    }
}
