package app.lightmove.api.core.storage.service;

import app.lightmove.api.core.storage.model.RemoteImage;
import java.net.URI;
import java.util.Optional;

/** Fetches a picture published somewhere else — a workspace's logo — so an email never points at another host. */
public interface RemoteImageFetcher {

    /** Empty for anything refused or unreachable; losing a logo never fails the work that wanted it. */
    Optional<RemoteImage> fetch(URI address);
}
