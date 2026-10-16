package app.uncava.api.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A working date: from 2000 to 2100, so a typo'd year 0202 or 9999 is refused rather than stored. Null passes. */
@Documented
@Constraint(validatedBy = PlausibleDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlausibleDate {

    String message() default "Enter a date between 2000 and 2100";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
