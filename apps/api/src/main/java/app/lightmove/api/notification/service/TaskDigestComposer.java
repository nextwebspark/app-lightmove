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
import app.lightmove.api.notification.model.DigestBuckets;
import app.lightmove.api.notification.model.DigestMention;
import app.lightmove.api.task.model.DueDigestLine;
import java.time.LocalDate;
import java.time.format.TextStyle;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lays out the one morning digest a person gets per workspace: a verdict, three picks to start from, today's tasks,
 * unread mentions, everything late grouped by position (one row per executive), work gone quiet, and the next
 * working days. Which bucket a task is in is {@link DigestBuckets}'s; how it reads is this class's.
 */
@Component
@RequiredArgsConstructor
public class TaskDigestComposer {

    private static final int ROWS_PER_POSITION = 5;
    private static final int TODAY_ROWS = 8;
    private static final int STALE_ROWS = 5;
    private static final int PICKS = 3;

    private final NotificationLinks links;

    /** {@code mentions} are the ones to name, newest first; {@code unreadMentions} counts them all. */
    public EmailContent compose(DigestBuckets buckets, DateTimeFormats formats, List<DigestMention> mentions,
                                int unreadMentions) {
        return new Layout(buckets, formats).contentOf(mentions, unreadMentions);
    }

    /** One digest's worth of state: the person's buckets and how they read dates and times. */
    private final class Layout {

        private final DigestBuckets buckets;
        private final DateTimeFormats formats;

        private Layout(DigestBuckets buckets, DateTimeFormats formats) {
            this.buckets = buckets;
            this.formats = formats;
        }

        private EmailContent contentOf(List<DigestMention> mentions, int unreadMentions) {
            List<DueDigestLine> late = buckets.late();
            List<DueDigestLine> dueToday = buckets.dueToday();
            List<DueDigestLine> stale = buckets.stale();
            List<PositionGroup> positions = positionGroups();

            List<EmailBlock> blocks = new ArrayList<>();
            String summary = summaryOf(positions, unreadMentions);
            if (!summary.isEmpty()) {
                blocks.add(EmailParagraph.of("%s", EmailPhrase.plain(summary)));
            }
            blocks.add(new EmailChips(List.of(
                    new EmailChip(late.size(), "overdue", EmailTone.SIGNAL, links.myTasks()),
                    new EmailChip(dueToday.size(), "due today", EmailTone.ACCENT, links.myTasks()),
                    new EmailChip(unreadMentions, unreadMentions == 1 ? "mention" : "mentions", EmailTone.NEUTRAL,
                            links.home()))));
            List<EmailRow> picks = picks();
            int rowsBelow = dueToday.size() + positions.stream().mapToInt(group -> group.rows().size()).sum()
                    + stale.size();
            if (rowsBelow > PICKS && !picks.isEmpty()) {
                blocks.add(new EmailCard(EmailCardStyle.HIGHLIGHT, "Start here", null, null, picks, null));
            }
            if (!dueToday.isEmpty()) {
                blocks.add(new EmailSectionTitle("Due today · " + dueToday.size(), EmailTone.ACCENT, null));
                blocks.add(new EmailCard(EmailCardStyle.PLAIN, null, null, null, dueToday.stream().limit(TODAY_ROWS)
                        .map(line -> EmailRow.of(nameOf(line), line.title() + " · " + positionOf(line), linkOf(line))
                                .withLeading(clockOf(line)))
                        .toList(), moreOf(dueToday.size() - TODAY_ROWS)));
            }
            if (unreadMentions > 0) {
                blocks.add(new EmailSectionTitle("Mentions · " + unreadMentions, EmailTone.NEUTRAL, null));
                blocks.add(new EmailCard(EmailCardStyle.PLAIN, null, null, null, mentions.stream()
                        .map(mention -> EmailRow.of(mention.actorName() + " mentioned you",
                                "On " + mention.executiveName() + " · " + mention.positionTitle(), mention.link()))
                        .toList(), moreOf(unreadMentions - mentions.size())));
            }
            if (!positions.isEmpty()) {
                blocks.add(new EmailSectionTitle("Overdue, by position", EmailTone.SIGNAL, "Oldest first"));
                for (PositionGroup group : positions) {
                    blocks.add(new EmailCard(EmailCardStyle.PLAIN, group.title(), group.subtitle(),
                            new EmailAction("Open position", links.position(group.projectId())),
                            group.rows().stream().limit(ROWS_PER_POSITION).toList(), group.footnote()));
                }
            }
            if (!stale.isEmpty()) {
                blocks.add(new EmailSectionTitle("Gone quiet · " + DigestBuckets.STALE_AFTER_DAYS + "+ days",
                        EmailTone.NEUTRAL, null));
                blocks.add(EmailParagraph.of("Still relevant? Close it or give it a new date in Uncava and it "
                        + "leaves this email."));
                blocks.add(new EmailCard(EmailCardStyle.QUIET, null, null, null, stale.stream().limit(STALE_ROWS)
                        .map(line -> EmailRow.of(nameOf(line), line.title() + " · " + positionOf(line)
                                        + " · was due " + formats.date(line.dueAt()), linkOf(line))
                                .withBadge(buckets.daysLate(line) + " days", EmailTone.NEUTRAL))
                        .toList(), moreOf(stale.size() - STALE_ROWS)));
            }
            if (!buckets.comingUp().isEmpty()) {
                blocks.add(new EmailSectionTitle("Coming up", EmailTone.NEUTRAL, null));
                blocks.add(new EmailCard(EmailCardStyle.PLAIN, null, null, null, comingUpRows(), null));
            }
            blocks.add(new EmailAction("Open my tasks", links.myTasks()));
            blocks.add(EmailNote.of("A digest goes each weekday morning there is something on your list. Change it "
                    + "or turn it off in Settings → Notifications."));
            return new EmailContent(headlineOf(late.size(), dueToday.size(), unreadMentions), blocks);
        }

