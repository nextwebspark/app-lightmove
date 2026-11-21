package app.lightmove.api.notification.service;

import app.lightmove.api.common.time.service.DateTimeFormats;
import app.lightmove.api.core.email.constant.EmailCardStyle;
import app.lightmove.api.core.email.constant.EmailTone;
import app.lightmove.api.core.email.render.EmailAction;
import app.lightmove.api.core.email.render.EmailBlock;
import app.lightmove.api.core.email.render.EmailCard;
import app.lightmove.api.core.email.render.EmailChip;
import app.lightmove.api.core.email.render.EmailChips;
import app.lightmove.api.core.email.render.EmailContent;
import app.lightmove.api.core.email.render.EmailNote;
import app.lightmove.api.core.email.render.EmailParagraph;
import app.lightmove.api.core.email.render.EmailPhrase;
import app.lightmove.api.core.email.render.EmailRow;
import app.lightmove.api.core.email.render.EmailSectionTitle;
import app.lightmove.api.notification.model.DigestMention;
import app.lightmove.api.task.model.DueDigest;
import app.lightmove.api.task.model.DueDigestLine;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lays out the one morning digest a person gets per workspace: a verdict, three picks to start from, today's tasks,
 * unread mentions, everything late grouped by position (one row per candidate), work gone quiet, and the next
 * working days. Days are the person's own: a task is late from the day after its due date, not the minute after.
 */
final class TaskDigestComposer {

    static final int STALE_AFTER_DAYS = 14;
    static final int ROWS_PER_POSITION = 5;
    static final int TODAY_ROWS = 8;
    static final int STALE_ROWS = 5;
    static final int MENTION_ROWS = 3;
    static final int PICKS = 3;
    static final int COMING_UP_WORKING_DAYS = 2;

    private final String baseUrl;
    private final DateTimeFormats formats;
    private final ZoneId zone;
    private final LocalDate today;

    TaskDigestComposer(String baseUrl, DateTimeFormats formats, ZoneId zone, Instant now) {
        this.baseUrl = baseUrl;
        this.formats = formats;
        this.zone = zone;
        this.today = LocalDate.ofInstant(now, zone);
    }

    /** The end of the last working day Coming up covers: how far ahead the task read must reach. */
    static Instant horizonOf(Instant now, ZoneId zone) {
        return lastComingUpDay(LocalDate.ofInstant(now, zone)).plusDays(1).atStartOfDay(zone).toInstant();
    }

    boolean hasNews(DueDigest due, long unreadMentions) {
        return unreadMentions > 0 || due.lines().stream().anyMatch(line -> !dayOf(line).isAfter(today));
    }

