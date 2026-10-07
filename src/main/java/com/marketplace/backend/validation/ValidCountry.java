package com.marketplace.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CountryValidator.class)
public @interface ValidCountry {
    String message() default "Choose a recognised country";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
