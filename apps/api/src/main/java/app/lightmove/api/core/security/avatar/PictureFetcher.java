package app.lightmove.api.core.security.avatar;

import java.net.URI;

/** Downloads a picture an identity provider named. Never throws: every outcome is a {@link PictureFetch}. */
public interface PictureFetcher {

    PictureFetch fetch(URI pictureUrl);
}