    EmailContent compose(DueDigest due, List<DigestMention> mentions, int unreadMentions) {
        List<DueDigestLine> late = due.lines().stream().filter(line -> dayOf(line).isBefore(today)).toList();
        List<DueDigestLine> dueToday = due.lines().stream().filter(line -> dayOf(line).equals(today)).toList();
        List<DueDigestLine> comingUp = due.lines().stream().filter(line -> dayOf(line).isAfter(today)).toList();
        List<DueDigestLine> fresh = late.stream().filter(line -> daysLate(line) < STALE_AFTER_DAYS).toList();
        List<DueDigestLine> stale = late.stream().filter(line -> daysLate(line) >= STALE_AFTER_DAYS).toList();
        List<PositionGroup> positions = positionGroupsOf(late, fresh);

        List<EmailBlock> blocks = new ArrayList<>();
        String summary = summaryOf(late, dueToday, positions, unreadMentions);
        if (!summary.isEmpty()) {
            blocks.add(EmailParagraph.of("%s", EmailPhrase.plain(summary)));
        }
        blocks.add(new EmailChips(List.of(
                new EmailChip(late.size(), "overdue", EmailTone.SIGNAL, tasksLink()),
                new EmailChip(dueToday.size(), "due today", EmailTone.ACCENT, tasksLink()),
                new EmailChip(unreadMentions, unreadMentions == 1 ? "mention" : "mentions", EmailTone.NEUTRAL,
                        baseUrl + "/"))));
        List<EmailRow> picks = picksOf(dueToday, fresh);
        if (dueToday.size() + positions.stream().mapToInt(group -> group.rows().size()).sum() + stale.size() > PICKS
                && !picks.isEmpty()) {
            blocks.add(new EmailCard(EmailCardStyle.HIGHLIGHT, "Start here", null, null, picks, null));
        }
        if (!dueToday.isEmpty()) {
            blocks.add(new EmailSectionTitle("Due today · " + dueToday.size(), EmailTone.ACCENT, null));
            blocks.add(new EmailCard(EmailCardStyle.PLAIN, null, null, null, dueToday.stream().limit(TODAY_ROWS)
                    .map(line -> new EmailRow(formats.time(line.dueAt()), nameOf(line),
                            line.title() + " · " + positionOf(line), null, null, null, rowLink(line)))
                    .toList(), moreOf(dueToday.size() - TODAY_ROWS)));
        }
        if (unreadMentions > 0) {
            blocks.add(new EmailSectionTitle("Mentions · " + unreadMentions, EmailTone.NEUTRAL, null));
            List<DigestMention> shown = mentions.stream().limit(MENTION_ROWS).toList();
            blocks.add(new EmailCard(EmailCardStyle.PLAIN, null, null, null, shown.stream()
                    .map(mention -> new EmailRow(null, mention.actorName() + " mentioned you",
                            "On " + mention.executiveName() + " · " + mention.positionTitle(), null, null, null,
                            mention.link()))
                    .toList(), moreOf(unreadMentions - shown.size())));
        }
        if (!positions.isEmpty()) {
            blocks.add(new EmailSectionTitle("Overdue, by position", EmailTone.SIGNAL, "Oldest first"));
            for (PositionGroup group : positions) {
                blocks.add(new EmailCard(EmailCardStyle.PLAIN, group.title(), group.subtitle(),
                        new EmailAction("Open position", positionLink(group.projectId())),
                        group.rows().stream().limit(ROWS_PER_POSITION).toList(), group.footnote()));
            }
        }
        if (!stale.isEmpty()) {
            blocks.add(new EmailSectionTitle("Gone quiet · " + STALE_AFTER_DAYS + "+ days", EmailTone.NEUTRAL, null));
            blocks.add(EmailParagraph.of("Still relevant? Close it or give it a new date in Uncava and it leaves "
                    + "this email."));
            blocks.add(new EmailCard(EmailCardStyle.QUIET, null, null, null, stale.stream().limit(STALE_ROWS)
                    .map(line -> new EmailRow(null, nameOf(line), line.title() + " · " + positionOf(line)
                            + " · was due " + formats.date(line.dueAt()), null, daysLate(line) + " days",
                            EmailTone.NEUTRAL, rowLink(line)))
                    .toList(), moreOf(stale.size() - STALE_ROWS)));
        }
        if (!comingUp.isEmpty()) {
            blocks.add(new EmailSectionTitle("Coming up", EmailTone.NEUTRAL, null));
            blocks.add(new EmailCard(EmailCardStyle.PLAIN, null, null, null, comingUpRowsOf(comingUp), null));
        }
        blocks.add(new EmailAction("Open my tasks", tasksLink()));
        blocks.add(EmailNote.of("A digest goes each weekday morning there is something on your list. Change it or "
                + "turn it off in Settings → Notifications."));
        return new EmailContent(headlineOf(late.size(), dueToday.size(), unreadMentions), blocks);
    }

    private static String headlineOf(int late, int dueToday, int unreadMentions) {
        if (late > 0 && dueToday > 0) {
            return countOf(dueToday, "task") + " due today, " + late + " overdue";
        }
        if (late > 0) {
            return countOf(late, "task") + " overdue, nothing due today";
        }
        if (dueToday > 0) {
            return countOf(dueToday, "task") + " due today";
        }
        return countOf(unreadMentions, "unread mention");
    }

