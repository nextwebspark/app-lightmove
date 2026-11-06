package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubPictureFetcher;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Settings → Profile's photo: the caller's own upload and removal, and the public link every screen draws it from.
 * Each test signs up on its own domain; nothing rolls back.
 */
@IntegrationTest
class ProfilePhotoIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("an upload is re-encoded, put on /me as a link, and served from it to anyone, signed in or not")
    void uploadsAndServes() throws Exception {
        String address = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", address);

        MvcResult uploaded = mvc.perform(upload(token, png(Color.ORANGE, 900, 600)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarEditable").value(true))
                .andReturn();
        String link = body(uploaded).get("avatarUrl").asText();
        assertThat(link).matches("/api/v1/avatars/[A-Za-z0-9_-]{43}");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.avatarUrl").value(link));

        MvcResult served = mvc.perform(get(link))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, private, immutable"))
                .andReturn();
        var picture = ImageIO.read(new ByteArrayInputStream(served.getResponse().getContentAsByteArray()));
        assertThat(picture.getWidth()).isEqualTo(512);
        assertThat(picture.getHeight()).isEqualTo(512);

        assertThat(auditCount("AVATAR_UPLOADED", address)).isEqualTo(1);
    }

    @Test
    @DisplayName("a new upload is a new link, and the old one answers nothing")
    void replacingIssuesANewLink() throws Exception {
        String token = verifiedUser("Alok Kumar", "alok@" + domain);
        String first = body(mvc.perform(upload(token, png(Color.RED, 300, 300))).andReturn()).get("avatarUrl").asText();

        String second = body(mvc.perform(upload(token, png(Color.BLUE, 300, 300))).andReturn())
                .get("avatarUrl").asText();

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(first)).andExpect(status().isNotFound());
        mvc.perform(get(second)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("removing goes back to initials, and the link stops answering")
    void removes() throws Exception {
        String address = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", address);
        String link = body(mvc.perform(upload(token, png(Color.GREEN, 200, 200))).andReturn()).get("avatarUrl").asText();

        mvc.perform(delete("/api/v1/auth/me/avatar").header("Authorization", "Bearer " + token).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());

        mvc.perform(get(link)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT avatar_source FROM app_lm_user WHERE email = ?", String.class, address))
                .isEqualTo("USER");
        assertThat(auditCount("AVATAR_REMOVED", address)).isEqualTo(1);
    }

    @Test
    @DisplayName("anything but a JPEG or PNG is refused on the file field, and nothing is stored")
    void refusesWhatIsNotAPicture() throws Exception {
        String address = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", address);
        byte[] svg = """
                <svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>"""
                .getBytes(StandardCharsets.UTF_8);

        for (byte[] content : new byte[][] {svg, "not a picture".getBytes(StandardCharsets.UTF_8), new byte[0]}) {
            mvc.perform(upload(token, content))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors.file").exists());
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM app_lm_user_avatar a JOIN app_lm_user u ON u.id = a.user_id WHERE u.email = ?",
                Integer.class, address)).isZero();
    }

    @Test
    @DisplayName("a file past the configured cap is refused with that cap, before it is decoded")
    void refusesAFileTooLarge() throws Exception {
        String token = verifiedUser("Alok Kumar", "alok@" + domain);

        mvc.perform(upload(token, new byte[5 * 1024 * 1024 + 1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.file").value("Use an image under 5 MB"));
    }

    @Test
    @DisplayName("an upload or a removal needs the session's bearer, which no other site can supply")
    void changesNeedTheSession() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/me/avatar").file(file(png(Color.RED, 50, 50)))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/auth/me/avatar").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a link that names no stored photo is the same 404, malformed or not")
    void unknownLinksAreNotFound() throws Exception {
        mvc.perform(get("/api/v1/avatars/" + "A".repeat(43))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/avatars/short")).andExpect(status().isNotFound());
    }

    private MockMultipartHttpServletRequestBuilder upload(String token, byte[] content) {
        return (MockMultipartHttpServletRequestBuilder) multipart(HttpMethod.PUT, "/api/v1/auth/me/avatar")
                .file(file(content))
                .header("Authorization", "Bearer " + token)
                .with(csrf());
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);
    }

    private static byte[] png(Color colour, int width, int height) {
        return StubPictureFetcher.pngOf(colour, width, height);
    }

    private int auditCount(String type, String address) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event event
                JOIN app_lm_user actor ON actor.id = event.actor_user_id
                WHERE event.event_type = ? AND actor.email = ?
                """, Integer.class, type, address);
    }
}
