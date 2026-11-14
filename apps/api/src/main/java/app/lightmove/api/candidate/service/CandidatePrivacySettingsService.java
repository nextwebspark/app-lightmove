package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.LawfulBasis;
import app.lightmove.api.candidate.dto.CandidatePrivacySettingsResponse;
import app.lightmove.api.candidate.dto.UpdateCandidatePrivacySettingsRequest;
import app.lightmove.api.candidate.model.CandidatePrivacySettings;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The firm's own privacy details (#923), one row per workspace. Read by every notice and by the drawer; changed by an
 * admin, audited by the names of the fields that changed and never their values.
 */
@Service
@RequiredArgsConstructor
public class CandidatePrivacySettingsService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public CandidatePrivacySettings settingsOf(UUID workspaceId) {
        return jdbc.query("""
                SELECT controller_name, privacy_contact_email, notice_url, default_lawful_basis
                FROM app_lm_workspace_candidate_privacy WHERE workspace_id = :workspaceId
                """, new MapSqlParameterSource("workspaceId", workspaceId), (row, index) ->
                new CandidatePrivacySettings(row.getString("controller_name"), row.getString("privacy_contact_email"),
                        row.getString("notice_url"), LawfulBasis.valueOf(row.getString("default_lawful_basis"))))
                .stream().findFirst().orElse(CandidatePrivacySettings.NOT_SET_UP);
    }

    @Transactional(readOnly = true)
    public CandidatePrivacySettingsResponse responseOf(UUID workspaceId) {
        return toResponse(settingsOf(workspaceId));
    }

    @Transactional
    public CandidatePrivacySettingsResponse change(UUID actorId, UUID workspaceId,
                                                   UpdateCandidatePrivacySettingsRequest request,
                                                   HttpServletRequest httpRequest) {
        CandidatePrivacySettings wanted = new CandidatePrivacySettings(blankToNull(request.controllerName()),
                blankToNull(request.privacyContactEmail()), noticeUrlOf(request.noticeUrl()),
                ApiValueEnum.require(LawfulBasis.class, request.defaultLawfulBasis(), "lawful basis"));
        CandidatePrivacySettings held = settingsOf(workspaceId);
        List<String> changed = changedFields(held, wanted);
        if (changed.isEmpty()) {
            return toResponse(held);
        }
        jdbc.update("""
                INSERT INTO app_lm_workspace_candidate_privacy
                    (workspace_id, controller_name, privacy_contact_email, notice_url, default_lawful_basis, updated_by)
                VALUES (:workspaceId, :controllerName, :contact, :noticeUrl, :basis, :actorId)
                ON CONFLICT (workspace_id) DO UPDATE SET
                    controller_name = EXCLUDED.controller_name,
                    privacy_contact_email = EXCLUDED.privacy_contact_email,
                    notice_url = EXCLUDED.notice_url,
                    default_lawful_basis = EXCLUDED.default_lawful_basis,
                    updated_by = EXCLUDED.updated_by
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("controllerName", wanted.controllerName())
                .addValue("contact", wanted.privacyContactEmail())
                .addValue("noticeUrl", wanted.noticeUrl())
                .addValue("basis", wanted.defaultLawfulBasis().name())
                .addValue("actorId", actorId));
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(httpRequest)
                .detail("section", "candidatePrivacy")
                .detail("changed", String.join(",", changed))
                .record();
        return toResponse(wanted);
    }

    private static List<String> changedFields(CandidatePrivacySettings held, CandidatePrivacySettings wanted) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(held.controllerName(), wanted.controllerName())) {
            changed.add("controllerName");
        }
        if (!Objects.equals(held.privacyContactEmail(), wanted.privacyContactEmail())) {
            changed.add("privacyContactEmail");
        }
        if (!Objects.equals(held.noticeUrl(), wanted.noticeUrl())) {
            changed.add("noticeUrl");
        }
        if (held.defaultLawfulBasis() != wanted.defaultLawfulBasis()) {
            changed.add("defaultLawfulBasis");
        }
        return changed;
    }

    /** An absolute https link with a host: every notice and footer sends people to it. */
    private static String noticeUrlOf(String raw) {
        String url = blankToNull(raw);
        if (url == null) {
            return null;
        }
        if (!isHttpsLinkWithHost(url)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "noticeUrl", "Enter a link starting with https://");
        }
        return url;
    }

    private static boolean isHttpsLinkWithHost(String url) {
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getHost().contains(".");
        } catch (URISyntaxException invalid) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static CandidatePrivacySettingsResponse toResponse(CandidatePrivacySettings settings) {
        return new CandidatePrivacySettingsResponse(settings.controllerName(), settings.privacyContactEmail(),
                settings.noticeUrl(), settings.defaultLawfulBasis().value(), settings.isReadyForNotices());
    }
}