    private String summaryOf(List<DueDigestLine> late, List<DueDigestLine> dueToday, List<PositionGroup> positions,
                             int unreadMentions) {
        List<String> sentences = new ArrayList<>();
        if (!dueToday.isEmpty()) {
            DueDigestLine first = dueToday.getFirst();
            sentences.add("First up: " + first.title() + " with " + nameOf(first) + " at "
                    + formats.time(first.dueAt()) + ".");
        }
        if (positions.size() > 1 && positions.getFirst().late() * 2 > late.size()) {
            sentences.add(positions.getFirst().late() + " of the " + late.size() + " sit on " + positions.getFirst().title() + ".");
        }
        if (!late.isEmpty()) {
            long oldest = daysLate(late.getFirst());
            sentences.add(oldest == 1 ? "The oldest is a day late." : "The oldest has waited " + oldest + " days.");
            if (dueToday.isEmpty() && unreadMentions == 0) {
                sentences.add("A clear day to catch up.");
            }
        }
        return String.join(" ", sentences);
    }

    /** Positions with late work, most first; inside each, one row per candidate, the longest waiting first. */
    private List<PositionGroup> positionGroupsOf(List<DueDigestLine> late, List<DueDigestLine> fresh) {
        Map<UUID, List<DueDigestLine>> lateByPosition = late.stream()
                .collect(Collectors.groupingBy(DueDigestLine::projectId, LinkedHashMap::new, Collectors.toList()));
        List<PositionGroup> groups = new ArrayList<>();
        lateByPosition.forEach((projectId, lines) -> {
            List<EmailRow> rows = conversationsOf(fresh.stream()
                    .filter(line -> line.projectId().equals(projectId)).toList()).stream()
                    .map(this::lateRowOf)
                    .toList();
            if (rows.isEmpty()) {
                return;
            }
            long candidates = lines.stream().map(DueDigestLine::personId).distinct().count();
            long quiet = lines.size() - fresh.stream().filter(line -> line.projectId().equals(projectId)).count();
            List<String> notes = new ArrayList<>();
            if (rows.size() > ROWS_PER_POSITION) {
                notes.add("+" + (rows.size() - ROWS_PER_POSITION) + " more in Uncava");
            }
            if (quiet > 0) {
                notes.add("+" + quiet + " under Gone quiet");
            }
            groups.add(new PositionGroup(projectId, positionOf(lines.getFirst()),
                    lines.size() + " overdue · " + countOf((int) candidates, "candidate"), lines.size(), rows,
                    notes.isEmpty() ? null : String.join(" · ", notes)));
        });
        groups.sort(Comparator.comparingInt(PositionGroup::late).reversed().thenComparing(PositionGroup::title));
        return groups;
    }

    /** A candidate's tasks on one position, oldest first, since one call or message usually closes them together. */
    private List<List<DueDigestLine>> conversationsOf(List<DueDigestLine> lines) {
        Map<List<UUID>, List<DueDigestLine>> byPerson = lines.stream().collect(Collectors.groupingBy(
                line -> List.of(line.projectId(), line.personId()), LinkedHashMap::new, Collectors.toList()));
        return byPerson.values().stream()
                .sorted(Comparator.<List<DueDigestLine>>comparingLong(group -> daysLate(group.getFirst())).reversed()
                        .thenComparing(group -> nameOf(group.getFirst())))
                .toList();
    }

    private EmailRow lateRowOf(List<DueDigestLine> conversation) {
        DueDigestLine oldest = conversation.getFirst();
        return new EmailRow(null, nameOf(oldest), titlesOf(conversation), null, lateBadgeOf(daysLate(oldest)),
                EmailTone.SIGNAL, rowLink(oldest));
    }

