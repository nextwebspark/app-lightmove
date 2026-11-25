package app.lightmove.api.promptregistry.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.ModelCapability;
import app.lightmove.api.core.llm.constant.ModelLaunchStage;
import app.lightmove.api.core.llm.model.AiModel;
import app.lightmove.api.core.llm.model.SettingRange;
import app.lightmove.api.core.llm.service.AiModelCatalog;
import app.lightmove.api.promptregistry.dto.AiModelResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The model list's retirement warning, on dates either side of a model's retirement and on none at all. */
class PromptCatalogServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 1, 10);

    @Test
    @DisplayName("a model warns from 60 days before it retires, stops warning once retired, and may state no date")
    void retirementIsWarnedOnlyBeforeTheDate() {
        AiModelCatalog catalog = new AiModelCatalog() {
            @Override
            public List<AiModel> all() {
                return List.of(model("far", TODAY.plusDays(200)), model("soon", TODAY.plusDays(60)),
                        model("retired", TODAY.minusDays(3)), model("undated", null));
            }
        };
        PromptCatalogService service = new PromptCatalogService(null, null, catalog, List.of(), null, null, null, null,
                Clock.fixed(Instant.parse("2027-01-10T12:00:00Z"), ZoneOffset.UTC));

        Map<String, AiModelResponse> listed = service.models().stream()
                .collect(Collectors.toMap(AiModelResponse::modelId, Function.identity()));

        assertThat(listed.get("far").retiringSoon()).isFalse();
        assertThat(listed.get("soon").retiringSoon()).isTrue();
        assertThat(listed.get("retired").retiringSoon()).isFalse();
        assertThat(listed.get("retired").retired()).isTrue();
        assertThat(listed.get("retired").retiresInDays()).isEqualTo(-3);
        assertThat(listed.get("undated").retiresInDays()).isNull();
        assertThat(listed.get("undated").retiringSoon()).isFalse();
        assertThat(listed.get("undated").retired()).isFalse();
        assertThat(listed.values()).allSatisfy(model -> assertThat(model.status()).isEqualTo(ModelLaunchStage.GA));
    }

    private static AiModel model(String modelId, LocalDate retiresOn) {
        return new AiModel(AiProvider.VERTEX_GEMINI, modelId, modelId, ModelLaunchStage.GA, retiresOn, "terms",
                Set.of(ModelCapability.STRUCTURED_ANSWER), new SettingRange(0, 1), new SettingRange(0, 1),
                new SettingRange(0, 1), List.of());
    }
}
