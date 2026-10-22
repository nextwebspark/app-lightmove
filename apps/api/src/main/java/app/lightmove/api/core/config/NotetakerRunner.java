package app.lightmove.api.core.config;

/** How our own Notetaker bot is started ({@code lightmove.notetaker.runner}). */
public enum NotetakerRunner {
    /** Logs the launch and starts nothing: the default, and the tests'. */
    LOG,
    /** A child process on this machine, for {@code npm run dev}. */
    LOCAL,
    /** One execution of the bot's Cloud Run Job per call. */
    CLOUD_RUN_JOB
}
