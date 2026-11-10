package app.lightmove.api.outreach.service;

import app.lightmove.api.common.constant.NoteFormat;
import app.lightmove.api.common.note.NoteText;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.dto.CreateEmailTemplateRequest;
import app.lightmove.api.outreach.dto.EmailTemplateOwnerDto;
import app.lightmove.api.outreach.dto.EmailTemplateResponse;
import app.lightmove.api.outreach.dto.EmailTemplateSharingRequest;
import app.lightmove.api.outreach.dto.EmailTemplatesResponse;
import app.lightmove.api.outreach.dto.UpdateEmailTemplateRequest;
import app.lightmove.api.outreach.model.EmailTemplate;
import app.lightmove.api.outreach.model.EmailTemplateContent;
import app.lightmove.api.outreach.model.SequenceTokens;
import app.lightmove.api.outreach.model.TokenUse;
import app.lightmove.api.outreach.repository.EmailTemplateRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Email templates in one workspace: the caller's own and the workspace's shared ones. Another person's personal template
 * reads as one that does not exist; a shared one is changed only by its owner or a {@code WORKSPACE_MANAGE} holder, and
 * every change to it is audited, since it reaches whoever applies it next.
 */
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    public static final int MAX_TEMPLATES = 100;

    static final String TARGET = "emailTemplate";

    private static final String SIGNATURE_TOKEN = "signature";

    private final EmailTemplateRepository templates;
    private final UserRepository users;
    private final WorkspaceAccess access;
    private final BookingPages bookingPages;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    public EmailTemplatesResponse list(UUID userId, UUID workspaceId) {
        List<EmailTemplate> visible = templates.findVisibleTo(workspaceId, userId);
        boolean isAdmin = access.holdsAction(userId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
        Map<UUID, User> owners = ownersOf(visible);
        return new EmailTemplatesResponse(
                visible.stream().map(template -> responseOf(template, userId, isAdmin, owners)).toList(),
                MAX_TEMPLATES, bookingPages.isOffered());
    }

    public EmailTemplateResponse create(UUID userId, UUID workspaceId, CreateEmailTemplateRequest request,
                                        HttpServletRequest httpRequest) {
        EmailTemplateContent content = contentOf(request.name(), request.subject(), request.body());
        EmailTemplate saved = transactions.execute(status -> {
            lockTemplatesOf(userId);
            refuseOverLimit(userId, workspaceId);
            EmailTemplate template = templates.saveAndFlush(
                    EmailTemplate.of(workspaceId, userId, content, request.isShared()));
            if (template.isShared()) {
                record(WorkspaceEventType.EMAIL_TEMPLATE_SHARED, template, userId, httpRequest);
            }
            return template;
        });
        return responseFor(saved, userId, workspaceId);
    }

    public EmailTemplateResponse update(UUID userId, UUID workspaceId, UUID templateId,
                                        UpdateEmailTemplateRequest request, HttpServletRequest httpRequest) {
        EmailTemplateContent content = contentOf(request.name(), request.subject(), request.body());
        EmailTemplate saved = transactions.execute(status -> {
            EmailTemplate template = editable(userId, workspaceId, templateId, request.version());
            template.write(content, userId);
            EmailTemplate written = templates.saveAndFlush(template);
            if (written.isShared()) {
                record(WorkspaceEventType.EMAIL_TEMPLATE_UPDATED, written, userId, httpRequest);
            }
            return written;
        });
        return responseFor(saved, userId, workspaceId);
    }

    public EmailTemplateResponse setSharing(UUID userId, UUID workspaceId, UUID templateId,
                                            EmailTemplateSharingRequest request, HttpServletRequest httpRequest) {
        boolean shared = request.shared();
        EmailTemplate saved = transactions.execute(status -> {
            EmailTemplate template = editable(userId, workspaceId, templateId, request.version());
            if (template.isShared() == shared) {
                return template;
            }
            template.share(shared, userId);
            EmailTemplate written = templates.saveAndFlush(template);
            record(shared ? WorkspaceEventType.EMAIL_TEMPLATE_SHARED : WorkspaceEventType.EMAIL_TEMPLATE_UNSHARED,
                    written, userId, httpRequest);
            return written;
        });
        return responseFor(saved, userId, workspaceId);
    }

    public void delete(UUID userId, UUID workspaceId, UUID templateId, long version, HttpServletRequest httpRequest) {
        transactions.executeWithoutResult(status -> {
            EmailTemplate template = editable(userId, workspaceId, templateId, version);
            if (template.isShared()) {
                record(WorkspaceEventType.EMAIL_TEMPLATE_DELETED, template, userId, httpRequest);
            }
            templates.delete(template);
        });
    }

    /** A personal copy owned by the caller, of any template they may apply. */
    public EmailTemplateResponse duplicate(UUID userId, UUID workspaceId, UUID templateId) {
        EmailTemplate saved = transactions.execute(status -> {
            EmailTemplate source = templates.findVisible(templateId, workspaceId, userId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));
            lockTemplatesOf(userId);
            refuseOverLimit(userId, workspaceId);
            return templates.saveAndFlush(EmailTemplate.copyOf(source, userId));
        });
        return responseFor(saved, userId, workspaceId);
    }

    /**
     * The template locked for a change by the caller at {@code version}: another person's personal one is missing, a
     * shared one is its owner's or an admin's to change, and a version older than the row's is stale.
     */
    private EmailTemplate editable(UUID userId, UUID workspaceId, UUID templateId, long version) {
        EmailTemplate template = templates.lockVisible(templateId, workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND));
        if (!template.isOwnedBy(userId)
                && !access.holdsAction(userId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE)) {
            throw ApiException.of(ErrorCode.EMAIL_TEMPLATE_NOT_YOURS);
        }
        if (template.getVersion() != version) {
            throw ApiException.of(ErrorCode.EMAIL_TEMPLATE_STALE);
        }
        return template;
    }

    /** Taken before counting, so two tabs saving at the limit decide one after the other. */
    private void lockTemplatesOf(UUID userId) {
        users.lockForOwnContentChange(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private void refuseOverLimit(UUID userId, UUID workspaceId) {
        if (templates.countByWorkspaceIdAndOwnerUserId(workspaceId, userId) >= MAX_TEMPLATES) {
            throw ApiException.of(ErrorCode.EMAIL_TEMPLATE_LIMIT_REACHED);
        }
    }

    private void record(WorkspaceEventType type, EmailTemplate template, UUID userId, HttpServletRequest httpRequest) {
        audit.event(type).actor(userId).workspace(template.getWorkspaceId()).target(TARGET, template.getId())
                .from(httpRequest)
                .record();
    }

    private EmailTemplateResponse responseFor(EmailTemplate template, UUID userId, UUID workspaceId) {
        boolean isAdmin = access.holdsAction(userId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
        return responseOf(template, userId, isAdmin, ownersOf(List.of(template)));
    }

    private Map<UUID, User> ownersOf(List<EmailTemplate> visible) {
        List<UUID> ownerIds = visible.stream().map(EmailTemplate::getOwnerUserId).distinct().toList();
        return ownerIds.isEmpty() ? Map.of()
                : users.findAllById(ownerIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static EmailTemplateResponse responseOf(EmailTemplate template, UUID userId, boolean isAdmin,
                                                    Map<UUID, User> owners) {
        boolean mine = template.isOwnedBy(userId);
        User owner = owners.get(template.getOwnerUserId());
        return new EmailTemplateResponse(template.getId(), template.getName(), template.getSubject(),
                template.getBody(), NoteText.plainTextOf(template.getBody(), NoteFormat.MARKDOWN),
                template.isShared(), mine, mine || template.isShared() && isAdmin,
                new EmailTemplateOwnerDto(template.getOwnerUserId(), owner == null ? null : owner.getFullName(),
                        owner == null ? null : owner.getAvatarUrl()),
                template.getUpdatedAt(), template.getVersion());
    }

    /** Trimmed, a blank subject as none, and a token the composer could not fill refused on its field. */
    EmailTemplateContent contentOf(String name, String subject, String body) {
        String keptSubject = subject == null || subject.isBlank() ? null : subject.strip();
        if (keptSubject != null && (keptSubject.contains("\n") || keptSubject.contains("\r"))) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "subject", "Keep the subject to one line");
        }
        refuseTokensIn("subject", keptSubject);
        refuseTokensIn("body", body);
        return new EmailTemplateContent(name.strip(), keptSubject, body.strip());
    }

    private void refuseTokensIn(String field, String text) {
        List<String> vocabulary = new ArrayList<>(SequenceTokens.PROFILE_TOKENS);
        if (bookingPages.isOffered()) {
            vocabulary.add(SequenceTokens.BOOKING_LINK);
        }
        for (TokenUse use : SequenceTokens.usesIn(text)) {
            if (SIGNATURE_TOKEN.equals(use.name())) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, field,
                        "Leave {{signature}} out: Include signature adds yours when you send");
            }
            if (SequenceTokens.OPENER.equals(use.name())) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, field,
                        "{{opener}} is written for each person in a sequence, so a template cannot use it");
            }
            if (!vocabulary.contains(use.name())) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, field,
                        "This uses a variable Uncava cannot fill. Insert variables from the Insert bar");
            }
            if (SequenceTokens.BOOKING_LINK.equals(use.name()) && Objects.nonNull(use.fallback())) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, field,
                        "A booking link cannot have a fallback");
            }
        }
    }
}
