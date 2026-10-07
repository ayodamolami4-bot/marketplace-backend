package com.marketplace.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.*;

public class CountryValidator implements ConstraintValidator<ValidCountry, String> {
    private static final Set<String> COUNTRIES = new HashSet<>();
    static {
        for (String code : Locale.getISOCountries()) {
            COUNTRIES.add(code.toLowerCase(Locale.ROOT));
            COUNTRIES.add(new Locale("", code).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT));
        }
    }
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && COUNTRIES.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
