package com.vodafone.ivr.service;

import com.vodafone.ivr.dto.request.RechargeRequest;
import com.vodafone.ivr.entity.Customer;
import com.vodafone.ivr.exception.BusinessRuleViolationException;
import com.vodafone.ivr.repository.CustomerRepository;
import com.vodafone.ivr.repository.TransactionRepository;
import com.vodafone.ivr.entity.Transaction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class RechargeService {

    private static final Logger log = LoggerFactory.getLogger(RechargeService.class);

    /** Maximum value of a single recharge, inclusive. */
    static final BigDecimal MAX_RECHARGE_AMOUNT = new BigDecimal("1000");

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("MM/yy", Locale.ROOT);

    private final Clock clock;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

  
    public RechargeService(CustomerRepository customerRepository, 
                           TransactionRepository transactionRepository,
                           Clock clock) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }


    @Transactional
    public void recharge(RechargeRequest request) {

    log.info("Recharge requested for subscriber {} using card {}, amount={}",
            SensitiveDataMasker.maskPhoneNumber(request.phoneNumber()),
            SensitiveDataMasker.maskCardNumber(request.cardNumber()),
            request.amount());

    // 1. الفحص
    validateCardNotExpired(request.expiryDate());
    validateAmountWithinLimit(request.amount());

    // 2. إيجاد العميل أو إنشاؤه مع تحديد الـ isVip
    Customer customer = customerRepository
            .findByPhoneNumber(request.phoneNumber())
            .orElseGet(() -> new Customer(
                    request.phoneNumber(), 
                    BigDecimal.ZERO, 
                    false // أو تحسبيها
            ));

    // 3. زيادة الرصيد
    customer.setBalance(customer.getBalance().add(request.amount()));
    customerRepository.save(customer);

    // 4. تسجيل المعاملة
    Transaction tx = new Transaction(
            "RECHARGE",
            null,
            request.phoneNumber(),
            request.amount()
    );
    transactionRepository.save(tx);

    log.info("Recharge accepted for subscriber {}",
            SensitiveDataMasker.maskPhoneNumber(request.phoneNumber()));
}



    /**
     * Validates and performs a recharge.
     *
     * <p>Returns normally when the recharge succeeds. When persistence and a payment gateway are
     * added this method will return a receipt (transaction id, new balance); today there is nothing
     * truthful to return, and inventing a fake identifier would be worse than returning nothing.
     *
     * @throws BusinessRuleViolationException if the card has expired or the amount is above the
     *         per-transaction ceiling
     */
   

    /**
     * A card is valid throughout its expiry month, so the comparison is "is the expiry month
     * strictly before the current month" - a card marked {@code 08/26} is still good on
     * 31 August 2026. Getting this boundary wrong would decline valid cards for up to a month.
     */
    void validateCardNotExpired(String expiryDate) {
        YearMonth expiry = parseExpiry(expiryDate);
        YearMonth currentMonth = YearMonth.now(clock);
        if (expiry.isBefore(currentMonth)) {
            throw new BusinessRuleViolationException(
                    "Card expired in " + expiry + "; current month is " + currentMonth);
        }
    }

    /**
     * The ceiling is inclusive: exactly 1000 is accepted, 1000.01 is not.
     * {@code compareTo} is used rather than {@code equals} because {@code BigDecimal.equals}
     * also compares scale, making {@code 1000} and {@code 1000.00} unequal.
     */
    void validateAmountWithinLimit(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessRuleViolationException("Amount was not supplied");
        }
        if (amount.compareTo(MAX_RECHARGE_AMOUNT) > 0) {
            throw new BusinessRuleViolationException(
                    "Amount " + amount + " exceeds the maximum of " + MAX_RECHARGE_AMOUNT);
        }
    }

    /**
     * Parses {@code MM/yy}. A two-digit year resolves into 2000-2099, which is the convention
     * printed on the card itself and matches what the caller keys into the IVR.
     */
    private YearMonth parseExpiry(String expiryDate) {
        if (expiryDate == null || expiryDate.isBlank()) {
            throw new BusinessRuleViolationException("Expiry date was not supplied");
        }
        try {
            return YearMonth.parse(expiryDate.trim(), EXPIRY_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleViolationException("Expiry date is not a valid MM/yy value");
        }
    }
}
