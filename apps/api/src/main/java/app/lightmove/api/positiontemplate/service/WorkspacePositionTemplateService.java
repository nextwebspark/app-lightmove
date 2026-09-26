package app.lightmove.api.positiontemplate.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.positiontemplate.constant.PositionTemplateOrigin;
import app.lightmove.api.positiontemplate.constant.TemplateImportAction;
import app.lightmove.api.positiontemplate.dto.PositionTemplateDetail;
import app.lightmove.api.positiontemplate.dto.PositionTemplateOverview;
import app.lightmove.api.positiontemplate.dto.PositionTemplateWriteRequest;
import app.lightmove.api.positiontemplate.dto.TemplateImportResponse;
import app.lightmove.api.positiontemplate.model.HiddenLibraryTemplate;
import app.lightmove.api.positiontemplate.model.ImportedTemplate;
import app.lightmove.api.positiontemplate.model.PlannedTemplateImport;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplateDraft;
import app.lightmove.api.positiontemplate.repository.HiddenLibraryTemplateRepository;
import app.lightmove.api.positiontemplate.repository.PositionTemplateRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * One firm's role templates: its copies, its own, and the library templates it hid. Every write lands
 * on the caller's own workspace rows; the library is read-only from here.
 */
@Service
@RequiredArgsConstructor
public class WorkspacePositionTemplateService {

