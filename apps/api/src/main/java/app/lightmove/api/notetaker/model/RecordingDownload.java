package app.lightmove.api.notetaker.model;

import java.net.URI;

/** A call's recording about to be streamed: Recall's pre-signed link, which only the server ever holds, and a file name. */
public record RecordingDownload(URI video, String filename) {}
