package com.vodafone.ivr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: proves every bean wires up. Catches configuration mistakes that no amount of unit
 * testing would - a missing Clock bean, a duplicate mapping, a broken application.yml.
 */
@SpringBootTest
@DisplayName("Application context")
class TelecomIvrApiApplicationTests {

    @Test
    @DisplayName("loads")
    void contextLoads() {
    }
}
