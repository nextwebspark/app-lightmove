package app.lightmove.api.core.email.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IndefiniteArticleTest {

    @ParameterizedTest
    @CsvSource({
            "Chief Financial Officer, a Chief Financial Officer",
            "Operations Director, an Operations Director",
            "Interim CFO, an Interim CFO",
            "HR Director, an HR Director",
            "CFO, a CFO",
            "MD Europe, an MD Europe",
            "Unit Head, a Unit Head",
            "European Sales Lead, a European Sales Lead",
            "'  Engineering Manager ', an Engineering Manager"
    })
    void picksTheArticleByHowTheTitleSounds(String title, String expected) {
        assertThat(IndefiniteArticle.before(title)).isEqualTo(expected);
    }
}
