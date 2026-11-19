
package com.revo.backend.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RegisterRequestValidationTest {

    @Test
    void shouldRejectInvalidRegistrationData() {

        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {

            Validator validator = factory.getValidator();

            RegisterRequest request = new RegisterRequest(
                    "",
                    "EMAIL",
                    "abc@example.com",
                    "123"
            );

            Set<ConstraintViolation<RegisterRequest>> violations =
                    validator.validate(request);

            Set<String> invalidFields = violations.stream()
                    .map(v -> v.getPropertyPath().toString())
                    .collect(Collectors.toSet());

            assertTrue(invalidFields.contains("fullName"));
            assertTrue(invalidFields.contains("password"));
        }
    }

    @Test
    void shouldAcceptValidRegistrationData() {

        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {

            Validator validator = factory.getValidator();

            RegisterRequest request = new RegisterRequest(
                    "Nguyen Van A",
                    "EMAIL",
                    "abc@example.com",
                    "Password123"
            );

            Set<ConstraintViolation<RegisterRequest>> violations =
                    validator.validate(request);

            assertTrue(violations.isEmpty());
        }
    }
}
