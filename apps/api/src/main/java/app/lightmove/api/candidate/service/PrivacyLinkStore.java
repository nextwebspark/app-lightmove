package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PrivacyLinkPurpose;
import app.lightmove.api.candidate.model.MintedPrivacyLink;
import app.lightmove.api.core.security.token.Tokens;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes the person's privacy links (V218); the row holds the token's hash, never the token. */
@Component
@RequiredArgsConstructor
class PrivacyLinkStore {

    private final NamedParameterJdbcTemplate jdbc;

    MintedPrivacyLink mint(UUID workspaceId, UUID personId, PrivacyLinkPurpose purpose, UUID sentBy, Instant now,
                           Duration lifetime) {
        return mintEach(workspaceId, List.of(personId), purpose, sentBy, now, lifetime).get(personId);
    }

    /** One link per person, in one batch; keyed by person id. */
    Map<UUID, MintedPrivacyLink> mintEach(UUID workspaceId, Collection<UUID> personIds, PrivacyLinkPurpose purpose,
                                          UUID sentBy, Instant now, Duration lifetime) {
        Map<UUID, MintedPrivacyLink> minted = new LinkedHashMap<>();
        List<MapSqlParameterSource> rows = new ArrayList<>(personIds.size());
        for (UUID personId : personIds) {
            MintedPrivacyLink link = new MintedPrivacyLink(UUID.randomUUID(), Tokens.generate());
            minted.put(personId, link);
            rows.add(new MapSqlParameterSource("id", link.id())
                    .addValue("workspaceId", workspaceId)
                    .addValue("personId", personId)
                    .addValue("tokenHash", Tokens.hash(link.token()))
                    .addValue("purpose", purpose.name())
                    .addValue("sentBy", sentBy)
                    .addValue("now", Timestamp.from(now))
                    .addValue("expiresAt", Timestamp.from(now.plus(lifetime))));
        }
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO app_lm_person_privacy_link (id, workspace_id, person_id, token_hash, purpose, sent_by,
                                                            created_at, expires_at)
                    VALUES (:id, :workspaceId, :personId, :tokenHash, :purpose, :sentBy, :now, :expiresAt)
                    """, rows.toArray(MapSqlParameterSource[]::new));
        }
        return minted;
    }
}
