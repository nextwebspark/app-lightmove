package app.lightmove.api.candidate.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** An upload that named no category is filed under the file name's best guess. */
class PersonDocumentCategoryTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "Jane_Doe_CV_2026.pdf, CV",
            "Resume - Jane Doe.docx, CV",
            "Cover letter.pdf, COVER_LETTER",
            "CV and cover letter.pdf, COVER_LETTER",
            "Reference from ADNOC.pdf, REFERENCE",
            "MBA diploma.jpg, CERTIFICATE",
            "Hogan assessment.pdf, ASSESSMENT",
            "scan0012.pdf, OTHER",
            "cvent-brochure.pdf, OTHER"
    })
    void guessesFromTheName(String fileName, PersonDocumentCategory expected) {
        assertThat(PersonDocumentCategory.guessFrom(fileName)).isEqualTo(expected);
    }
}
