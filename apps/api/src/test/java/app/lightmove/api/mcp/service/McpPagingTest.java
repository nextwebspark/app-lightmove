package app.lightmove.api.mcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.mcp.dto.McpCompanyRow;
import app.lightmove.api.mcp.model.McpRowsPage;
import app.lightmove.api.mcp.model.McpToolRefusal;
import app.lightmove.api.publicapi.dto.PublicCompany;
import app.lightmove.api.publicapi.dto.PublicPage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class McpPagingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<Integer> ROWS = IntStream.range(0, 23).boxed().toList();

    @Test
    @DisplayName("a cursor reads on from any offset, across the read's own pages, to the last row and no further")
    void readsOnFromAnyOffset() {
        McpPaging paging = new McpPaging(JSON, 90_000);
        List<Integer> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            McpRowsPage<Integer> page = paging.page(cursor, 7, McpPagingTest::read, row -> row);
            seen.addAll(page.rows());
            assertThat(page.totalCount()).isEqualTo(23);
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);
        assertThat(seen).isEqualTo(ROWS);
        assertThat(pages).isEqualTo(4);

        McpRowsPage<Integer> unaligned = paging.page(McpCursor.at(5), 7, McpPagingTest::read, row -> row);
        assertThat(unaligned.rows()).containsExactly(5, 6, 7, 8, 9, 10, 11);
        assertThat(McpCursor.offsetOf(unaligned.nextCursor())).isEqualTo(12);
    }

    @Test
    @DisplayName("a page the cap cuts short says so and reads on from its first left-out row")
    void cutPageReadsOn() {
        McpPaging paging = new McpPaging(JSON, 1_000 + 3 * 4);
        McpRowsPage<Integer> page = paging.page(null, 10, McpPagingTest::read, row -> row + 100);
        assertThat(page.rows()).containsExactly(100, 101, 102);
        assertThat(page.notice()).startsWith("Cut to 3 of 10 rows");
        assertThat(McpCursor.offsetOf(page.nextCursor())).isEqualTo(3);
    }

    @Test
    @DisplayName("a concise page of 100 companies stays well under the default cap")
    void concisePageIsSmall() {
        List<McpCompanyRow> rows = IntStream.range(0, 100)
                .mapToObj(index -> McpCompanyRow.of(new PublicCompany(UUID.randomUUID(), "inUniverse",
                        "Saudi Arabian Mining Company Ma'aden Holding " + index, "Mining & Metals", "Saudi Arabia",
                        "Riyadh", 12_000, 9_000_000_000L, "https://www.maaden.com.sa",
                        "https://www.linkedin.com/company/maaden", 1997, "x".repeat(1_500), null, false,
                        Instant.now()), false))
                .toList();
        assertThat(JSON.writeValueAsString(rows).length()).isLessThan(90_000 / 3);
    }

    @Test
    @DisplayName("a cursor this server did not issue, and a limit out of range, are refused")
    void refusals() {
        McpPaging paging = new McpPaging(JSON, 90_000);
        assertThatThrownBy(() -> McpCursor.offsetOf("not-ours")).isInstanceOf(McpToolRefusal.class);
        assertThatThrownBy(() -> McpCursor.offsetOf(McpCursor.at(0).substring(1))).isInstanceOf(McpToolRefusal.class);
        assertThatThrownBy(() -> paging.page(null, 0, McpPagingTest::read, row -> row))
                .isInstanceOf(McpToolRefusal.class);
    }

    private static PublicPage<Integer> read(int page, int size) {
        List<Integer> rows = ROWS.stream().skip((long) page * size).limit(size).toList();
        return new PublicPage<>(rows, page, size, ROWS.size());
    }
}