        private String summaryOf(List<PositionGroup> positions, int unreadMentions) {
            List<DueDigestLine> late = buckets.late();
            List<DueDigestLine> dueToday = buckets.dueToday();
            List<String> sentences = new ArrayList<>();
            if (!dueToday.isEmpty()) {
                DueDigestLine first = dueToday.getFirst();
                sentences.add("First up: " + first.title() + " with " + nameOf(first)
                        + (buckets.isCarriedFromWeekend(first) ? ", carried from " + weekdayOf(first)
                                : " at " + formats.time(first.dueAt())) + ".");
            }
            if (positions.size() > 1 && positions.getFirst().late() * 2 > late.size()) {
                sentences.add(positions.getFirst().late() + " of the " + late.size() + " sit on "
                        + positions.getFirst().title() + ".");
            }
            if (!late.isEmpty()) {
                long oldest = buckets.daysLate(late.getFirst());
                sentences.add(oldest == 1 ? "The oldest is a day late." : "The oldest has waited " + oldest + " days.");
                if (dueToday.isEmpty() && unreadMentions == 0) {
                    sentences.add("A clear day to catch up.");
                }
            }
            return String.join(" ", sentences);
        }

        /**
         * Every position with late work, the most rows to act on first; inside each, one row per executive, the longest
         * waiting first. A position whose late work has all gone quiet keeps its card, empty but for the count.
         */
        private List<PositionGroup> positionGroups() {
            Map<UUID, List<DueDigestLine>> lateByPosition = buckets.late().stream()
                    .collect(Collectors.groupingBy(DueDigestLine::projectId, LinkedHashMap::new, Collectors.toList()));
            List<PositionGroup> groups = new ArrayList<>();
            lateByPosition.forEach((projectId, lines) -> {
                List<DueDigestLine> fresh = buckets.fresh().stream()
                        .filter(line -> line.projectId().equals(projectId)).toList();
                List<EmailRow> rows = conversationsOf(fresh).stream().map(this::lateRowOf).toList();
                long executives = lines.stream().map(DueDigestLine::personId).distinct().count();
                List<String> notes = new ArrayList<>();
                if (rows.size() > ROWS_PER_POSITION) {
                    notes.add("+" + (rows.size() - ROWS_PER_POSITION) + " more in Uncava");
                }
                if (lines.size() > fresh.size()) {
                    notes.add("+" + (lines.size() - fresh.size()) + " under Gone quiet");
                }
                groups.add(new PositionGroup(projectId, positionOf(lines.getFirst()),
                        lines.size() + " overdue · " + countOf((int) executives, "executive"), lines.size(), rows,
                        notes.isEmpty() ? null : String.join(" · ", notes)));
            });
            groups.sort(Comparator.comparingInt((PositionGroup group) -> group.rows().size()).reversed()
                    .thenComparing(Comparator.comparingInt(PositionGroup::late).reversed())
                    .thenComparing(PositionGroup::title));
            return groups;
        }

