package com.vodafone.ivr.repository;

import com.vodafone.ivr.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}