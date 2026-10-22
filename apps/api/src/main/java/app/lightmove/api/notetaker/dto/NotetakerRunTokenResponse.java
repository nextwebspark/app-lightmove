package app.lightmove.api.notetaker.dto;

/** The run's token, handed to its bot once in exchange for the bot's own identity. */
public record NotetakerRunTokenResponse(String runToken) {

    @Override
    public String toString() {
        return "NotetakerRunTokenResponse[runToken=<redacted>]";
    }
}
