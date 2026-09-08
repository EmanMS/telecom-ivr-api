package com.vodafone.ivr.service;

import com.vodafone.ivr.entity.Customer;
import com.vodafone.ivr.exception.BusinessRuleViolationException;
import com.vodafone.ivr.repository.CustomerRepository;

import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides whether a subscriber is a VIP.
 *
 * <p>The current rule is arithmetic: a subscriber is a VIP when the sum of the digits of their
 * phone number is a prime number. It is intentionally isolated behind {@link #isVip(String)} so
 * that the persistence phase can replace the rule with a lookup (or combine the two) without any
 * change to the controller, the DTOs, or the IVR flow.
 */
@Service
public class VipStatusService {

    private static final Logger log = LoggerFactory.getLogger(VipStatusService.class);
    private final CustomerRepository customerRepository;

    public VipStatusService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * @param phoneNumber the subscriber MSISDN, digits only
     * @return {@code true} when the digit sum of the number is prime
     * @throws BusinessRuleViolationException if the number is absent or contains a non-digit
     */

    @Transactional
    public boolean isVip(String phoneNumber) {
        
        String normalised = normalise(phoneNumber);
        int digitSum = digitSum(normalised);
        boolean vip = isPrime(digitSum);
        log.info("VIP evaluation for {}: digitSum={}, isVip={}",
                SensitiveDataMasker.maskPhoneNumber(normalised), digitSum, vip);

        Customer customer = customerRepository.findByPhoneNumber(normalised)
        .orElseGet(() -> new Customer(normalised, BigDecimal.ZERO, vip));
        
        customer.setIsVip(vip);
        customerRepository.save(customer);
        return vip;
    }

    /**
     * Defensive normalisation. Bean Validation already rejects malformed numbers at the edge, but
     * the service must not assume it is only ever called through the controller - a scheduled job
     * or a future internal caller would bypass those annotations entirely.
     */
    private String normalise(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BusinessRuleViolationException("Phone number was not supplied");
        }
        String trimmed = phoneNumber.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                throw new BusinessRuleViolationException("Phone number contains a non-digit character");
            }
        }
        return trimmed;
    }

  
    int digitSum(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += digits.charAt(i) - '0';
        }
        return sum;
    }

  
    boolean isPrime(int candidate) {
        if (candidate < 2) {
            return false;
        }
        if (candidate % 2 == 0) {
            return candidate == 2;
        }
        for (int divisor = 3; (long) divisor * divisor <= candidate; divisor += 2) {
            if (candidate % divisor == 0) {
                return false;
            }
        }
        return true;
    }
}
