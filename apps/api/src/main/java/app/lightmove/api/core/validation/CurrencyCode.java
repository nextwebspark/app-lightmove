package app.lightmove.api.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An ISO 4217 currency code, upper case ({@code AED}); null passes, as with every Bean Validation constraint. */
@Documented
@Constraint(validatedBy = CurrencyCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrencyCode {

    String message() default "Not a currency code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
