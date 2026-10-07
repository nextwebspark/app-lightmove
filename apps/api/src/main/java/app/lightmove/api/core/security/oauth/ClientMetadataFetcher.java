package app.lightmove.api.core.security.oauth;

import java.net.URI;

/** Reads a client id metadata document from where its client id says it lives. */
public interface ClientMetadataFetcher {

    /** Throws {@link ClientMetadataUnavailable}, never anything else a caller has to tell apart. */
    FetchedClientMetadata fetch(URI documentUrl);
}