    private final PositionTemplateRepository templates;
    private final HiddenLibraryTemplateRepository hiddenTemplates;
    private final PositionTemplateValidator validator;
    private final PositionTemplateExchange exchange;
    private final UserRepository users;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    /** The firm's own rows first, then every active library template it has not copied, hidden ones included. */
    @Transactional(readOnly = true)
    public List<PositionTemplateOverview> list(UUID workspaceId) {
        WorkspaceCatalog catalog = catalogOf(workspaceId);
        Map<UUID, String> reviserNames = users.findAllById(catalog.own().values().stream()
                        .map(PositionTemplate::getRevisedBy).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        List<PositionTemplateOverview> rows = new ArrayList<>();
        catalog.own().values().forEach(template -> rows.add(overviewOf(template, catalog, reviserNames)));
        catalog.library().values().stream()
                .filter(template -> template.isActive() && !catalog.own().containsKey(template.getCode()))
                .forEach(template -> rows.add(overviewOf(template, catalog, reviserNames)));
        return rows;
    }

    @Transactional(readOnly = true)
    public PositionTemplateDetail get(UUID workspaceId, String code) {
        WorkspaceCatalog catalog = catalogOf(workspaceId);
        return detailOf(catalog.resolve(code), catalog);
    }

    /** Its code avoids the library's too: sharing one would make it a copy of that template. */
    @Transactional
    public PositionTemplateDetail create(UUID userId, UUID workspaceId, PositionTemplateWriteRequest request,
                                         HttpServletRequest httpRequest) {
        PositionTemplateDraft draft = validator.requireValid(request.draft());
        WorkspaceCatalog catalog = catalogOf(workspaceId);
        Set<String> taken = new HashSet<>(catalog.own().keySet());
        taken.addAll(catalog.library().keySet());
        PositionTemplate template = templates.saveAndFlush(PositionTemplate.ownedBy(workspaceId,
                PositionTemplateCodes.unusedCode(draft.title(), taken), draft, userId));
        record(WorkspaceEventType.POSITION_TEMPLATE_CREATED, userId, workspaceId, template.getCode(), httpRequest);
        return detailOf(template, catalog);
    }

    /**
     * The first save of a library template takes the firm's copy, checked against the library row's
     * version so a copy is never taken from content the editor did not see.
     */
    @Transactional
    public PositionTemplateDetail save(UUID userId, UUID workspaceId, String code,
                                       PositionTemplateWriteRequest request, HttpServletRequest httpRequest) {
        PositionTemplateDraft draft = validator.requireValid(request.draft());
        WorkspaceCatalog catalog = catalogOf(workspaceId);
        PositionTemplate current = catalog.resolve(code);
        PositionTemplateValidator.requireVersion(current, request.version());

        PositionTemplate saved = current;
        WorkspaceEventType event = WorkspaceEventType.POSITION_TEMPLATE_UPDATED;
        if (current.isSharedLibrary()) {
            saved = templates.save(PositionTemplate.customisationOf(current, workspaceId, draft, userId));
            showAgain(workspaceId, code);
            event = WorkspaceEventType.POSITION_TEMPLATE_CUSTOMISED;
        } else {
            current.revise(draft, userId);
        }
        templates.flush();
        record(event, userId, workspaceId, code, httpRequest);
        return detailOf(saved, catalog);
    }

    /**
     * Resets a copy to the library's template, or deletes a template the firm wrote — only at the version
     * the caller saw, since either discards content outright.
     */
    @Transactional
    public void remove(UUID userId, UUID workspaceId, String code, long version, HttpServletRequest httpRequest) {
        PositionTemplate own = templates.findWorkspaceTemplate(workspaceId, code)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        PositionTemplateValidator.requireVersion(own, version);
        templates.delete(own);
        record(own.isCustomisation() ? WorkspaceEventType.POSITION_TEMPLATE_RESET
                : WorkspaceEventType.POSITION_TEMPLATE_DELETED, userId, workspaceId, code, httpRequest);
    }

    @Transactional
    public PositionTemplateDetail setHidden(UUID userId, UUID workspaceId, String code, boolean hidden,
                                            HttpServletRequest httpRequest) {
        WorkspaceCatalog catalog = catalogOf(workspaceId);
        PositionTemplate library = catalog.library().get(code);
        if (library == null || !library.isActive()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        if (catalog.own().containsKey(code)) {
            throw ApiException.userFacing(ErrorCode.CONFLICT,
                    "Reset your firm's copy of this template before hiding it");
        }
        if (hidden && PositionTemplateService.isFallback(library)) {
            throw ApiException.of(ErrorCode.TEMPLATE_FALLBACK_REQUIRED);
        }

        Set<String> hiddenCodes = new HashSet<>(catalog.hidden());
        boolean wasHidden = hiddenCodes.contains(code);
        if (hidden && !wasHidden) {
            hiddenTemplates.save(HiddenLibraryTemplate.of(workspaceId, code, userId));
            hiddenCodes.add(code);
            record(WorkspaceEventType.POSITION_TEMPLATE_HIDDEN, userId, workspaceId, code, httpRequest);
        } else if (!hidden && wasHidden) {
            showAgain(workspaceId, code);
            hiddenCodes.remove(code);
            record(WorkspaceEventType.POSITION_TEMPLATE_SHOWN, userId, workspaceId, code, httpRequest);
        }
        return detailOf(library, new WorkspaceCatalog(catalog.own(), catalog.library(), hiddenCodes));
    }

    @Transactional(readOnly = true)
    public byte[] export(UUID workspaceId) {
        return exchange.write(templates.findAllVisibleTo(workspaceId));
    }

    public byte[] schema() {
        return exchange.schema();
    }

    /** Neither import call parses the file inside a transaction, so no pooled connection is held across it. */
    public TemplateImportResponse previewImport(UUID workspaceId, MultipartFile file) {
        List<ImportedTemplate> imported = exchange.read(file);
        return TemplateImportResponse.of(false, plan(catalogOf(workspaceId), imported));
    }

    public TemplateImportResponse commitImport(UUID userId, UUID workspaceId, MultipartFile file,
                                               HttpServletRequest httpRequest) {
        List<ImportedTemplate> imported = exchange.read(file);
        return transactions.execute(status -> commit(userId, workspaceId, imported, httpRequest));
    }

    private TemplateImportResponse commit(UUID userId, UUID workspaceId, List<ImportedTemplate> entries,
                                          HttpServletRequest httpRequest) {
        List<PlannedTemplateImport> plan = plan(catalogOf(workspaceId), entries);
        if (plan.stream().anyMatch(step -> step.action() == TemplateImportAction.INVALID)) {
            throw ApiException.of(ErrorCode.TEMPLATE_IMPORT_INVALID);
        }
        for (PlannedTemplateImport step : plan) {
            ImportedTemplate imported = step.template();
            switch (step.action()) {
                case CREATE -> templates.save(
                        PositionTemplate.ownedBy(workspaceId, imported.code(), imported.draft(), userId));
                case CUSTOMISE -> {
                    templates.save(PositionTemplate.customisationOf(step.target(), workspaceId,
                            imported.draft(), userId));
                    showAgain(workspaceId, imported.code());
                }
                case UPDATE -> step.target().revise(imported.draft(), userId);
                case UNCHANGED, INVALID -> { }
            }
        }
        audit.event(WorkspaceEventType.POSITION_TEMPLATES_IMPORTED)
                .actor(userId).workspace(workspaceId).target("workspace", workspaceId).from(httpRequest)
                .detail("created", PlannedTemplateImport.count(plan, TemplateImportAction.CREATE))
                .detail("customised", PlannedTemplateImport.count(plan, TemplateImportAction.CUSTOMISE))
                .detail("updated", PlannedTemplateImport.count(plan, TemplateImportAction.UPDATE))
                .record();
        return TemplateImportResponse.of(true, plan);
    }

    /**
     * An identical template is left alone, so re-importing an export forks nothing. A library code is
     * customised even when archived: a CREATE would shadow it untracked the day it is restored.
     */
    private static List<PlannedTemplateImport> plan(WorkspaceCatalog catalog, List<ImportedTemplate> imported) {
        return imported.stream().map(template -> {
            if (!template.isValid()) {
                return new PlannedTemplateImport(template, TemplateImportAction.INVALID, null);
            }
            PositionTemplate own = catalog.own().get(template.code());
            if (own != null) {
                return new PlannedTemplateImport(template, own.toDraft().equals(template.draft())
                        ? TemplateImportAction.UNCHANGED : TemplateImportAction.UPDATE, own);
            }
            PositionTemplate library = catalog.library().get(template.code());
            if (library != null) {
                return new PlannedTemplateImport(template, library.toDraft().equals(template.draft())
                        ? TemplateImportAction.UNCHANGED : TemplateImportAction.CUSTOMISE, library);
            }
            return new PlannedTemplateImport(template, TemplateImportAction.CREATE, null);
        }).toList();
    }

    private void showAgain(UUID workspaceId, String code) {
        hiddenTemplates.deleteByWorkspaceIdAndCode(workspaceId, code);
    }

    private WorkspaceCatalog catalogOf(UUID workspaceId) {
        return new WorkspaceCatalog(byCode(templates.findOwnedBy(workspaceId)), byCode(templates.findLibrary()),
                hiddenTemplates.findByWorkspaceId(workspaceId).stream()
                        .map(HiddenLibraryTemplate::getCode)
                        .collect(Collectors.toSet()));
    }

    private PositionTemplateOverview overviewOf(PositionTemplate template, WorkspaceCatalog catalog,
                                                Map<UUID, String> reviserNames) {
        String revisedBy = template.isSharedLibrary() ? null : reviserNames.get(template.getRevisedBy());
        return new PositionTemplateOverview(template.getCode(), template.getTitle(), template.getDiscipline(),
                template.getSeniority(), template.getSummary(), List.copyOf(template.getKeywords()),
                catalog.originOf(template), template.isActive(), PositionTemplateService.isFallback(template), catalog.isBehind(template),
                null, template.getRevisedAt(), revisedBy);
    }

    private PositionTemplateDetail detailOf(PositionTemplate template, WorkspaceCatalog catalog) {
        String revisedBy = template.isSharedLibrary() || template.getRevisedBy() == null ? null
                : users.findById(template.getRevisedBy()).map(User::getFullName).orElse(null);
        return new PositionTemplateDetail(template.getCode(), template.getTitle(), template.getDiscipline(),
                template.getSeniority(), template.getSummary(), List.copyOf(template.getKeywords()),
                template.getBody(), catalog.originOf(template), template.isActive(), PositionTemplateService.isFallback(template),
                catalog.isBehind(template), null, template.getVersion(), template.getRevisedAt(), revisedBy);
    }

    private void record(WorkspaceEventType event, UUID userId, UUID workspaceId, String code,
                        HttpServletRequest httpRequest) {
        audit.event(event).actor(userId).workspace(workspaceId).target("position_template", code)
                .from(httpRequest).record();
    }

    private static Map<String, PositionTemplate> byCode(List<PositionTemplate> rows) {
        Map<String, PositionTemplate> byCode = new LinkedHashMap<>();
        rows.forEach(row -> byCode.put(row.getCode(), row));
        return byCode;
    }

    /** Read once per request. */
    private record WorkspaceCatalog(Map<String, PositionTemplate> own, Map<String, PositionTemplate> library,
                                    Set<String> hidden) {

        PositionTemplate resolve(String code) {
            PositionTemplate firms = own.get(code);
            if (firms != null) {
                return firms;
            }
            PositionTemplate shared = library.get(code);
            if (shared != null && shared.isActive()) {
                return shared;
            }
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }

        PositionTemplateOrigin originOf(PositionTemplate template) {
            if (!template.isSharedLibrary()) {
                return template.isCustomisation() ? PositionTemplateOrigin.CUSTOMISED : PositionTemplateOrigin.OWN;
            }
            return hidden.contains(template.getCode()) ? PositionTemplateOrigin.HIDDEN : PositionTemplateOrigin.LIBRARY;
        }

        boolean isBehind(PositionTemplate template) {
            PositionTemplate source = template.isCustomisation() ? library.get(template.getCode()) : null;
            return source != null && template.isBehind(source);
        }
    }
}
