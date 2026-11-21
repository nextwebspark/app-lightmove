package app.lightmove.api.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.time.model.DateTimePreferences;
import app.lightmove.api.common.time.service.DateTimeFormats;
import app.lightmove.api.core.email.constant.EmailCardStyle;
import app.lightmove.api.core.email.render.EmailBlock;
import app.lightmove.api.core.email.render.EmailCard;
import app.lightmove.api.core.email.render.EmailContent;
import app.lightmove.api.core.email.render.EmailRow;
import app.lightmove.api.core.email.render.EmailSectionTitle;
import app.lightmove.api.notification.model.DigestMention;
import app.lightmove.api.task.model.DueDigest;
import app.lightmove.api.task.model.DueDigestLine;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The morning digest's layout, on the shape of a real morning: one email, every position in it. */
class TaskDigestComposerTest {

    private static final String BASE_URL = "https://beta.uncava.com";
    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 20);
    private static final Instant MORNING = FRIDAY.atTime(8, 5).atZone(DUBAI).toInstant();

    private static final UUID MERCHANDISING = UUID.randomUUID();
    private static final UUID TECHNOLOGY = UUID.randomUUID();
    private static final UUID INVESTMENT = UUID.randomUUID();

    private final TaskDigestComposer composer = new TaskDigestComposer(BASE_URL,
            DateTimeFormats.of(DateTimePreferences.STANDARD), DUBAI, MORNING);

    private final UUID amit = UUID.randomUUID();

    @Test
    @DisplayName("a late morning reads as one verdict, with everything late grouped by position")
    void groupsLateWorkByPosition() {
        EmailContent content = composer.compose(lateMorning(), List.of(), 0);

        assertThat(content.heading()).isEqualTo("9 tasks overdue, nothing due today");
        List<EmailCard> positions = cardsAfter(content, "Overdue, by position");
        assertThat(positions).extracting(EmailCard::title)
                .containsExactly("Merchandising Director", "Chief Investment Officer", "Chief Technology Officer");
        assertThat(positions.getFirst().subtitle()).isEqualTo("7 overdue · 6 candidates");
        assertThat(positions.getFirst().footnote()).isEqualTo("+1 under Gone quiet");
        assertThat(positions.getFirst().link().href()).isEqualTo(BASE_URL + "/projects/" + MERCHANDISING + "/pipeline");
    }

    @Test
    @DisplayName("a candidate's two tasks on one position are one row, the longest wait first")
    void mergesOneCandidatesTasks() {
        EmailCard merchandising = cardsAfter(composer.compose(lateMorning(), List.of(), 0), "Overdue, by position")
                .getFirst();

        assertThat(merchandising.rows()).extracting(EmailRow::primary).containsExactly("Amit Tandon",
                "Yiğit Kocadölü", "Ayanda Thomas (Masondo)", "Gary de Klerk", "Natalie Weare");
        assertThat(merchandising.rows().getFirst().secondary()).isEqualTo("Call to qualify interest · Debrief");
        assertThat(merchandising.rows().getFirst().badge()).isEqualTo("11 days late");
        assertThat(merchandising.rows().getLast().badge()).isEqualTo("1 day late");
    }

    @Test
    @DisplayName("work fourteen days late leaves the groups for Gone quiet")
    void setsStaleWorkAside() {
        EmailContent content = composer.compose(lateMorning(), List.of(), 0);

        EmailCard quiet = cardsAfter(content, "Gone quiet · 14+ days").getFirst();
        assertThat(quiet.style()).isEqualTo(EmailCardStyle.QUIET);
        assertThat(quiet.rows()).extracting(EmailRow::primary).containsExactly("Cem Akkuş");
        assertThat(quiet.rows().getFirst().badge()).isEqualTo("30 days");
    }

    @Test
    @DisplayName("Start here picks the candidate one conversation clears most for, then the longest waiting")
    void picksWhereToStart() {
        EmailCard start = highlightOf(composer.compose(lateMorning(), List.of(), 0));

        assertThat(start.rows()).hasSize(3);
        assertThat(start.rows().getFirst().primary()).isEqualTo("Amit Tandon");
        assertThat(start.rows().getFirst().detail()).isEqualTo("One conversation closes 2 tasks");
        assertThat(start.rows()).extracting(EmailRow::primary).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("today's tasks lead, with their time, and the first of them is the first pick")
    void leadsWithToday() {
        List<DueDigestLine> lines = new ArrayList<>(lateMorning().lines());
        lines.add(line("Call to qualify interest", "Natalie Weare", MERCHANDISING, "Merchandising Director",
                FRIDAY.atTime(10, 0)));
        EmailContent content = composer.compose(new DueDigest(lines), List.of(), 0);

        assertThat(content.heading()).isEqualTo("1 task due today, 9 overdue");
        assertThat(cardsAfter(content, "Due today · 1").getFirst().rows().getFirst().leading()).isEqualTo("10:00");
        assertThat(highlightOf(content).rows().getFirst().badge()).isEqualTo("Today");
    }

    @Test
    @DisplayName("mentions and the next two working days appear only when there is something in them")
    void showsMentionsAndComingUp() {
        List<DueDigestLine> lines = new ArrayList<>(lateMorning().lines());
        lines.add(line("Debrief", "Rajiv Shankar", TECHNOLOGY, "Chief Technology Officer", LocalDate.of(2026, 11, 23)
                .atTime(9, 0)));
        DigestMention mention = new DigestMention("Sara Ali", "Gary de Klerk", "Merchandising Director",
                BASE_URL + "/projects/x/pipeline");
        EmailContent content = composer.compose(new DueDigest(lines), List.of(mention), 2);

        assertThat(cardsAfter(content, "Mentions · 2").getFirst().rows().getFirst().primary())
                .isEqualTo("Sara Ali mentioned you");
        assertThat(cardsAfter(content, "Mentions · 2").getFirst().footnote()).isEqualTo("+1 more in Uncava");
        assertThat(cardsAfter(content, "Coming up").getFirst().rows().getFirst().primary()).startsWith("Monday");
        assertThat(cardsAfter(composer.compose(lateMorning(), List.of(), 0), "Coming up")).isEmpty();
    }

    @Test
    @DisplayName("the read reaches the end of the second working day ahead, over the weekend")
    void reachesTwoWorkingDaysAhead() {
        assertThat(TaskDigestComposer.horizonOf(MORNING, DUBAI))
                .isEqualTo(LocalDate.of(2026, 11, 25).atStartOfDay(DUBAI).toInstant());
    }

    @Test
    @DisplayName("only tasks still ahead are no reason to send")
    void sendsNothingForFutureWorkAlone() {
        DueDigest ahead = new DueDigest(List.of(line("Debrief", "Rajiv Shankar", TECHNOLOGY,
                "Chief Technology Officer", LocalDate.of(2026, 11, 23).atTime(9, 0))));

        assertThat(composer.hasNews(ahead, 0)).isFalse();
        assertThat(composer.hasNews(ahead, 1)).isTrue();
        assertThat(composer.hasNews(lateMorning(), 0)).isTrue();
    }

    private DueDigest lateMorning() {
        return new DueDigest(List.of(
                line("Debrief", "Cem Akkuş", MERCHANDISING, "Merchandising Director", at(21, 10)),
                line(amit, "Call to qualify interest", "Amit Tandon", MERCHANDISING, "Merchandising Director",
                        at(9, 11)),
                line("Call to qualify interest", "Yiğit Kocadölü", MERCHANDISING, "Merchandising Director",
                        at(9, 11)),
                line(amit, "Debrief", "Amit Tandon", MERCHANDISING, "Merchandising Director", at(12, 11)),
                line("Interview", "Rajiv Shankar", TECHNOLOGY, "Chief Technology Officer", at(12, 11)),
                line("Debrief", "Aleksandra Mizińska", INVESTMENT, "Chief Investment Officer", at(16, 11)),
                line("Reply", "Ayanda Thomas (Masondo)", MERCHANDISING, "Merchandising Director", at(17, 11)),
                line("Reply", "Gary de Klerk", MERCHANDISING, "Merchandising Director", at(17, 11)),
                line("Reply", "Natalie Weare", MERCHANDISING, "Merchandising Director", at(19, 11))));
    }

    private static java.time.LocalDateTime at(int day, int month) {
        return LocalDate.of(2026, month, day).atTime(LocalTime.of(10, 30));
    }

    private static DueDigestLine line(String title, String name, UUID projectId, String position,
                                      java.time.LocalDateTime due) {
        return line(UUID.randomUUID(), title, name, projectId, position, due);
    }

    private static DueDigestLine line(UUID personId, String title, String name, UUID projectId, String position,
                                      java.time.LocalDateTime due) {
        return new DueDigestLine(UUID.randomUUID(), title, personId, name, UUID.randomUUID(), projectId, position,
                due.atZone(DUBAI).toInstant());
    }

    private static EmailCard highlightOf(EmailContent content) {
        return content.blocks().stream().filter(EmailCard.class::isInstance).map(EmailCard.class::cast)
                .filter(card -> card.style() == EmailCardStyle.HIGHLIGHT).findFirst().orElseThrow();
    }

    /** The cards between a section title and the next one. */
    private static List<EmailCard> cardsAfter(EmailContent content, String label) {
        List<EmailCard> cards = new ArrayList<>();
        boolean inSection = false;
        for (EmailBlock block : content.blocks()) {
            if (block instanceof EmailSectionTitle title) {
                inSection = title.label().equals(label);
            } else if (inSection && block instanceof EmailCard card) {
                cards.add(card);
            }
        }
        return cards;
    }
}
