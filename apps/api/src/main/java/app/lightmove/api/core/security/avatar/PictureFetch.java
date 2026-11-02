package app.lightmove.api.core.security.avatar;

/** What asking a provider for a picture came to. */
public sealed interface PictureFetch {

    record Fetched(byte[] content) implements PictureFetch {
    }

    /** The link will not answer with a picture again: refused, missing, or something that is not one. */
    record Gone(String reason) implements PictureFetch {
    }

    /** It might next time: a timeout, the provider's own error, the network. */
    record Unavailable(String reason) implements PictureFetch {
    }
}
