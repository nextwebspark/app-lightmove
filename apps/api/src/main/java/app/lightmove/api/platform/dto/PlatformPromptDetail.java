package app.lightmove.api.platform.dto;

import java.util.List;

/**
 * One prompt for the editor: the shipped text, the saved one in use ({@code activeBody} null while shipped) and
 * every saved version, newest first. A save sends {@code activeVersion} back as its {@code expectedVersion}.
 */
public record PlatformPromptDetail(
        String id,
        String label,
        String resource,
        List<String> variables,
        String shippedBody,
        String activeBody,
        Integer activeVersion,
        List<PlatformPromptVersionResponse> versions,
        PlatformConfigVersionResponse config
) {}
