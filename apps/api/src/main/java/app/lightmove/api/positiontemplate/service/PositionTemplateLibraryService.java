package app.lightmove.api.positiontemplate.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.positiontemplate.constant.TemplateImportAction;
import app.lightmove.api.positiontemplate.dto.PositionTemplateDetail;
import app.lightmove.api.positiontemplate.dto.PositionTemplateOverview;
import app.lightmove.api.positiontemplate.dto.PositionTemplateWriteRequest;
import app.lightmove.api.positiontemplate.dto.TemplateImportResponse;
import app.lightmove.api.positiontemplate.model.ImportedTemplate;
import app.lightmove.api.positiontemplate.model.PlannedTemplateImport;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplateDraft;
import app.lightmove.api.positiontemplate.model.TemplateCodeCount;
import app.lightmove.api.positiontemplate.repository.PositionTemplateRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * The LightMove library as a super admin edits it. A change reaches every workspace that has neither
 * copied nor hidden the template, from the next mandate it drafts — never a brief already written.
 * Nothing here reads a workspace's data beyond counting how many firms keep their own copy.
 */
@Service
@RequiredArgsConstructor
public class PositionTemplateLibraryService {

    private static final int SORT_STEP = 10;

    private final PositionTemplateRepository templates;
    private final PositionTemplateValidator validator;
    private final PositionTemplateExchange exchange;
    private final UserRepository users;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    @Transactional(readOnly = true)
    public List<PositionTemplateOverview> list() {
        List<PositionTemplate> library = templates.findLibrary();
        Map<String, Long> copies = templates.countWorkspaceTemplatesByCode().stream()
                .collect(Collectors.toMap(TemplateCodeCount::code, TemplateCodeCount::count));
        Map<UUID, String> reviserNames = users.findAllById(library.stream()
                        .map(PositionTemplate::getRevisedBy).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return library.stream()
                .map(template -> new PositionTemplateOverview(template.getCode(), template.getTitle(),
                        template.getDiscipline(), template.getSeniority(), template.getSummary(),
                        List.copyOf(template.getKeywords()), null, template.isActive(), PositionTemplateService.isFallback(template), false,
                        copies.getOrDefault(template.getCode(), 0L), template.getRevisedAt(),
                        reviserNames.get(template.getRevisedBy())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PositionTemplateDetail get(String code) {
        return detailOf(require(code));
    }

    @Transactional
    public PositionTemplateDetail create(UUID userId, PositionTemplateWriteRequest request,
                                         HttpServletRequest httpRequest) {
        PositionTemplateDraft draft = validator.requireValid(request.draft());
        Set<String> taken = templates.findLibraryCodes();
        PositionTemplate template = templates.saveAndFlush(PositionTemplate.forLibrary(
                PositionTemplateCodes.unusedCode(draft.title(), taken), draft,
                templates.findLastLibrarySortOrder() + SORT_STEP, userId));
        record(PlatformEventType.POSITION_TEMPLATE_LIBRARY_CREATED, userId, template.getCode(), httpRequest);
        return detailOf(template);
    }

    @Transactional
    public PositionTemplateDetail update(UUID userId, String code, PositionTemplateWriteRequest request,
                                         HttpServletRequest httpRequest) {
        PositionTemplateDraft draft = validator.requireValid(request.draft());
        PositionTemplate template = require(code);
        PositionTemplateValidator.requireVersion(template, request.version());
        template.revise(draft, userId);
        // Flushed so the version handed back is the one the next save must quote.
        templates.flush();
        record(PlatformEventType.POSITION_TEMPLATE_LIBRARY_UPDATED, userId, code, httpRequest);
        return detailOf(template);
    }

    @Transactional
    public PositionTemplateDetail setActive(UUID userId, String code, boolean active,
                                            HttpServletRequest httpRequest) {
        PositionTemplate template = require(code);
        if (!active && PositionTemplateService.isFallback(template)) {
            throw ApiException.of(ErrorCode.TEMPLATE_FALLBACK_REQUIRED);
        }
        if (template.isActive() != active) {
            template.setActive(active);
            templates.flush();
            record(active ? PlatformEventType.POSITION_TEMPLATE_LIBRARY_RESTORED
                    : PlatformEventType.POSITION_TEMPLATE_LIBRARY_ARCHIVED, userId, code, httpRequest);
        }
        return detailOf(template);
    }

    @Transactional(readOnly = true)
    public byte[] export() {
        return exchange.write(templates.findLibrary());
    }

    public byte[] schema() {
        return exchange.schema();
    }

    /**
     * Neither import call parses the file inside a transaction: reading it touches no row, and a pooled
     * connection held across a megabyte of JSON is one the rest of the app is waiting for.
     */
    public TemplateImportResponse previewImport(MultipartFile file) {
        List<ImportedTemplate> imported = exchange.read(file);
        return TemplateImportResponse.of(false, plan(imported));
    }

    public TemplateImportResponse commitImport(UUID userId, MultipartFile file, HttpServletRequest httpRequest) {
        List<ImportedTemplate> imported = exchange.read(file);
        return transactions.execute(status -> commit(userId, imported, httpRequest));
    }

    private TemplateImportResponse commit(UUID userId, List<ImportedTemplate> entries,
                                          HttpServletRequest httpRequest) {
        List<PlannedTemplateImport> plan = plan(entries);
        if (plan.stream().anyMatch(step -> step.action() == TemplateImportAction.INVALID)) {
            throw ApiException.of(ErrorCode.TEMPLATE_IMPORT_INVALID);
        }
        int sortOrder = templates.findLastLibrarySortOrder();
        for (PlannedTemplateImport step : plan) {
            ImportedTemplate imported = step.template();
            if (step.action() == TemplateImportAction.CREATE) {
                sortOrder += SORT_STEP;
                templates.save(PositionTemplate.forLibrary(imported.code(), imported.draft(), sortOrder, userId));
            } else if (step.action() == TemplateImportAction.UPDATE) {
                step.target().revise(imported.draft(), userId);
            }
        }
        audit.event(PlatformEventType.POSITION_TEMPLATE_LIBRARY_IMPORTED)
                .actor(userId).target("position_template_library", null).from(httpRequest)
                .detail("created", PlannedTemplateImport.count(plan, TemplateImportAction.CREATE))
                .detail("updated", PlannedTemplateImport.count(plan, TemplateImportAction.UPDATE))
                .record();
        return TemplateImportResponse.of(true, plan);
    }

    private List<PlannedTemplateImport> plan(List<ImportedTemplate> imported) {
        Map<String, PositionTemplate> library = templates.findLibrary().stream()
                .collect(Collectors.toMap(PositionTemplate::getCode, Function.identity()));
        return imported.stream().map(template -> {
            if (!template.isValid()) {
                return new PlannedTemplateImport(template, TemplateImportAction.INVALID, null);
            }
            PositionTemplate existing = library.get(template.code());
            if (existing == null) {
                return new PlannedTemplateImport(template, TemplateImportAction.CREATE, null);
            }
            TemplateImportAction action = existing.toDraft().equals(template.draft())
                    ? TemplateImportAction.UNCHANGED : TemplateImportAction.UPDATE;
            return new PlannedTemplateImport(template, action, existing);
        }).toList();
    }

    private PositionTemplateDetail detailOf(PositionTemplate template) {
        return new PositionTemplateDetail(template.getCode(), template.getTitle(), template.getDiscipline(),
                template.getSeniority(), template.getSummary(), List.copyOf(template.getKeywords()),
                template.getBody(), null, template.isActive(), PositionTemplateService.isFallback(template), false,
                templates.countWorkspaceTemplatesCoded(template.getCode()), template.getVersion(),
                template.getRevisedAt(), nameOf(template.getRevisedBy()));
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : users.findById(userId).map(User::getFullName).orElse(null);
    }

    private PositionTemplate require(String code) {
        return templates.findLibraryTemplate(code).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private void record(PlatformEventType event, UUID userId, String code, HttpServletRequest httpRequest) {
        audit.event(event).actor(userId).target("position_template", code).from(httpRequest).record();
    }
}
