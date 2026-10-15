package app.lightmove.api.core.error.service;

import org.springframework.dao.DataIntegrityViolationException;

/** Names the database constraint behind a refused write, so a race is told apart from a genuine violation. */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** The violated constraint's name, or an empty string when the driver did not report one. */
    public static String nameOf(DataIntegrityViolationException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
        }
        return "";
    }
}
