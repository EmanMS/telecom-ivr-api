package com.vodafone.ivr.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "sms_logs")
public class SmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 11)
    private String phoneNumber;

    @Column(name = "template_code", nullable = false)
    private String templateCode; // INTERNET_PACKAGES, CALL_TONES, PROMOTIONS

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public SmsLog() {}

    public SmsLog(String phoneNumber, String templateCode) {
        this.phoneNumber = phoneNumber;
        this.templateCode = templateCode;
        this.sentAt = LocalDateTime.now();
    }

    
}