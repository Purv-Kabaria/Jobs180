package com.companytracker.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceNormalizeTest {

    @Test
    void normalizesEmail() {
        assertEquals("a@b.com", AuthService.normalizeEmail("  A@B.Com "));
    }

    @Test
    void rejectsBlankEmail() {
        assertThrows(Exception.class, () -> AuthService.normalizeEmail("  "));
    }
}
