package app.lightmove.api.screentext.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.screentext.constant.ScreenTextLocale;
import app.lightmove.api.screentext.dto.ScreenTextBundle;
import app.lightmove.api.screentext.dto.ScreenTextDraftRequest;
import app.lightmove.api.screentext.dto.ScreenTextEditorOverview;
import app.lightmove.api.screentext.dto.ScreenTextEntryView;
import app.lightmove.api.screentext.dto.ScreenTextPublishRequest;
import app.lightmove.api.screentext.dto.ScreenTextReleaseView;
import app.lightmove.api.screentext.dto.ScreenTextScreenSummary;
import app.lightmove.api.screentext.model.ScreenTextCatalogEntry;
import app.lightmove.api.screentext.model.ScreenTextDraft;
import app.lightmove.api.screentext.model.ScreenTextRelease;
import app.lightmove.api.screentext.repository.ScreenTextDraftRepository;
import app.lightmove.api.screentext.repository.ScreenTextReleaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Screen text as a super admin edits it: drafts per key, published together as one release, rolled back the same way. */
@Service
@RequiredArgsConstructor
public class ScreenTextEditorService {

    private static final int RELEASES_LISTED = 50;

    private final ScreenTextCatalog catalog;
    private final ScreenTextRules rules;
    private final ScreenTextBundles bundles;
    private final ScreenTextDraftRepository drafts;
    private final ScreenTextReleaseRepository releases;
    private final UserRepository users;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ScreenTextEditorOverview overview(ScreenTextLocale locale) {
        Map<String, String> published = catalog.servable(latestEntries(locale));
        Set<String> drafted = draftsByKey(locale).keySet();
        Map<String, List<ScreenTextCatalogEntry>> byScreen = catalog.entries().stream()
                .collect(Collectors.groupingBy(ScreenTextCatalogEntry::screen));
        List<ScreenTextScreenSummary> screens = catalog.screens().stream()
                .map(screen -> {
                    List<ScreenTextCatalogEntry> entries = byScreen.getOrDefault(screen.id(), List.of());
                    return new ScreenTextScreenSummary(screen.id(), screen.title(), entries.size(),
                            count(entries, published.keySet()), count(entries, drafted));
                })
                .toList();
        int latest = releases.findFirstByLocaleOrderByNumberDesc(locale.tag()).map(ScreenTextRelease::getNumber).orElse(0);
        return new ScreenTextEditorOverview(locale.tag(), latest, drafted.size(), screens);
    }

    @Transactional(readOnly = true)
    public List<ScreenTextEntryView> entries(ScreenTextLocale locale, String screen) {
        Map<String, String> stored = latestEntries(locale);
        Map<String, String> published = catalog.servable(stored);
        Map<String, ScreenTextDraft> drafted = draftsByKey(locale);
        Map<UUID, String> names = namesOf(drafted.values().stream().map(ScreenTextDraft::getUpdatedBy).toList());
        return catalog.entries().stream()
                .filter(entry -> screen == null || screen.isBlank() || entry.screen().equals(screen))
                .map(entry -> view(entry, stored, published, drafted.get(entry.key()), names))
                .toList();
    }

