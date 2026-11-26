package app.lightmove.api.subprocessor.repository;

import app.lightmove.api.subprocessor.model.SubprocessorNotice;
import app.lightmove.api.subprocessor.model.SubprocessorNoticeDraft;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The notices themselves: a platform table, read alike from the console and from every workspace. */
@Repository
@RequiredArgsConstructor
public class SubprocessorNoticeRepository {

    private static final String COLUMNS = """
            SELECT n.id, n.provider, n.receives, n.purpose, n.region, n.effective_on, n.list_url, n.published_at,
                   publisher.full_name AS published_by_name
              FROM app_lm_subprocessor_notice n
              LEFT JOIN app_lm_user publisher ON publisher.id = n.published_by_user_id
            """;

    private final JdbcClient jdbc;

    public UUID insert(SubprocessorNoticeDraft draft, UUID publishedBy) {
        return jdbc.sql("""
                        INSERT INTO app_lm_subprocessor_notice
                            (provider, receives, purpose, region, effective_on, list_url, published_by_user_id)
                        VALUES (:provider, :receives, :purpose, :region, :effectiveOn, :listUrl, :publishedBy)
                        RETURNING id
                        """)
                .param("provider", draft.provider())
                .param("receives", draft.receives())
                .param("purpose", draft.purpose())
                .param("region", draft.region())
                .param("effectiveOn", draft.effectiveOn())
                .param("listUrl", draft.listUrl())
                .param("publishedBy", publishedBy)
                .query(UUID.class)
                .single();
    }

    /** The changes still to come first, then the ones in effect, the latest of each first. */
    public List<SubprocessorNotice> newestFirst() {
        return jdbc.sql(COLUMNS + " ORDER BY n.effective_on DESC, n.published_at DESC")
                .query(SubprocessorNoticeRepository::noticeOf)
                .list();
    }

    public Optional<SubprocessorNotice> find(UUID noticeId) {
        return jdbc.sql(COLUMNS + " WHERE n.id = :id")
                .param("id", noticeId)
                .query(SubprocessorNoticeRepository::noticeOf)
                .optional();
    }

    private static SubprocessorNotice noticeOf(ResultSet row, int rowNumber) throws SQLException {
        return new SubprocessorNotice(row.getObject("id", UUID.class), row.getString("provider"),
                row.getString("receives"), row.getString("purpose"), row.getString("region"),
                row.getObject("effective_on", LocalDate.class), row.getString("list_url"),
                row.getString("published_by_name"), row.getTimestamp("published_at").toInstant());
    }
}
