package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubjectAccessHtmlTest {

    @Test
    @DisplayName("a column reads as words, and an id shown by what it names drops its ID")
    void labelsReadAsWords() {
        assertThat(SubjectAccessHtml.labelOf("ai_nationality_reading", false)).isEqualTo("AI nationality reading");
        assertThat(SubjectAccessHtml.labelOf("linkedin_url", false)).isEqualTo("LinkedIn URL");
        assertThat(SubjectAccessHtml.labelOf("project_id", false)).isEqualTo("Project ID");
        assertThat(SubjectAccessHtml.labelOf("project_id", true)).isEqualTo("Position");
        assertThat(SubjectAccessHtml.labelOf("author_user_id", true)).isEqualTo("Author");
        assertThat(SubjectAccessHtml.labelOf("status_id", true)).isEqualTo("Status");
    }
}
