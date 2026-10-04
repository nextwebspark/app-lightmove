package app.lightmove.api.common.constant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

class ApiValueEnumTest {

    @Test
    @DisplayName("every implementer spells each constant by a distinct token that resolves back to it")
    void everyTokenIsDistinctAndRoundTrips() throws ClassNotFoundException {
        List<Class<?>> implementers = implementers();
        assertThat(implementers).hasSizeGreaterThan(10);
        for (Class<?> type : implementers) {
            ApiValueEnum[] constants = (ApiValueEnum[]) type.getEnumConstants();
            assertThat(Arrays.stream(constants).map(ApiValueEnum::value))
                    .as(type.getSimpleName()).doesNotHaveDuplicates().doesNotContainNull();
            for (ApiValueEnum constant : constants) {
                assertThat(roundTrip(type, constant.value())).as(type.getSimpleName()).isSameAs(constant);
            }
        }
    }

    @Test
    @DisplayName("fromValue answers null for null and for a token no constant spells")
    void fromValueAnswersNullForUnknown() {
        assertThat(ApiValueEnum.fromValue(CandidateStatus.class, null)).isNull();
        assertThat(ApiValueEnum.fromValue(CandidateStatus.class, "IDENTIFIED")).isNull();
        assertThat(ApiValueEnum.fromValue(CandidateStatus.class, "identified")).isSameAs(CandidateStatus.IDENTIFIED);
    }

    @Test
    @DisplayName("parse gives an absent token its default and a wrong one a 400 naming the field")
    void parseDefaultsBlankAndRefusesUnknown() {
        assertThat(ApiValueEnum.parse(CandidateStatus.class, null, CandidateStatus.IDENTIFIED, "status"))
                .isSameAs(CandidateStatus.IDENTIFIED);
        assertThat(ApiValueEnum.parse(CandidateStatus.class, "  ", null, "status")).isNull();
        assertThatThrownBy(() -> ApiValueEnum.parse(CandidateStatus.class, "hired", null, "candidate status"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("Unknown candidate status: hired");
    }

    @Test
    @DisplayName("require refuses an absent token too")
    void requireRefusesBlank() {
        assertThatThrownBy(() -> ApiValueEnum.require(CandidateStatus.class, "", "candidate status"))
                .isInstanceOf(ApiException.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object roundTrip(Class<?> type, String token) {
        return ApiValueEnum.fromValue((Class) type, token);
    }

    private static List<Class<?>> implementers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(ApiValueEnum.class));
        List<Class<?>> types = new java.util.ArrayList<>();
        for (var definition : scanner.findCandidateComponents("app.lightmove.api")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (type.isEnum()) {
                types.add(type);
            }
        }
        return types;
    }
}