    /** Today's first task, the candidate one conversation would clear most for, then whoever has waited longest. */
    private List<EmailRow> picksOf(List<DueDigestLine> dueToday, List<DueDigestLine> fresh) {
        List<EmailRow> picks = new ArrayList<>();
        Set<List<UUID>> picked = new HashSet<>();
        if (!dueToday.isEmpty()) {
            DueDigestLine first = dueToday.getFirst();
            picks.add(new EmailRow(null, first.title() + " · " + nameOf(first), positionOf(first),
                    "Due at " + formats.time(first.dueAt()) + " today", "Today", EmailTone.ACCENT, rowLink(first)));
            picked.add(conversationKeyOf(first));
        }
        List<DueDigestLine> open = new ArrayList<>(fresh);
        open.addAll(dueToday);
        conversationsOf(open).stream()
                .filter(conversation -> conversation.size() > 1 && !picked.contains(conversationKeyOf(conversation.getFirst())))
                .max(Comparator.comparingInt(List::size))
                .ifPresent(conversation -> {
                    DueDigestLine oldest = conversation.getFirst();
                    picks.add(new EmailRow(null, nameOf(oldest), positionOf(oldest) + " · " + titlesOf(conversation),
                            "One conversation closes " + conversation.size() + " tasks", badgeOf(oldest),
                            toneOf(oldest), rowLink(oldest)));
                    picked.add(conversationKeyOf(oldest));
                });
        for (List<DueDigestLine> conversation : conversationsOf(fresh)) {
            if (picks.size() == PICKS) {
                break;
            }
            DueDigestLine oldest = conversation.getFirst();
            if (picked.add(conversationKeyOf(oldest))) {
                picks.add(new EmailRow(null, nameOf(oldest), positionOf(oldest) + " · " + titlesOf(conversation),
                        "Waiting since " + formats.date(oldest.dueAt()), lateBadgeOf(daysLate(oldest)),
                        EmailTone.SIGNAL, rowLink(oldest)));
            }
        }
        return picks;
    }

    private List<EmailRow> comingUpRowsOf(List<DueDigestLine> comingUp) {
        Map<LocalDate, List<DueDigestLine>> byDay = comingUp.stream()
                .collect(Collectors.groupingBy(this::dayOf, LinkedHashMap::new, Collectors.toList()));
        return byDay.entrySet().stream()
                .map(day -> new EmailRow(null,
                        day.getKey().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " "
                                + formats.date(day.getKey()),
                        countOf(day.getValue().size(), "task") + " · " + countOf((int) day.getValue().stream()
                                .map(DueDigestLine::projectId).distinct().count(), "position"),
                        null, null, null, tasksLink()))
                .toList();
    }

    private static LocalDate lastComingUpDay(LocalDate today) {
        LocalDate day = today;
        int workingDays = 0;
        while (workingDays < COMING_UP_WORKING_DAYS) {
            day = day.plusDays(1);
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workingDays++;
            }
        }
        return day;
    }

    private LocalDate dayOf(DueDigestLine line) {
        return LocalDate.ofInstant(line.dueAt(), zone);
    }

    private long daysLate(DueDigestLine line) {
        return ChronoUnit.DAYS.between(dayOf(line), today);
    }

    private String badgeOf(DueDigestLine line) {
        return dayOf(line).equals(today) ? "Today" : lateBadgeOf(daysLate(line));
    }

    private EmailTone toneOf(DueDigestLine line) {
        return dayOf(line).equals(today) ? EmailTone.ACCENT : EmailTone.SIGNAL;
    }

    private static String lateBadgeOf(long days) {
        return days == 1 ? "1 day late" : days + " days late";
    }

    private static String titlesOf(List<DueDigestLine> conversation) {
        return conversation.stream().map(DueDigestLine::title).distinct().collect(Collectors.joining(" · "));
    }

    private static List<UUID> conversationKeyOf(DueDigestLine line) {
        return List.of(line.projectId(), line.personId());
    }

    private static String nameOf(DueDigestLine line) {
        return Objects.requireNonNullElse(line.executiveName(), "An executive");
    }

    private static String positionOf(DueDigestLine line) {
        return Objects.requireNonNullElse(line.positionTitle(), "A position");
    }

    private static String moreOf(int hidden) {
        return hidden > 0 ? "+" + hidden + " more in Uncava" : null;
    }

    private static String countOf(int count, String noun) {
        return count == 1 ? "1 " + noun : count + " " + noun + "s";
    }

    private String tasksLink() {
        return baseUrl + "/tasks";
    }

    private String positionLink(UUID projectId) {
        return "%s/projects/%s/pipeline".formatted(baseUrl, projectId);
    }

    /** The bell's destination for a task: the candidate's drawer on the board, else the person's own record. */
    private String rowLink(DueDigestLine line) {
        return line.candidateId() != null
                ? "%s/projects/%s/pipeline?candidate=%s".formatted(baseUrl, line.projectId(), line.candidateId())
                : "%s/candidates?person=%s".formatted(baseUrl, line.personId());
    }

    private record PositionGroup(UUID projectId, String title, String subtitle, int late, List<EmailRow> rows,
                                 String footnote) {}
}
