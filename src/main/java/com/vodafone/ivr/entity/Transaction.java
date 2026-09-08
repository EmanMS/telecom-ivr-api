package com.vodafone.ivr.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type; // "RECHARGE" or "TRANSFER"

    @Column(name = "from_number")
    private String fromNumber;

    @Column(name = "to_number", nullable = false)
    private String toNumber;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Transaction() {}

    public Transaction(String type, String fromNumber, String toNumber, BigDecimal amount) {
        this.type = type;
        this.fromNumber = fromNumber;
        this.toNumber = toNumber;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }
}