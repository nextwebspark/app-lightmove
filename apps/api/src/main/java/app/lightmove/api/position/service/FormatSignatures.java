package app.lightmove.api.position.service;

/** The one byte-signature primitive every {@link PositionDocumentFormatReader} shares. */
final class FormatSignatures {

    private FormatSignatures() {
    }

    static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
