package app.lightmove.api.outreach.model;

/**
 * One file sent with an outreach email: its sanitised name, the type its bytes were read as, and the bytes. Its
 * {@code toString} names the file and its size only — the bytes are a person's CV as often as not.
 */
public record EmailAttachment(String fileName, String contentType, byte[] content) {

    @Override
    public String toString() {
        return "EmailAttachment[fileName=" + fileName + ", contentType=" + contentType + ", sizeBytes="
                + (content == null ? 0 : content.length) + "]";
    }
}
