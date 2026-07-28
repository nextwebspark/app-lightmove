package app.lightmove.api.company.service;

/**
 * A CoreSignal call failed. {@code fatal} separates the two ways a run should react: a bad API key
 * or an exhausted credit balance dooms every subsequent call, so the run must stop and report;
 * a rate-limit or transient 5xx dooms only this record, so the run skips it and carries on.
 */
public class CoreSignalUnavailableException extends RuntimeException {

    private final boolean fatal;

    public CoreSignalUnavailableException(String message, boolean fatal) {
        super(message);
        this.fatal = fatal;
    }

    public CoreSignalUnavailableException(String message, boolean fatal, Throwable cause) {
        super(message, cause);
        this.fatal = fatal;
    }

    public boolean isFatal() {
        return fatal;
    }
}
