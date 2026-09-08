package com.vodafone.ivr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vodafone.ivr.exception.BusinessRuleViolationException;
import com.vodafone.ivr.repository.CustomerRepository;

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
@DisplayName("VipStatusService")
class VipStatusServiceTest {

    private VipStatusService service;
    @Mock
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        service = new VipStatusService(customerRepository);
    }

    @Nested
    @DisplayName("digit sum")
    class DigitSum {

        @ParameterizedTest(name = "digitSum(\"{0}\") == {1}")
        @CsvSource({
                "01234567890, 45",
                "00000000001, 1",
                "00000000000, 0",
                "99999999999, 99",
                "01234567892, 47"
        })
        void sumsEveryDigit(String phoneNumber, int expected) {
            assertThat(service.digitSum(phoneNumber)).isEqualTo(expected);
        }

        @Test
        @DisplayName("handles a 15-digit number that would overflow an int if parsed numerically")
        void handlesMaximumLengthNumber() {
            assertThat(service.digitSum("999999999999999")).isEqualTo(135);
        }

        @Test
        @DisplayName("counts leading zeros as digits worth zero rather than dropping them")
        void keepsLeadingZeros() {
            assertThat(service.digitSum("0000000007")).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("primality")
    class Primality {

        @ParameterizedTest(name = "{0} is prime")
        @ValueSource(ints = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61,
                67, 71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131})
        void recognisesPrimes(int candidate) {
            assertThat(service.isPrime(candidate)).isTrue();
        }

        @ParameterizedTest(name = "{0} is not prime")
        @ValueSource(ints = {-7, -1, 0, 1, 4, 6, 8, 9, 15, 21, 25, 27, 45, 49, 81, 91, 100, 121, 135})
        void rejectsNonPrimes(int candidate) {
            assertThat(service.isPrime(candidate)).isFalse();
        }

        @Test
        @DisplayName("1 is not prime - reachable from phone number 00000000001")
        void oneIsNotPrime() {
            assertThat(service.isPrime(1)).isFalse();
        }

        @Test
        @DisplayName("2 is prime despite being even")
        void twoIsPrime() {
            assertThat(service.isPrime(2)).isTrue();
        }

        @Test
        @DisplayName("0 is not prime - reachable from an all-zero number")
        void zeroIsNotPrime() {
            assertThat(service.isPrime(0)).isFalse();
        }

        @Test
        @DisplayName("squares of primes are rejected, which a sqrt bound off by one would miss")
        void rejectsSquaresOfPrimes() {
            assertThat(service.isPrime(9)).isFalse();
            assertThat(service.isPrime(49)).isFalse();
            assertThat(service.isPrime(121)).isFalse();
        }
    }

    @Nested
    @DisplayName("isVip")
    class IsVip {

        @ParameterizedTest(name = "{0} -> isVip={1}")
        @CsvSource({
                "01234567892, true",
                "01234567890, false",
                "00000000001, false",
                "00000000000, false",
                "00000000002, true",
                "11111111111, true",
                "99999999999, false"
        })
        void appliesThePrimeDigitSumRule(String phoneNumber, boolean expected) {
            assertThat(service.isVip(phoneNumber)).isEqualTo(expected);
        }

        @Test
        @DisplayName("tolerates surrounding whitespace")
        void trimsInput() {
            assertThat(service.isVip("  01234567892  ")).isTrue();
        }

        @Test
        @DisplayName("rejects a null number instead of throwing NullPointerException")
        void rejectsNull() {
            assertThatThrownBy(() -> service.isVip(null))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("not supplied");
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {"", "   ", "0123abc890", "+201234567", "0123-456-789"})
        void rejectsNonDigitInput(String phoneNumber) {
            assertThatThrownBy(() -> service.isVip(phoneNumber))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
