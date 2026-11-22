package app.lightmove.api.dataimport.service;

import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataimport.model.ParsedSheet;
import app.lightmove.api.dataimport.model.SheetColumn;
import app.lightmove.api.dataimport.model.SheetColumn.ValueShape;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The column mapping's Try it: the headers of an invented research sheet, with no rows and no sample values. */
@Component
@RequiredArgsConstructor
class ColumnMappingTrial implements PromptTrial {

    private static final ParsedSheet SHEET = new ParsedSheet(List.of(
            column(0, "Exec", ValueShape.SHORT_TEXT),
            column(1, "Current Co.", ValueShape.SHORT_TEXT),
            column(2, "Role", ValueShape.SHORT_TEXT),
            column(3, "Where based", ValueShape.SHORT_TEXT),
            column(4, "LI profile", ValueShape.URL),
            column(5, "Yrs in seat", ValueShape.NUMBER),
            column(6, "Last spoke", ValueShape.DATE),
            column(7, "Our view", ValueShape.LONG_TEXT),
            column(8, "Relocate?", ValueShape.BOOLEAN),
            new SheetColumn(9, "Spare", ValueShape.BLANK, List.of(), true)), List.of());

    private static final List<CustomColumnDto> EXISTING_COLUMNS = List.of(
            new CustomColumnDto("trial-column", "candidate", "willing_to_relocate", "Willing to relocate",
                    "boolean", 0, false));

    private final ColumnMappingProposer proposer;
    private final HeuristicColumnMatcher heuristics;

    @Override
    public String promptId() {
        return "import-column-mapping";
    }

    @Override
    public String inputName() {
        return "Abbreviated research sheet";
    }

    @Override
    public String inputDescription() {
        return "Ten column headers from a fictional research sheet, abbreviated as a consultant would type them, "
                + "and one custom column the position already has. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return ColumnMappingProposer.USER_MESSAGE;
    }

    @Override
    public void run() {
        proposer.proposeFromModel(SHEET, EXISTING_COLUMNS,
                heuristics.propose(SHEET, EXISTING_COLUMNS).mappings());
    }

    private static SheetColumn column(int index, String header, ValueShape shape) {
        return new SheetColumn(index, header, shape, List.of(), false);
    }
}