    /** A draft equal to what is already published is no change at all, so it removes the draft rather than keeping one. */
    @Transactional
    public ScreenTextEntryView saveDraft(UUID userId, ScreenTextLocale locale, String key, ScreenTextDraftRequest request) {
        ScreenTextCatalogEntry entry = requireEntry(key);
        rules.check(entry, request.text());
        Optional<ScreenTextDraft> existing = drafts.findByLocaleAndTextKey(locale.tag(), key);
        long readVersion = existing.map(ScreenTextDraft::getVersion).orElse(-1L);
        if (!Objects.equals(request.version() == null ? -1L : request.version(), readVersion)) {
            throw ApiException.of(ErrorCode.SCREEN_TEXT_STALE);
        }
        String effective = catalog.servable(latestEntries(locale)).getOrDefault(key, entry.defaultText());
        if (effective.equals(request.text())) {
            existing.ifPresent(drafts::delete);
        } else {
            existing.ifPresentOrElse(draft -> draft.revise(request.text(), userId),
                    () -> drafts.save(ScreenTextDraft.of(locale.tag(), key, request.text(), userId)));
        }
        drafts.flush();
        return entries(locale, entry.screen()).stream()
                .filter(view -> view.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void discardDraft(ScreenTextLocale locale, String key) {
        requireEntry(key);
        drafts.findByLocaleAndTextKey(locale.tag(), key).ifPresent(drafts::delete);
    }

    /** What the screens would read if the drafts were published now, for the editor's own preview. */
    @Transactional(readOnly = true)
    public ScreenTextBundle preview(ScreenTextLocale locale) {
        Map<String, String> next = applyDrafts(latestEntries(locale), draftsByKey(locale).values());
        int latest = releases.findFirstByLocaleOrderByNumberDesc(locale.tag()).map(ScreenTextRelease::getNumber).orElse(0);
        return bundles.of(locale, latest, next);
    }

    @Transactional
    public ScreenTextReleaseView publish(UUID userId, ScreenTextLocale locale, ScreenTextPublishRequest request,
                                         HttpServletRequest httpRequest) {
        lockPublishing(locale);
        Collection<ScreenTextDraft> pending = draftsByKey(locale).values();
        if (pending.isEmpty()) {
            throw ApiException.of(ErrorCode.SCREEN_TEXT_NOTHING_TO_PUBLISH);
        }
        Map<String, String> current = latestEntries(locale);
        Map<String, String> next = applyDrafts(current, pending);
        List<String> changed = changedKeys(current, next);
        ScreenTextRelease release = releases.save(ScreenTextRelease.of(locale.tag(), nextNumber(locale), next, changed,
                blankToNull(request == null ? null : request.note()), null, userId, clock.instant()));
        drafts.deleteAll(pending);
        recorded(PlatformEventType.SCREEN_TEXT_PUBLISHED, userId, release, httpRequest);
        return viewOf(release, namesOf(List.of(userId)));
    }

    @Transactional
    public ScreenTextReleaseView restore(UUID userId, ScreenTextLocale locale, int number, HttpServletRequest httpRequest) {
        lockPublishing(locale);
        ScreenTextRelease source = releases.findByLocaleAndNumber(locale.tag(), number)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Map<String, String> current = latestEntries(locale);
        ScreenTextRelease release = releases.save(ScreenTextRelease.of(locale.tag(), nextNumber(locale),
                source.getEntries(), changedKeys(current, source.getEntries()), null, number, userId, clock.instant()));
        recorded(PlatformEventType.SCREEN_TEXT_RESTORED, userId, release, httpRequest);
        return viewOf(release, namesOf(List.of(userId)));
    }

    @Transactional(readOnly = true)
    public List<ScreenTextReleaseView> releases(ScreenTextLocale locale) {
        List<ScreenTextRelease> listed =
                releases.findAllByLocaleOrderByNumberDesc(locale.tag(), PageRequest.of(0, RELEASES_LISTED));
        Map<UUID, String> names = namesOf(listed.stream().map(ScreenTextRelease::getPublishedBy).toList());
        return listed.stream().map(release -> viewOf(release, names)).toList();
    }

    private ScreenTextCatalogEntry requireEntry(String key) {
        return catalog.entry(key).orElseThrow(() -> ApiException.of(ErrorCode.SCREEN_TEXT_KEY_UNKNOWN));
    }

    /** Two publishes racing would both read release N and both write N+1; the second waits for the first instead. */
    private void lockPublishing(ScreenTextLocale locale) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", "screen-text:" + locale.tag());
    }

    private Map<String, String> applyDrafts(Map<String, String> current, Collection<ScreenTextDraft> pending) {
        Map<String, String> next = new LinkedHashMap<>(current);
        for (ScreenTextDraft draft : pending) {
            Optional<ScreenTextCatalogEntry> entry = catalog.entry(draft.getTextKey());
            if (entry.isEmpty() || entry.get().defaultText().equals(draft.getText())) {
                next.remove(draft.getTextKey());
            } else {
                next.put(draft.getTextKey(), draft.getText());
            }
        }
        return next;
    }

    private static List<String> changedKeys(Map<String, String> before, Map<String, String> after) {
        Set<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        return keys.stream().filter(key -> !Objects.equals(before.get(key), after.get(key))).toList();
    }

    private int nextNumber(ScreenTextLocale locale) {
        return releases.findFirstByLocaleOrderByNumberDesc(locale.tag()).map(ScreenTextRelease::getNumber).orElse(0) + 1;
    }

    private Map<String, String> latestEntries(ScreenTextLocale locale) {
        return releases.findFirstByLocaleOrderByNumberDesc(locale.tag())
                .map(ScreenTextRelease::getEntries)
                .orElse(Map.of());
    }

    private Map<String, ScreenTextDraft> draftsByKey(ScreenTextLocale locale) {
        return drafts.findAllByLocale(locale.tag()).stream()
                .collect(Collectors.toMap(ScreenTextDraft::getTextKey, Function.identity()));
    }

    private void recorded(PlatformEventType event, UUID userId, ScreenTextRelease release, HttpServletRequest request) {
        audit.event(event).actor(userId).target("screen_text_release", release.getNumber())
                .detail("locale", release.getLocale())
                .detail("changedKeys", release.getChangedKeys().size())
                .detailIfPresent("restoredFrom", release.getRestoredFrom())
                .from(request)
                .record();
        ScreenTextLocale locale = ScreenTextLocale.of(release.getLocale());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bundles.evict(locale);
            }
        });
    }

    private ScreenTextEntryView view(ScreenTextCatalogEntry entry, Map<String, String> stored,
                                     Map<String, String> published, ScreenTextDraft draft, Map<UUID, String> names) {
        return new ScreenTextEntryView(entry.key(), entry.screen(), entry.kind(), entry.where(), entry.defaultText(),
                entry.placeholders(), entry.maxLength(), published.get(entry.key()),
                stored.containsKey(entry.key()) && !published.containsKey(entry.key())
                        && !stored.get(entry.key()).equals(entry.defaultText()),
                draft == null ? null : draft.getText(),
                draft == null ? null : draft.getVersion(),
                draft == null ? null : names.get(draft.getUpdatedBy()),
                draft == null ? null : draft.getUpdatedAt());
    }

    private ScreenTextReleaseView viewOf(ScreenTextRelease release, Map<UUID, String> names) {
        return new ScreenTextReleaseView(release.getNumber(), release.getNote(), release.getRestoredFrom(),
                release.getChangedKeys(), release.getEntries().size(), names.get(release.getPublishedBy()),
                release.getPublishedAt());
    }

    private Map<UUID, String> namesOf(List<UUID> userIds) {
        List<UUID> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private static int count(List<ScreenTextCatalogEntry> entries, Set<String> keys) {
        return (int) entries.stream().filter(entry -> keys.contains(entry.key())).count();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
