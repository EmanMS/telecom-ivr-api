package com.vodafone.ivr.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SensitiveDataMasker")
class SensitiveDataMaskerTest {

    @Test
    @DisplayName("keeps only the last four digits of a card number")
    void masksCardNumber() {
        assertThat(SensitiveDataMasker.maskCardNumber("4242424242424242"))
                .isEqualTo("************4242");
    }

    @Test
    @DisplayName("never lets a full card number through, whatever its length")
    void neverLeaksAFullCardNumber() {
        for (String card : new String[] {"4242424242424242", "4111111111111", "378282246310005"}) {
            assertThat(SensitiveDataMasker.maskCardNumber(card)).doesNotContain(card);
        }
    }

    @Test
    @DisplayName("keeps only the last three digits of a phone number")
    void masksPhoneNumber() {
        assertThat(SensitiveDataMasker.maskPhoneNumber("01234567890"))
                .isEqualTo("********890");
    }

    @Test
    @DisplayName("masks a value shorter than the visible window completely")
    void masksShortValuesEntirely() {
        assertThat(SensitiveDataMasker.maskCardNumber("123")).isEqualTo("***");
    }

    @Test
    @DisplayName("reports absent values without throwing")
    void handlesNullAndBlank() {
        assertThat(SensitiveDataMasker.maskCardNumber(null)).isEqualTo("[absent]");
        assertThat(SensitiveDataMasker.maskPhoneNumber("   ")).isEqualTo("[absent]");
    }
}
