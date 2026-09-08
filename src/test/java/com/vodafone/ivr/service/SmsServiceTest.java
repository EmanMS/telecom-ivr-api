package com.vodafone.ivr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vodafone.ivr.dto.request.SmsRequest;
import com.vodafone.ivr.exception.BusinessRuleViolationException;
import com.vodafone.ivr.repository.SmsLogRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsService")
class SmsServiceTest {

    @Mock
    private SmsLogRepository smsLogRepository;
    private SmsService service;

    
    @BeforeEach
    void setUp() {
        service = new SmsService(smsLogRepository);

    }

    @Nested
    @DisplayName("template resolution")
    class TemplateResolution {

        @ParameterizedTest(name = "resolves {0}")
        @EnumSource(SmsTemplate.class)
        @DisplayName("resolves every template in the catalogue by its own name")
        void resolvesEveryCatalogueEntry(SmsTemplate template) {
            assertThat(service.resolveTemplate(template.name())).isEqualTo(template);
        }

        @ParameterizedTest(name = "resolves \"{0}\"")
        @ValueSource(strings = {"INTERNET_PACKAGES", "CALL_TONES", "PROMOTIONS"})
        @DisplayName("resolves the three codes named in the spec, spelled exactly")
        void resolvesTheSpecifiedCodes(String code) {
            assertThatCode(() -> service.resolveTemplate(code)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {
                "INTERNET_PACKAGE",     // singular - a plausible typo
                "internet_packages",    // lower case
                "Internet_Packages",    // mixed case
                " INTERNET_PACKAGES",   // leading space
                "INTERNET PACKAGES",    // space instead of underscore
                "ROAMING",              // not in the catalogue
                "VIP_OFFERS",
                ""})
        @DisplayName("rejects anything that is not an exact catalogue entry")
        void rejectsUnknownCodes(String code) {
            assertThatThrownBy(() -> service.resolveTemplate(code))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Unknown SMS template code");
        }

        @Test
        @DisplayName("rejects a null code instead of throwing NullPointerException")
        void rejectsNull() {
            assertThatThrownBy(() -> service.resolveTemplate(null))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("names the rejected code in the message, for the log line")
        void reportsTheRejectedCode() {
            assertThatThrownBy(() -> service.resolveTemplate("ROAMING"))
                    .hasMessageContaining("ROAMING");
        }
    }

    @Nested
    @DisplayName("send")
    class Send {

        @ParameterizedTest(name = "sends {0}")
        @EnumSource(SmsTemplate.class)
        @DisplayName("accepts a request for any catalogue template")
        void sendsEveryCatalogueTemplate(SmsTemplate template) {
            SmsRequest request = new SmsRequest("01234567890", template.name());

            assertThatCode(() -> service.send(request)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects a request carrying a template outside the catalogue")
        void rejectsUnknownTemplate() {
            SmsRequest request = new SmsRequest("01234567890", "ROAMING");

            assertThatThrownBy(() -> service.send(request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("catalogue")
    class Catalogue {

        @Test
        @DisplayName("contains exactly the three templates the spec allows")
        void containsExactlyTheSpecifiedTemplates() {
            assertThat(SmsTemplate.values())
                    .containsExactly(SmsTemplate.INTERNET_PACKAGES,
                            SmsTemplate.CALL_TONES,
                            SmsTemplate.PROMOTIONS);
        }

        @Test
        @DisplayName("fromCode returns an empty Optional rather than throwing")
        void fromCodeIsTotal() {
            assertThat(SmsTemplate.fromCode("ROAMING")).isEmpty();
            assertThat(SmsTemplate.fromCode(null)).isEmpty();
            assertThat(SmsTemplate.fromCode("  ")).isEmpty();
            assertThat(SmsTemplate.fromCode("PROMOTIONS")).contains(SmsTemplate.PROMOTIONS);
        }
    }
}