        /** An executive's tasks on one position, oldest first: one call or message usually closes them together. */
        private List<List<DueDigestLine>> conversationsOf(List<DueDigestLine> lines) {
            Map<List<UUID>, List<DueDigestLine>> byPerson = lines.stream().collect(Collectors.groupingBy(
                    TaskDigestComposer::conversationKeyOf, LinkedHashMap::new, Collectors.toList()));
            return byPerson.values().stream()
                    .sorted(Comparator.<List<DueDigestLine>>comparingLong(group -> buckets.daysLate(group.getFirst()))
                            .reversed()
                            .thenComparing(group -> nameOf(group.getFirst())))
                    .toList();
        }

        private EmailRow lateRowOf(List<DueDigestLine> conversation) {
            DueDigestLine oldest = conversation.getFirst();
            return EmailRow.of(nameOf(oldest), titlesOf(conversation), linkOf(oldest))
                    .withBadge(lateBadgeOf(buckets.daysLate(oldest)), EmailTone.SIGNAL);
        }

        /** Today's first task, the executive one conversation would clear most for, then whoever waited longest. */
        private List<EmailRow> picks() {
            List<DueDigestLine> dueToday = buckets.dueToday();
            List<EmailRow> picks = new ArrayList<>();
            Set<List<UUID>> picked = new HashSet<>();
            if (!dueToday.isEmpty()) {
                DueDigestLine first = dueToday.getFirst();
                picks.add(EmailRow.of(first.title() + " · " + nameOf(first), positionOf(first), linkOf(first))
                        .withDetail(buckets.isCarriedFromWeekend(first) ? "Carried from " + weekdayOf(first)
                                : "Due at " + formats.time(first.dueAt()) + " today")
                        .withBadge("Today", EmailTone.ACCENT));
                picked.add(conversationKeyOf(first));
            }
            List<DueDigestLine> open = new ArrayList<>(buckets.fresh());
            open.addAll(dueToday);
            conversationsOf(open).stream()
                    .filter(conversation -> conversation.size() > 1
                            && !picked.contains(conversationKeyOf(conversation.getFirst())))
                    .max(Comparator.comparingInt(List::size))
                    .ifPresent(conversation -> {
                        DueDigestLine oldest = conversation.getFirst();
                        boolean today = buckets.isDueToday(oldest);
                        picks.add(EmailRow.of(nameOf(oldest), positionOf(oldest) + " · " + titlesOf(conversation),
                                        linkOf(oldest))
                                .withDetail("One conversation closes " + conversation.size() + " tasks")
                                .withBadge(today ? "Today" : lateBadgeOf(buckets.daysLate(oldest)),
                                        today ? EmailTone.ACCENT : EmailTone.SIGNAL));
                        picked.add(conversationKeyOf(oldest));
                    });
            for (List<DueDigestLine> conversation : conversationsOf(buckets.fresh())) {
                if (picks.size() == PICKS) {
                    break;
                }
                DueDigestLine oldest = conversation.getFirst();
                if (picked.add(conversationKeyOf(oldest))) {
                    picks.add(EmailRow.of(nameOf(oldest), positionOf(oldest) + " · " + titlesOf(conversation), linkOf(oldest))
                            .withDetail("Waiting since " + formats.date(oldest.dueAt()))
                            .withBadge(lateBadgeOf(buckets.daysLate(oldest)), EmailTone.SIGNAL));
                }
            }
            return picks;
        }

        private List<EmailRow> comingUpRows() {
            Map<LocalDate, List<DueDigestLine>> byDay = buckets.comingUp().stream()
                    .collect(Collectors.groupingBy(buckets::workingDayOf, LinkedHashMap::new, Collectors.toList()));
            return byDay.entrySet().stream()
                    .map(day -> EmailRow.of(
                            day.getKey().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " "
                                    + formats.date(day.getKey()),
                            countOf(day.getValue().size(), "task") + " · " + countOf((int) day.getValue().stream()
                                    .map(DueDigestLine::projectId).distinct().count(), "position"),
                            links.myTasks()))
                    .toList();
        }

        private String clockOf(DueDigestLine line) {
            return buckets.isCarriedFromWeekend(line) ? weekdayOf(line).substring(0, 3) : formats.time(line.dueAt());
        }

        private String weekdayOf(DueDigestLine line) {
            return buckets.dayOf(line).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        }

        private String linkOf(DueDigestLine line) {
            return links.of(line.projectId(), line.candidateId(), line.personId(), null);
        }
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

    private record PositionGroup(UUID projectId, String title, String subtitle, int late, List<EmailRow> rows,
                                 String footnote) {}
}
