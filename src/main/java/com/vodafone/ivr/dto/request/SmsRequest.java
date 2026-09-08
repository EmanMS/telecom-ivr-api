package com.vodafone.ivr.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vodafone.ivr.validation.PhoneNumber;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /sms}.
 *
 * <p><strong>On the field name.</strong> The IVR contract spells the field {@code tempelateCode}.
 * That typo is part of the wire format and cannot be changed without breaking the Call Studio
 * flow, but it does not have to spread through the Java code: the component is named correctly
 * and {@link JsonProperty} pins the wire name to the spec's spelling. The typo is now confined to
 * a single line. {@link JsonAlias} additionally accepts the correct spelling, so a caller that
 * sends {@code templateCode} - or a future corrected version of the IVR flow - keeps working
 * without a second deployment.
 *
 * <p>The template code is only checked for <em>presence</em> here. Which codes are actually
 * allowed is a business question, answered by {@code SmsService} against {@code SmsTemplate}.
 */
public record SmsRequest(

        @PhoneNumber
        String phoneNumber,
      
        @NotBlank(message = "templateCode is required")
        String templateCode) {}
