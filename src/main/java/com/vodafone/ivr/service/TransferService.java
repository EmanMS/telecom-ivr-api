package com.vodafone.ivr.service;

import com.vodafone.ivr.dto.request.TransferRequest;
import com.vodafone.ivr.entity.Customer;
import com.vodafone.ivr.entity.Transaction;
import com.vodafone.ivr.exception.BusinessRuleViolationException;
import com.vodafone.ivr.repository.CustomerRepository;
import com.vodafone.ivr.repository.TransactionRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the business rules that govern a balance transfer between two subscribers.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    /** Number of leading digits the two subscribers must share. */
    static final int REQUIRED_MATCHING_PREFIX_LENGTH = 3;

    /**
     * Maximum value of a single transfer, inclusive.
     */
    static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("1000");

    // 1. تعريف الـ Repositories
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    // 2. الـ Constructor
    public TransferService(CustomerRepository customerRepository, TransactionRepository transactionRepository) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Validates and performs a transfer.
     */
    @Transactional
    public void transfer(TransferRequest request) {
        log.info("Transfer requested from subscriber {} to subscriber {}, amount={}",
                SensitiveDataMasker.maskPhoneNumber(request.fromNumber()),
                SensitiveDataMasker.maskPhoneNumber(request.toNumber()),
                request.amount());

        validateNumbersAreCompatible(request.fromNumber(), request.toNumber());
        validateAmountWithinLimit(request.amount());

        // 3. كود الداتابيز (فحص رصيد الراسل، الخصم، الإضافة للمستلم، وحفظ المعاملة)
        Customer sender = customerRepository.findByPhoneNumber(request.fromNumber())
                .orElseThrow(() -> new BusinessRuleViolationException("Sender account does not exist"));

        if (sender.getBalance().compareTo(request.amount()) < 0) {
            throw new BusinessRuleViolationException("Insufficient balance to complete transfer");
        }

        Customer receiver = customerRepository.findByPhoneNumber(request.toNumber())
                .orElseGet(() -> new Customer(request.toNumber(), BigDecimal.ZERO, false));

        sender.setBalance(sender.getBalance().subtract(request.amount()));
        receiver.setBalance(receiver.getBalance().add(request.amount()));

        customerRepository.save(sender);
        customerRepository.save(receiver);

        Transaction tx = new Transaction(
                "TRANSFER",
                request.fromNumber(),
                request.toNumber(),
                request.amount()
        );
        transactionRepository.save(tx);

        log.info("Transfer accepted from subscriber {} to subscriber {}",
                SensitiveDataMasker.maskPhoneNumber(request.fromNumber()),
                SensitiveDataMasker.maskPhoneNumber(request.toNumber()));
    }

    /**
     * Enforces the two cross-field rules: equal length, and an identical three-digit prefix.
     */
    void validateNumbersAreCompatible(String fromNumber, String toNumber) {
        if (fromNumber == null || toNumber == null) {
            throw new BusinessRuleViolationException("Both subscriber numbers must be supplied");
        }
        if (fromNumber.length() != toNumber.length()) {
            throw new BusinessRuleViolationException(
                    "Subscriber numbers differ in length: " + fromNumber.length()
                            + " and " + toNumber.length());
        }
        if (fromNumber.length() < REQUIRED_MATCHING_PREFIX_LENGTH) {
            throw new BusinessRuleViolationException(
                    "Subscriber numbers are shorter than the required matching prefix");
        }
        String fromPrefix = fromNumber.substring(0, REQUIRED_MATCHING_PREFIX_LENGTH);
        String toPrefix = toNumber.substring(0, REQUIRED_MATCHING_PREFIX_LENGTH);
        if (!fromPrefix.equals(toPrefix)) {
            throw new BusinessRuleViolationException(
                    "Subscriber numbers do not share the first "
                            + REQUIRED_MATCHING_PREFIX_LENGTH + " digits");
        }
    }

    /**
     * The ceiling is inclusive: exactly 1000 is accepted, 1000.01 is not.
     */
    void validateAmountWithinLimit(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessRuleViolationException("Amount was not supplied");
        }
        if (amount.compareTo(MAX_TRANSFER_AMOUNT) > 0) {
            throw new BusinessRuleViolationException(
                    "Amount " + amount + " exceeds the maximum of " + MAX_TRANSFER_AMOUNT);
        }
    }
}