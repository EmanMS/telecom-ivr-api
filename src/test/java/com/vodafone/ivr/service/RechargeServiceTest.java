package com.vodafone.ivr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vodafone.ivr.dto.request.RechargeRequest;
import com.vodafone.ivr.exception.BusinessRuleViolationException;
import com.vodafone.ivr.repository.CustomerRepository;
import com.vodafone.ivr.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The clock is fixed to 15 June 2026 so that "expired", "expires this month" and "expires later"
 * stay meaningful for ever. A test written against the real clock would pass today and start
 * failing on its own months from now, which is the worst kind of test to inherit.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RechargeService")
class RechargeServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-15T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    
    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TransactionRepository transactionRepository;
    private RechargeService service;

    @BeforeEach
    void setUp() {
        service = new RechargeService(customerRepository,transactionRepository,FIXED_CLOCK);
    }

    private RechargeRequest requestWith(String expiryDate, String amount) {
        return new RechargeRequest(
                "01234567890", "4242424242424242", expiryDate, "567", new BigDecimal(amount));
    }

    @Nested
    @DisplayName("card expiry")
    class CardExpiry {

        @Test
        @DisplayName("accepts a card expiring exactly this month - it is valid until month end")
        void acceptsExpiryInCurrentMonth() {
            assertThatCode(() -> service.validateCardNotExpired("06/26")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects a card that expired last month")
        void rejectsExpiryInPreviousMonth() {
            assertThatThrownBy(() -> service.validateCardNotExpired("05/26"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("rejects a card that expired in December of the previous year")
        void rejectsExpiryInPreviousYear() {
            assertThatThrownBy(() -> service.validateCardNotExpired("12/25"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @ParameterizedTest(name = "accepts {0}")
        @ValueSource(strings = {"07/26", "12/26", "01/27", "12/34", "12/99"})
        void acceptsFutureExpiryDates(String expiryDate) {
            assertThatCode(() -> service.validateCardNotExpired(expiryDate))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("resolves a two-digit year into the 2000s, not the 1900s")
        void resolvesTwoDigitYearIntoCurrentCentury() {
            assertThatCode(() -> service.validateCardNotExpired("01/99")).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "rejects malformed value \"{0}\"")
        @ValueSource(strings = {"13/26", "00/26", "6/26", "06-26", "062026", "abc", "06/2026", " "})
        void rejectsMalformedExpiryDates(String expiryDate) {
            assertThatThrownBy(() -> service.validateCardNotExpired(expiryDate))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("rejects a null expiry date instead of throwing NullPointerException")
        void rejectsNull() {
            assertThatThrownBy(() -> service.validateCardNotExpired(null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("not supplied");
        }
    }

    @Nested
    @DisplayName("amount ceiling")
    class AmountCeiling {

        @Test
        @DisplayName("accepts exactly 1000 - the ceiling is inclusive")
        void acceptsExactCeiling() {
            assertThatCode(() -> service.validateAmountWithinLimit(new BigDecimal("1000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts 1000.00 - scale must not affect the comparison")
        void acceptsCeilingWithDifferentScale() {
            assertThatCode(() -> service.validateAmountWithinLimit(new BigDecimal("1000.00")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects the smallest value above the ceiling")
        void rejectsJustAboveCeiling() {
            assertThatThrownBy(() -> service.validateAmountWithinLimit(new BigDecimal("1000.01")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("exceeds");
        }

        @ParameterizedTest(name = "accepts {0}")
        @ValueSource(strings = {"0.01", "1", "50.0", "999.99", "1000"})
        void acceptsAmountsWithinLimit(String amount) {
            assertThatCode(() -> service.validateAmountWithinLimit(new BigDecimal(amount)))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "rejects {0}")
        @ValueSource(strings = {"1000.01", "1001", "5000", "99999999"})
        void rejectsAmountsAboveLimit(String amount) {
            assertThatThrownBy(() -> service.validateAmountWithinLimit(new BigDecimal(amount)))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("rejects a null amount instead of throwing NullPointerException")
        void rejectsNull() {
            assertThatThrownBy(() -> service.validateAmountWithinLimit(null))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("recharge")
    class Recharge {

        @Test
        @DisplayName("completes when every rule passes")
        void acceptsValidRequest() {
            assertThatCode(() -> service.recharge(requestWith("12/34", "50.0")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("checks expiry before amount, so an expired card is reported first")
        void checksExpiryBeforeAmount() {
            assertThatThrownBy(() -> service.recharge(requestWith("01/20", "5000")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("rejects an over-limit amount on an otherwise valid card")
        void rejectsOverLimitAmount() {
            assertThatThrownBy(() -> service.recharge(requestWith("12/34", "1000.01")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("reads the current month from the injected clock, not from the system clock")
        void readsTimeFromInjectedClock() {
            Clock stubbedClock = mock(Clock.class);
            when(stubbedClock.instant()).thenReturn(Instant.parse("2030-01-10T00:00:00Z"));
            when(stubbedClock.getZone()).thenReturn(ZoneOffset.UTC);

            RechargeService serviceInThe2030s = new RechargeService(customerRepository, transactionRepository, stubbedClock);

            assertThatThrownBy(() -> serviceInThe2030s.recharge(requestWith("12/29", "10")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("expired");
        }
    }

    @Test
    @DisplayName("the ceiling constant is the documented 1000")
    void ceilingConstantMatchesTheContract() {
        assertThat(RechargeService.MAX_RECHARGE_AMOUNT).isEqualByComparingTo("1000");
    }
}
