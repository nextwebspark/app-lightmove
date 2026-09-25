package app.lightmove.api.positiontemplate.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PositionTemplateBodyTest {

    @Test
    @DisplayName("a template that names no currency drafts its package in AED")
    void anUnstatedCurrencyIsTheDefault() {
        assertThat(PositionTemplateBody.empty().currency()).isEqualTo("AED");
    }
}
