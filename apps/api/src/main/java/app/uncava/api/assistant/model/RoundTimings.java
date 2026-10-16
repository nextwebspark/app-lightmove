package app.uncava.api.assistant.model;

/** How long one answer waited on the model: its rounds, and the attempts that had to be asked again. */
public record RoundTimings(int rounds, long modelMs, long slowestRoundMs, int reAsked) {

    public static final RoundTimings NONE = new RoundTimings(0, 0, 0, 0);

    public RoundTimings plus(long roundMs, int attemptsReAsked) {
        return new RoundTimings(rounds + 1, modelMs + roundMs, Math.max(slowestRoundMs, roundMs),
                reAsked + attemptsReAsked);
    }
}
