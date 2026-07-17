package app.lightmove.api.company.service;

import app.lightmove.api.company.constant.TaxonomyType;
import app.lightmove.api.company.dto.CompanyDtos.CompanySearchFilter;
import app.lightmove.api.company.dto.CompanyDtos.TaxonomyMatch;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * One {@link ChatClient} call that maps a free-text search query, plus the taxonomy candidates
 * {@link TaxonomySearchService} shortlisted, onto a structured {@link CompanySearchFilter}.
 *
 * <p>Structured output goes directly into {@code CompanySearchFilter} rather than a separate internal
 * intent record — the response DTO already has exactly the shape this call needs to produce.
 *
 * <p>Hand-written constructor (not {@code @RequiredArgsConstructor}): the prompt resource needs
 * {@code @Value} on a constructor parameter, which Lombok's generated constructor doesn't carry over.
 */
@Service
@Slf4j
public class CompanyIntentExtractor {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    public CompanyIntentExtractor(ChatClient.Builder chatClientBuilder,
                                   @Value("classpath:prompts/company-search-intent.st") Resource promptResource) {
        this.chatClient = chatClientBuilder.build();
        this.promptTemplate = new PromptTemplate(promptResource);
    }

    public CompanySearchFilter extract(String query, List<TaxonomyMatch> candidates) {
        List<String> primaryCandidates = candidates.stream()
                .filter(c -> c.type() == TaxonomyType.PRIMARY_TYPE)
                .map(TaxonomyMatch::name)
                .toList();
        List<String> tagCandidates = candidates.stream()
                .filter(c -> c.type() == TaxonomyType.TAG)
                .map(TaxonomyMatch::name)
                .toList();

        // Joined into a plain comma-separated string, not passed as raw List/Set values: Spring AI's
        // PromptTemplate renders via StringTemplate (ST4), which concatenates multi-valued attributes
        // with NO separator unless the template itself declares one (`{list; separator=", "}`) — passing
        // the collections directly would jam every candidate name together unreadably.
        String rendered = promptTemplate.render(Map.of(
                "query", query,
                "primaryIndustryCandidates", String.join(", ", primaryCandidates),
                "industryTagCandidates", String.join(", ", tagCandidates)));

        CompanySearchFilter raw = chatClient.prompt(rendered).call().entity(CompanySearchFilter.class);

        return validateAgainstCandidates(raw, Set.copyOf(primaryCandidates), Set.copyOf(tagCandidates));
    }

    /**
     * Defense in depth, independent of the prompt's own "never invent a value" instruction: a
     * generative response is never trusted to feed a {@code WHERE ... IN (...)} unchecked. Any
     * {@code primaryTypes}/{@code tags} entry the model returned that isn't actually one of the
     * candidates it was given is dropped, not passed through.
     */
    private CompanySearchFilter validateAgainstCandidates(CompanySearchFilter raw,
                                                           Set<String> primaryCandidates,
                                                           Set<String> tagCandidates) {
        List<String> primaryTypes = filterToCandidates(raw.primaryTypes(), primaryCandidates, "primary type");
        List<String> tags = filterToCandidates(raw.tags(), tagCandidates, "tag");

        return new CompanySearchFilter(primaryTypes, tags, raw.country(), raw.employeeCountMin(),
                raw.employeeCountMax(), raw.revenueMinUsd(), raw.revenueMaxUsd());
    }

    private List<String> filterToCandidates(List<String> values, Set<String> candidates, String label) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> valid = values.stream().filter(candidates::contains).toList();
        if (valid.size() != values.size()) {
            List<String> dropped = values.stream().filter(v -> !candidates.contains(v)).toList();
            log.warn("Dropped hallucinated {}(s) not in the candidate set: {}", label, dropped);
        }
        return valid;
    }
}
