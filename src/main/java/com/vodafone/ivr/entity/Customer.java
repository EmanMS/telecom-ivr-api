package com.vodafone.ivr.entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "customers")

public class Customer {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", unique = true, nullable = false, length = 11)
    private String phoneNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal balance;

    @Column(name = "is_vip", nullable = false)
    private Boolean isVip;

    public Customer() {}

    public Customer(String phoneNumber, BigDecimal balance, Boolean isVip) {
        this.phoneNumber = phoneNumber;
        this.balance = balance;
        this.isVip = isVip;
    }
 
}