package app.lightmove.api.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An ISO 4217 code as a form or a file supplies it: blank passes (nothing chosen) and case is ignored, since the
 * model upper-cases it on write. Use {@link CurrencyCode} where the caller always sends the canonical code.
 */
@Documented
@Constraint(validatedBy = SuppliedCurrencyCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SuppliedCurrencyCode {

    String message() default "Choose a currency from the list";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
