package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** The calls this deployment's own bot can join, read once at boot so a misspelt platform fails the start. */
public record OfferedPlatforms(Set<NotetakerPlatform> platforms) {

    public OfferedPlatforms {
        platforms = platforms.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(platforms));
    }

    public static OfferedPlatforms parse(Collection<String> names) {
        Set<NotetakerPlatform> parsed = EnumSet.noneOf(NotetakerPlatform.class);
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                parsed.add(NotetakerPlatform.valueOf(name.trim()));
            }
        }
        return new OfferedPlatforms(parsed);
    }

    public boolean offers(NotetakerPlatform platform) {
        return platform != null && platforms.contains(platform);
    }
}
