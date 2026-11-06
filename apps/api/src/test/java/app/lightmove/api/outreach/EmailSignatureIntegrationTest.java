package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubPictureFetcher;
import app.lightmove.api.core.storage.service.EmailImageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Email signatures (docs/email-signatures.md, phase 1): the profile details they follow, the photo and logo an email
 * shows from Uncava's own address, and each consultant's signatures — theirs alone, at most one the default.
 */
@IntegrationTest
class EmailSignatureIntegrationTest extends FlowTestSupport {

    private static final String SIGNATURES = "/api/v1/me/signatures";
    private static final String LOGO_URL = "https://logos.example/al-futtaim.png";

    @Autowired @FixtureJdbc private JdbcTemplate jdbc;
    @Autowired private StubPictureFetcher pictures;

    private String yara;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        String yaraEmail = "yara@" + domain;
        workspaceId = createWorkspace(verifiedUser("Yara Haddad", yaraEmail), "Al-Futtaim");
        yara = login(yaraEmail);
    }

    @Test
    @DisplayName("Profile keeps a phone as typed and a LinkedIn profile canonical, and refuses what is neither")
    void profileContactDetails() throws Exception {
        JsonNode saved = body(mvc.perform(profile(yara, """
                        {"fullName":"Yara Haddad","title":"Head of Talent","timezone":"Asia/Dubai","locale":"en",
                         "phone":" +971 50  123 4567 ","linkedinUrl":"linkedin.com/in/YaraHaddad/?trk=x"}"""))
                .andExpect(status().isOk()).andReturn());
        assertThat(saved.get("phone").asText()).isEqualTo("+971 50 123 4567");
        assertThat(saved.get("linkedinUrl").asText()).isEqualTo("https://www.linkedin.com/in/yarahaddad");

        JsonNode untouched = body(mvc.perform(profile(yara, """
                        {"fullName":"Yara Haddad","title":"Head of Talent","timezone":"Asia/Dubai","locale":"en"}"""))
                .andExpect(status().isOk()).andReturn());
        assertThat(untouched.get("phone").asText()).isEqualTo("+971 50 123 4567");

        MvcResult badPhone = mvc.perform(profile(yara, """
                        {"fullName":"Yara Haddad","timezone":"Asia/Dubai","locale":"en","phone":"call me"}"""))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(badPhone).get("fieldErrors").has("phone")).isTrue();
        MvcResult notLinkedIn = mvc.perform(profile(yara, """
                        {"fullName":"Yara Haddad","timezone":"Asia/Dubai","locale":"en","linkedinUrl":"evil.example/in/x"}"""))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(notLinkedIn).get("fieldErrors").has("linkedinUrl")).isTrue();

        JsonNode cleared = body(mvc.perform(profile(yara, """
                        {"fullName":"Yara Haddad","timezone":"Asia/Dubai","locale":"en","phone":"","linkedinUrl":" "}"""))
                .andExpect(status().isOk()).andReturn());
        assertThat(cleared.get("phone").isNull()).isTrue();
        assertThat(cleared.get("linkedinUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("the profile photo is copied once for email, served publicly by its own token, and kept after removal")
    void photoFromAvatar() throws Exception {
        String photo = """
                {"name":"With photo","layout":"PHOTO","showPhoto":true}""";
        assertThat(previewHtml(photo)).doesNotContain("<img");

        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/me/avatar")
                        .file(new MockMultipartFile("file", "me.png", "image/png", picture(900, 600)))
                        .header("Authorization", "Bearer " + yara).with(csrf()))
                .andExpect(status().isOk());
        String first = previewHtml(photo);
        String second = previewHtml(photo);
        assertThat(first).contains("/api/v1/email-images/").doesNotContain("/api/v1/avatars/");
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from app_lm_email_image where kind = 'PHOTO'", Integer.class))
                .isOne();

        String path = first.substring(first.indexOf("/api/v1/email-images/"), first.indexOf('"',
                first.indexOf("/api/v1/email-images/")));
        MvcResult served = mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(served.getResponse().getHeader("Cache-Control")).contains("immutable", "public");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(served.getResponse().getContentAsByteArray()))
                .getWidth()).isEqualTo(192);
        mvc.perform(get("/api/v1/email-images/not-a-token")).andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/auth/me/avatar").header("Authorization", "Bearer " + yara).with(csrf()))
                .andExpect(status().isOk());
        assertThat(previewHtml(photo)).doesNotContain("<img");
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the first signature is the default, only another takes the mark, and deleting it hands it on")
    void oneDefault() throws Exception {
        String formal = create(yara, signature("Formal", "PHOTO_LOGO", false)).get("id").asText();
        String shortReply = create(yara, signature("Short reply", "MINIMAL", true)).get("id").asText();
        String third = create(yara, signature("Third", "CLASSIC", false)).get("id").asText();

        JsonNode listed = list(yara);
        assertThat(listed.get("limit").asInt()).isEqualTo(10);
        assertThat(namesWithDefault(listed)).containsExactly("Formal", "Short reply*", "Third");

        mvc.perform(post(SIGNATURES + "/" + formal + "/default").header("Authorization", "Bearer " + yara))
                .andExpect(status().isOk());
        assertThat(namesWithDefault(list(yara))).containsExactly("Formal*", "Short reply", "Third");

        mvc.perform(put(SIGNATURES + "/" + third).header("Authorization", "Bearer " + yara)
                        .contentType(MediaType.APPLICATION_JSON).content(signature("Third, renamed", "CLASSIC", true)))
                .andExpect(status().isOk());
        assertThat(namesWithDefault(list(yara))).containsExactly("Formal", "Short reply", "Third, renamed*");

        mvc.perform(put(SIGNATURES + "/" + third).header("Authorization", "Bearer " + yara)
                        .contentType(MediaType.APPLICATION_JSON).content(signature("Third, renamed", "CLASSIC", false)))
                .andExpect(status().isOk());
        assertThat(namesWithDefault(list(yara))).containsExactly("Formal", "Short reply", "Third, renamed*");

        mvc.perform(delete(SIGNATURES + "/" + third).header("Authorization", "Bearer " + yara))
                .andExpect(status().isNoContent());
        assertThat(namesWithDefault(list(yara))).containsExactly("Formal*", "Short reply");
        assertThat(shortReply).isNotBlank();
    }

    @Test
    @DisplayName("a signature is its owner's alone: a colleague reads it as missing, and a pure client reaches none")
    void ownersOnly() throws Exception {
        String formal = create(yara, signature("Formal", "CLASSIC", true)).get("id").asText();
        inviteAndAccept(yara, "Omar Saleh", "omar@" + domain, "MEMBER");
        String omar = login("omar@" + domain);

        assertThat(list(omar).get("signatures")).isEmpty();
        mvc.perform(put(SIGNATURES + "/" + formal).header("Authorization", "Bearer " + omar)
                        .contentType(MediaType.APPLICATION_JSON).content(signature("Mine now", "CLASSIC", true)))
                .andExpect(status().isNotFound());
        mvc.perform(delete(SIGNATURES + "/" + formal).header("Authorization", "Bearer " + omar))
                .andExpect(status().isNotFound());
        assertThat(namesWithDefault(list(yara))).containsExactly("Formal*");

        String client = clientRepresentative(yara, "Nadia Saleh", "nadia@client-" + domain);
        mvc.perform(get(SIGNATURES).header("Authorization", "Bearer " + client)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ten is the most anyone keeps")
    void atMostTen() throws Exception {
        for (int n = 1; n <= 10; n++) {
            create(yara, signature("Signature " + n, "MINIMAL", false));
        }
        MvcResult refused = mvc.perform(post(SIGNATURES).header("Authorization", "Bearer " + yara)
                        .contentType(MediaType.APPLICATION_JSON).content(signature("Eleven", "MINIMAL", false)))
                .andExpect(status().isConflict()).andReturn();
        assertThat(codeOf(refused)).isEqualTo("EMAIL_SIGNATURE_LIMIT_REACHED");
    }

    @Test
    @DisplayName("the preview is the server's drawing: escaped, the profile filled in, a bad address refused by name")
    void preview() throws Exception {
        mvc.perform(profile(yara, """
                        {"fullName":"Yara Haddad","title":"Head of Talent","timezone":"Asia/Dubai","locale":"en",
                         "phone":"+971 50 123 4567"}"""))
                .andExpect(status().isOk());
        JsonNode drawn = body(mvc.perform(post(SIGNATURES + "/preview").header("Authorization", "Bearer " + yara)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Formal","layout":"CLASSIC","fields":{"company":"<b>Al-Futtaim</b>"},
                                 "disclaimer":"Confidential"}"""))
                .andExpect(status().isOk()).andReturn());
        assertThat(drawn.get("html").asText()).contains("Yara Haddad", "Head of Talent", "&lt;b&gt;Al-Futtaim&lt;/b&gt;",
                "tel:+971501234567", "mailto:yara@" + domain, "Confidential");
        assertThat(drawn.has("text")).isFalse();
        assertThat(list(yara).get("signatures")).isEmpty();

        MvcResult badSite = mvc.perform(post(SIGNATURES + "/preview").header("Authorization", "Bearer " + yara)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Formal","layout":"CLASSIC","fields":{"website":"javascript:alert(1)"}}"""))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(badSite).get("fieldErrors").has("fields.website")).isTrue();
        MvcResult badPhone = mvc.perform(post(SIGNATURES + "/preview").header("Authorization", "Bearer " + yara)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Formal","layout":"CLASSIC","fields":{"phone":"call me on 4 5 6"}}"""))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(body(badPhone).get("fieldErrors").has("fields.phone")).isTrue();
    }

    @Test
    @DisplayName("the workspace logo is copied in once and served from Uncava, never linked where it was published")
    void logoCopiedOnce() throws Exception {
        jdbc.update("update app_lm_workspace set logo_url = ? where id = ?::uuid", LOGO_URL, workspaceId);
        pictures.answer(LOGO_URL, picture(800, 200));

        String first = body(mvc.perform(post(SIGNATURES + "/preview").header("Authorization", "Bearer " + yara)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Banner","layout":"LOGO_BANNER","showLogo":true}"""))
                .andExpect(status().isOk()).andReturn()).get("html").asText();
        assertThat(first).contains("/api/v1/email-images/").doesNotContain(LOGO_URL);
        create(yara, """
                {"name":"Banner","layout":"LOGO_BANNER","showLogo":true}""");
        list(yara);

        assertThat(pictures.fetched()).containsOnlyOnce(LOGO_URL);
        String logoPath = first.substring(first.indexOf(EmailImageService.PATH), first.indexOf('"',
                first.indexOf(EmailImageService.PATH)));
        mvc.perform(get(logoPath)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        assertThat(list(yara).get("sources").get("logoAvailable").asBoolean()).isTrue();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder profile(String token, String json) {
        return patch("/api/v1/auth/me").header("Authorization", "Bearer " + token).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String previewHtml(String json) throws Exception {
        return body(mvc.perform(post(SIGNATURES + "/preview").header("Authorization", "Bearer " + yara)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn()).get("html").asText();
    }

    private JsonNode create(String token, String json) throws Exception {
        return body(mvc.perform(post(SIGNATURES).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode list(String token) throws Exception {
        return body(mvc.perform(get(SIGNATURES).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
    }

    private static java.util.List<String> namesWithDefault(JsonNode listed) {
        java.util.List<String> names = new java.util.ArrayList<>();
        listed.get("signatures").forEach(signature -> names.add(signature.get("name").asText()
                + (signature.get("isDefault").asBoolean() ? "*" : "")));
        return names;
    }

    private static String signature(String name, String layout, boolean isDefault) {
        return """
                {"name":"%s","layout":"%s","showPhoto":true,"showLogo":true,"isDefault":%s}"""
                .formatted(name, layout, isDefault);
    }

    private static byte[] picture(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
