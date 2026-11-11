package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.EmailTemplate;
import app.lightmove.api.outreach.repository.EmailTemplateRepository;
import app.lightmove.api.workspace.service.SharedEmailTemplateNames;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The audit log's names for shared email templates. */
@Component
@RequiredArgsConstructor
class SharedEmailTemplateNamesAdapter implements SharedEmailTemplateNames {

    private final EmailTemplateRepository templates;

    @Override
    public Map<UUID, String> namesOf(UUID workspaceId, Collection<UUID> templateIds) {
        if (templateIds.isEmpty()) {
            return Map.of();
        }
        return templates.findByWorkspaceIdAndIdInAndSharedAtIsNotNull(workspaceId, templateIds).stream()
                .collect(Collectors.toMap(EmailTemplate::getId, EmailTemplate::getName));
    }
}
