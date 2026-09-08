package com.vodafone.ivr.dto.response;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code GET /customer/{phoneNumber}/vip-status}.
 * <p>The explicit {@link JsonProperty} matters: Jackson's default bean naming would strip the "is"
 * prefix from a boolean accessor and serialise this field as {@code "vip"}. The IVR flow is coded
 * against {@code "isVip"}, so the JSON name is pinned here rather than left to a convention.
 */
public record VipStatusResponse(@JsonProperty("isVip") boolean isVip) {
}
