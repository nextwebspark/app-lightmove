package app.uncava.api.core.error.handler;

import tools.jackson.core.JacksonException;

/** A binding failure's JSON path in Bean Validation's spelling: {@code steps[1].subject}. */
final class RequestBodyPaths {

    private RequestBodyPaths() {
    }

    static String of(JacksonException failure) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : failure.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
