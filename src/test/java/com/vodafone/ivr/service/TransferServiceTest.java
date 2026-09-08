package com.vodafone.ivr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vodafone.ivr.dto.request.TransferRequest;
import com.vodafone.ivr.exception.BusinessRuleViolationException;
import com.vodafone.ivr.repository.CustomerRepository;
import com.vodafone.ivr.repository.TransactionRepository;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService")
class TransferServiceTest {

    private TransferService service;
    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        service = new TransferService(customerRepository, transactionRepository);
    }

    private TransferRequest requestWith(String fromNumber, String toNumber, String amount) {
        return new TransferRequest(fromNumber, toNumber, new BigDecimal(amount));
    }

    @Nested
    @DisplayName("matching prefix")
    class MatchingPrefix {

        @Test
        @DisplayName("accepts two numbers sharing the first three digits")
        void acceptsSamePrefix() {
            assertThatCode(() -> service.validateNumbersAreCompatible("01234567890", "01234567899"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts two numbers that are entirely identical")
        void acceptsIdenticalNumbers() {
            assertThatCode(() -> service.validateNumbersAreCompatible("01234567890", "01234567890"))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "rejects {0} -> {1}")
        @CsvSource({
                "01234567890, 01134567890", // differs at the 3rd digit
                "01234567890, 02234567890", // differs at the 2nd digit
                "01234567890, 11234567890", // differs at the 1st digit
                "01234567890, 99934567890" // no shared prefix at all
        })
        @DisplayName("rejects numbers whose first three digits differ")
        void rejectsDifferentPrefix(String fromNumber, String toNumber) {
            assertThatThrownBy(() -> service.validateNumbersAreCompatible(fromNumber, toNumber))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("first 3 digits");
        }

        @Test
        @DisplayName("compares exactly three digits - a shared 4th digit cannot rescue a bad 3rd")
        void comparesExactlyThreeDigits() {
            assertThatThrownBy(() -> service.validateNumbersAreCompatible("01234567890", "01934567890"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("a difference beyond the third digit is irrelevant")
        void ignoresDifferencesAfterThePrefix() {
            assertThatCode(() -> service.validateNumbersAreCompatible("01200000000", "01299999999"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("matching length")
    class MatchingLength {

        @ParameterizedTest(name = "rejects {0} -> {1}")
        @CsvSource({
                "01234567890, 0123456789", // recipient one digit shorter
                "01234567890, 012345678901", // recipient one digit longer
                "0123456789, 01234567890", // sender shorter
                "012, 01234567890" // wildly different
        })
        @DisplayName("rejects numbers of differing length")
        void rejectsDifferentLengths(String fromNumber, String toNumber) {
            assertThatThrownBy(() -> service.validateNumbersAreCompatible(fromNumber, toNumber))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("differ in length");
        }

        @Test
        @DisplayName("length is checked before the prefix, so the message names the real problem")
        void checksLengthBeforePrefix() {
            assertThatThrownBy(() -> service.validateNumbersAreCompatible("01234567890", "012"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("differ in length");
        }

        @Test
        @DisplayName("rejects numbers too short to hold a three-digit prefix")
        void rejectsNumbersShorterThanThePrefix() {
            assertThatThrownBy(() -> service.validateNumbersAreCompatible("01", "01"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("shorter than");
        }

        @ParameterizedTest(name = "rejects a null number ({0})")
        @ValueSource(strings = { "from", "to" })
        @DisplayName("rejects a null number instead of throwing NullPointerException")
        void rejectsNull(String whichIsNull) {
            String from = "from".equals(whichIsNull) ? null : "01234567890";
            String to = "to".equals(whichIsNull) ? null : "01234567890";

            assertThatThrownBy(() -> service.validateNumbersAreCompatible(from, to))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("must be supplied");
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
        @ValueSource(strings = { "0.01", "1", "25.0", "999.99", "1000" })
        void acceptsAmountsWithinLimit(String amount) {
            assertThatCode(() -> service.validateAmountWithinLimit(new BigDecimal(amount)))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "rejects {0}")
        @ValueSource(strings = { "1000.01", "1001", "5000", "99999999" })
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
    @DisplayName("transfer")
    class Transfer {

        @Test
        @DisplayName("completes when every rule passes")
        void acceptsValidRequest() {
            assertThatCode(() -> service.transfer(requestWith("01234567890", "01234567899", "25.0")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts a transfer of exactly the ceiling amount")
        void acceptsTransferAtCeiling() {
            assertThatCode(() -> service.transfer(requestWith("01234567890", "01234567899", "1000")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects an over-limit amount between compatible numbers")
        void rejectsOverLimitAmount() {
            assertThatThrownBy(() -> service.transfer(requestWith("01234567890", "01234567899", "1000.01")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("checks the numbers before the amount, so a bad prefix is reported first")
        void checksNumbersBeforeAmount() {
            assertThatThrownBy(() -> service.transfer(requestWith("01234567890", "09934567890", "5000")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("first 3 digits");
        }
    }

    @Test
    @DisplayName("the ceiling constant is the documented 1000")
    void ceilingConstantMatchesTheContract() {
        assertThat(TransferService.MAX_TRANSFER_AMOUNT).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("the transfer ceiling is a separate constant from the recharge ceiling")
    void ceilingIsIndependentOfTheRechargeCeiling() {
        assertThat(TransferService.MAX_TRANSFER_AMOUNT)
                .isNotSameAs(RechargeService.MAX_RECHARGE_AMOUNT);
    }
}
