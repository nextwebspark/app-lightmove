package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ResilienceSettings;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.resilience.service.VendorCallGuard;
import app.lightmove.api.core.resilience.service.VendorClientFactory;
import app.lightmove.api.core.resilience.service.VendorRateLimiter;
import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.constant.MeetingVideo;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.model.NewCalendarEvent;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ReleasedGrant;
import app.lightmove.api.outreach.model.SentEmail;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Graph gateway against recorded Microsoft answers: the consent link, a redeemed code, a first email and a
 * threaded follow-up, who wrote into a conversation, the calendar's events, free/busy and a booked call, and a refresh
 * Microsoft refuses.
 */
class MicrosoftMailboxGatewayTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final URI CALLBACK = URI.create("https://beta.uncava.com/api/v1/outreach/mailbox/callback");
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final ProviderCredentials SHARED = new ProviderCredentials(IntegrationProvider.MICROSOFT,
            CredentialMode.SHARED, "uncava-microsoft-client", "uncava-secret", null);
    private static final ProviderCredentials OWN = new ProviderCredentials(IntegrationProvider.MICROSOFT,
            CredentialMode.OWN, "firm-client", "firm-secret", "8f3a6c1e-2b4d-4e5f-9a0b-1c2d3e4f5a6b");

    private final ProviderCredentialsResolver resolver = mock(ProviderCredentialsResolver.class);
    private final MailboxTokens mailboxTokens = mock(MailboxTokens.class);
    private final RecordedMicrosoft microsoft = new RecordedMicrosoft();

    private HttpServer server;
    private MicrosoftMailboxGateway gateway;
    private OAuthProviderTokenClient tokenEndpoint;

    @BeforeEach
    void startRecordedMicrosoft() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", microsoft::answer);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.resilience()).thenReturn(new ResilienceSettings(Duration.ofSeconds(2), 0,
                Duration.ofMillis(1), 1.0, Duration.ZERO, Duration.ofMillis(1), Duration.ofSeconds(1)));
        VendorRateLimiter limiter = new VendorRateLimiter();
        VendorClientFactory factory = new VendorClientFactory(properties);
        VendorCallGuard guard = new VendorCallGuard(limiter, properties);
        tokenEndpoint = new OAuthProviderTokenClient(factory, limiter, guard,
                Map.of(IntegrationProvider.GOOGLE, base + "/google", IntegrationProvider.MICROSOFT, base + "/login",
                        IntegrationProvider.ZOOM, base + "/zoom"));
        gateway = new MicrosoftMailboxGateway(resolver, tokenEndpoint, mailboxTokens, factory, limiter, guard,
                base + "/graph", base + "/login");
        when(resolver.resolve(WORKSPACE, IntegrationProvider.MICROSOFT)).thenReturn(Optional.of(SHARED));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("the consent link signs in at any organisation for the shared app, at the firm's directory for its own")
    void theConsentLinkNamesTheRightDirectory() {
        URI shared = gateway.authorizationUri(WORKSPACE, "microsoft", "yara@firm.example", "state-1", CALLBACK);
        MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(shared).build().getQueryParams();
        assertThat(shared.getPath()).endsWith("/login/organizations/oauth2/v2.0/authorize");
        assertThat(query.getFirst("client_id")).isEqualTo("uncava-microsoft-client");
        assertThat(decoded(query.getFirst("scope")))
                .isEqualTo("offline_access User.Read Mail.ReadWrite Mail.Send Calendars.ReadWrite");
        assertThat(decoded(query.getFirst("redirect_uri"))).isEqualTo(CALLBACK.toString());
        assertThat(query.getFirst("state")).isEqualTo("state-1");
        assertThat(shared.toString()).doesNotContain("uncava-secret");

        when(resolver.resolve(WORKSPACE, IntegrationProvider.MICROSOFT)).thenReturn(Optional.of(OWN));
        URI own = gateway.authorizationUri(WORKSPACE, "microsoft", null, "state-2", CALLBACK);
        assertThat(own.getPath()).endsWith("/login/" + OWN.tenantId() + "/oauth2/v2.0/authorize");
        assertThat(UriComponentsBuilder.fromUri(own).build().getQueryParams().getFirst("client_id"))
                .isEqualTo("firm-client");
    }

    @Test
    @DisplayName("a redeemed code is a direct grant for the signed-in address, holding the refresh token")
    void aCodeRedeemsToADirectGrant() {
        GrantedMailbox granted = gateway.redeem(WORKSPACE, "microsoft", "code-1", CALLBACK);

        assertThat(MailboxGrants.isDirect(granted.grantId())).isTrue();
        assertThat(MailboxGrants.directProviderOf(granted.grantId())).contains("microsoft");
        assertThat(granted.address()).isEqualTo("yara.haddad@meridian.example");
        assertThat(granted.provider()).isEqualTo("microsoft");
        assertThat(granted.refreshToken()).isEqualTo("M.C123_refresh");
        Map<String, String> form = microsoft.lastForm();
        assertThat(form).containsEntry("grant_type", "authorization_code").containsEntry("code", "code-1")
                .containsEntry("client_secret", "uncava-secret").containsEntry("redirect_uri", CALLBACK.toString());
        assertThat(microsoft.authorizationOf("/graph/v1.0/me")).isEqualTo("Bearer eyJ0eXAi.access");
    }

    @Test
    @DisplayName("a first email is a draft then a send, and answers the immutable message id and conversation id")
    void aFirstEmailIsDraftedThenSent() throws Exception {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        SentEmail sent = gateway.send(grantId, new OutgoingEmail("priya@client.example", "A CFO role", "<p>Hi</p>"));

        assertThat(sent.messageId()).isEqualTo("AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft");
        assertThat(sent.threadId()).isEqualTo("AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==");
        JsonNode draft = JSON.readTree(microsoft.bodyOf("POST /graph/v1.0/me/messages"));
        assertThat(draft.path("subject").asString("")).isEqualTo("A CFO role");
        assertThat(draft.path("body").path("contentType").asString("")).isEqualTo("HTML");
        assertThat(draft.path("toRecipients").get(0).path("emailAddress").path("address").asString(""))
                .isEqualTo("priya@client.example");
        assertThat(microsoft.requested()).containsSubsequence("POST /graph/v1.0/me/messages",
                "POST /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft/send");
        assertThat(microsoft.preferOf("/graph/v1.0/me/messages")).contains("IdType=\"ImmutableId\"");
    }

    @Test
    @DisplayName("a follow-up is a reply drafted on the last message, addressed to the executive, in the same conversation")
    void aFollowUpRepliesInTheConversation() throws Exception {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        SentEmail sent = gateway.send(grantId, new OutgoingEmail("priya@client.example", "Re: A CFO role",
                "<p>Following up</p>", "AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft"));

        assertThat(sent.messageId()).isEqualTo("AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply");
        assertThat(sent.threadId()).isEqualTo("AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==");
        JsonNode reply = JSON.readTree(microsoft.bodyOf(
                "POST /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft/createReply"));
        assertThat(reply.path("message").path("toRecipients").get(0).path("emailAddress").path("address")
                .asString("")).isEqualTo("priya@client.example");
        assertThat(reply.path("message").path("subject").asString("")).isEqualTo("Re: A CFO role");
        JsonNode addressed = JSON.readTree(microsoft.bodyOf(
                "PATCH /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply"));
        assertThat(addressed.path("toRecipients")).hasSize(1);
        assertThat(addressed.path("toRecipients").get(0).path("emailAddress").path("address").asString(""))
                .isEqualTo("priya@client.example");
        assertThat(addressed.path("ccRecipients")).isEmpty();
        assertThat(addressed.path("bccRecipients")).isEmpty();
        assertThat(microsoft.requested()).containsSubsequence(
                "PATCH /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply",
                "POST /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply/send");
    }

    @Test
    @DisplayName("who wrote into a conversation is read by address only, from the send on, never drafts or Sent Items")
    void threadSendersAreAddressesOnly() {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        List<String> writers = gateway.senderAddressesInThread(grantId,
                "AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==", Instant.parse("2026-10-01T00:00:00Z"));

        assertThat(writers).containsExactly("priya@client.example", "postmaster@client.example");
        String query = microsoft.lastQuery();
        assertThat(query).contains("$filter=conversationId eq 'AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg=='")
                .contains("$select=from,receivedDateTime,isDraft,parentFolderId");
    }

    @Test
    @DisplayName("a refresh Microsoft refuses is final, and a refused app is never read as a dead grant")
    void refusalsAreToldApart() {
        microsoft.refreshAnswers(400, """
                {"error":"invalid_grant","error_description":"AADSTS700082: The refresh token has expired"}""");
        assertThatThrownBy(() -> tokenEndpoint.refresh(SHARED, "M.C123_refresh"))
                .isInstanceOf(ProviderGrantRefused.class);

        microsoft.refreshAnswers(401, """
                {"error":"invalid_client","error_description":"AADSTS7000215: Invalid client secret provided"}""");
        assertThatThrownBy(() -> tokenEndpoint.refresh(OWN, "M.C123_refresh"))
                .isInstanceOf(ProviderAppUnavailable.class);
        assertThat(microsoft.lastPath()).isEqualTo("/login/" + OWN.tenantId() + "/oauth2/v2.0/token");

        microsoft.refreshAnswers(429, "{}");
        assertThatThrownBy(() -> tokenEndpoint.refresh(SHARED, "M.C123_refresh"))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.RATE_LIMITED));
    }

    @Test
    @DisplayName("a send Graph refuses deletes the unsent draft; one that may have gone leaves it alone")
    void aRefusedSendDiscardsTheDraft() {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");
        OutgoingEmail email = new OutgoingEmail("priya@client.example", "A CFO role", "<p>Hi</p>");

        microsoft.sendAnswers(403);
        assertThatThrownBy(() -> gateway.send(grantId, email)).isInstanceOfSatisfying(VendorException.class,
                failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.CREDENTIALS));
        assertThat(microsoft.requested())
                .contains("DELETE /graph/v1.0/me/messages/AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft");

        microsoft.clearRequests();
        microsoft.sendAnswers(503);
        assertThatThrownBy(() -> gateway.send(grantId, email)).isInstanceOfSatisfying(VendorException.class,
                failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.UNAVAILABLE));
        assertThat(microsoft.requested()).noneMatch(request -> request.startsWith("DELETE"));
    }

    @Test
    @DisplayName("offered only where some app exists to connect through; per workspace, only where that one has one")
    void offeredOnlyWhereAnAppExists() {
        when(resolver.isAnyAppAt(IntegrationProvider.MICROSOFT)).thenReturn(false);
        assertThat(gateway.isOffered()).isFalse();
        when(resolver.isAnyAppAt(IntegrationProvider.MICROSOFT)).thenReturn(true);
        assertThat(gateway.isOffered()).isTrue();

        UUID withoutApp = UUID.randomUUID();
        when(resolver.resolve(withoutApp, IntegrationProvider.MICROSOFT)).thenReturn(Optional.empty());
        assertThat(gateway.isOfferedTo(WORKSPACE)).isTrue();
        assertThat(gateway.isOfferedTo(withoutApp)).isFalse();
    }

    @Test
    @DisplayName("calendar events are the timed, single events of every page, in UTC: no series, all-day or cancelled one")
    void calendarEventsAreTimedSingleEvents() {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        List<CalendarEvent> events = gateway.calendarEvents(grantId, Instant.parse("2026-07-05T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));

        assertThat(events).extracting(CalendarEvent::id)
                .containsExactly("040000008200E00074C5B7101A82E0080000000075A1", "AAMkPage2");
        CalendarEvent teams = events.getFirst();
        assertThat(teams.title()).isEqualTo("Confidential: first conversation");
        assertThat(teams.startsAt()).isEqualTo(Instant.parse("2026-10-06T06:00:00Z"));
        assertThat(teams.endsAt()).isEqualTo(Instant.parse("2026-10-06T06:30:00Z"));
        assertThat(teams.participantAddresses()).containsExactly("priya@client.example", "yara.haddad@meridian.example");
        assertThat(teams.joinUrl()).startsWith("https://teams.microsoft.com/l/meetup-join/");
        assertThat(teams.conferencingProvider()).isEqualTo("Microsoft Teams");
        assertThat(events.get(1).joinUrl()).isNull();
        assertThat(microsoft.preferOf("/graph/v1.0/me/calendarView")).contains("outlook.timezone=\"UTC\"")
                .contains("IdType=\"ImmutableId\"");
        String query = decoded(microsoft.lastQuery());
        assertThat(query).contains("startDateTime=2026-07-05T00:00:00Z").contains("$skip=250")
                .contains("$select=").contains("iCalUId").doesNotContain("body").doesNotContain("evil.example");
    }

    @Test
    @DisplayName("only the paging of Graph's next link is kept: the link itself is never followed")
    void onlyTheNextLinksPagingIsKept() {
        assertThat(MicrosoftMailboxGateway.pagingOf(
                "https://evil.example/v1.0/me/calendarView?startDateTime=x&%24skip=100&%24select=body"))
                .containsExactly(Map.entry("$skip", "100"));
        assertThat(MicrosoftMailboxGateway.pagingOf(
                "https://graph.microsoft.com/v1.0/me/calendarView?%24skiptoken=a+b%2Fc"))
                .containsExactly(Map.entry("$skiptoken", "a+b/c"));
        assertThat(MicrosoftMailboxGateway.pagingOf(null)).isEmpty();
    }

    @Test
    @DisplayName("free/busy offers only free and elsewhere: unknown is taken, and an unreadable calendar is a failure")
    void freeBusyFailsOnAnUnreadableCalendar() throws Exception {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");
        Instant from = Instant.parse("2026-10-05T00:00:00Z");
        Instant to = Instant.parse("2026-10-10T00:00:00Z");

        List<BusyInterval> busy = gateway.busyTimes(grantId, "yara.haddad@meridian.example", from, to);

        assertThat(busy).containsExactly(
                new BusyInterval(Instant.parse("2026-10-06T05:00:00Z"), Instant.parse("2026-10-06T06:00:00Z")),
                new BusyInterval(Instant.parse("2026-10-07T05:00:00Z"), Instant.parse("2026-10-07T05:30:00Z")),
                new BusyInterval(Instant.parse("2026-10-08T05:00:00Z"), Instant.parse("2026-10-08T05:30:00Z")));
        JsonNode body = JSON.readTree(microsoft.bodyOf("POST /graph/v1.0/me/calendar/getSchedule"));
        assertThat(body.path("schedules").get(0).asString("")).isEqualTo("yara.haddad@meridian.example");
        assertThat(body.path("startTime").path("dateTime").asString("")).isEqualTo("2026-10-05T00:00:00");
        assertThat(body.path("startTime").path("timeZone").asString("")).isEqualTo("UTC");
        microsoft.scheduleAnswersError();
        assertThatThrownBy(() -> gateway.busyTimes(grantId, "yara.haddad@meridian.example", from, to))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.UNAVAILABLE));
    }

    @Test
    @DisplayName("a booked call asks Teams where the calendar offers it, and reads the join link back")
    void aBookedCallCarriesTeams() throws Exception {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        CalendarEvent created = gateway.createEvent(grantId, new NewCalendarEvent("Confidential: first conversation",
                Instant.parse("2026-10-06T06:00:00Z"), Instant.parse("2026-10-06T06:30:00Z"), "priya@client.example",
                MeetingVideo.MICROSOFT_TEAMS));

        assertThat(created.id()).isEqualTo("040000008200E00074C5B7101A82E0080000000075A1");
        assertThat(created.joinUrl()).startsWith("https://teams.microsoft.com/");
        JsonNode body = JSON.readTree(microsoft.bodyOf("POST /graph/v1.0/me/events"));
        assertThat(body.path("subject").asString("")).isEqualTo("Confidential: first conversation");
        assertThat(body.path("start").path("dateTime").asString("")).isEqualTo("2026-10-06T06:00:00");
        assertThat(body.path("attendees").get(0).path("emailAddress").path("address").asString(""))
                .isEqualTo("priya@client.example");
        assertThat(body.path("isOnlineMeeting").asBoolean(false)).isTrue();
        assertThat(body.path("onlineMeetingProvider").asString("")).isEqualTo("teamsForBusiness");
    }

    @Test
    @DisplayName("a personal account offers no Teams: its invite goes without a link, and a refused create is tried once")
    void aPersonalAccountsInviteGoesWithoutTeams() throws Exception {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");
        microsoft.calendarOffersTeams(false);
        NewCalendarEvent call = new NewCalendarEvent("Call", Instant.parse("2026-10-06T06:00:00Z"),
                Instant.parse("2026-10-06T06:30:00Z"), "priya@client.example", MeetingVideo.MICROSOFT_TEAMS);

        gateway.createEvent(grantId, call);

        JsonNode body = JSON.readTree(microsoft.bodyOf("POST /graph/v1.0/me/events"));
        assertThat(body.has("isOnlineMeeting")).isFalse();
        assertThat(body.has("onlineMeetingProvider")).isFalse();
        microsoft.createEventAnswers(503);
        assertThatThrownBy(() -> gateway.createEvent(grantId, call)).isInstanceOf(VendorException.class);
        assertThat(microsoft.requested()).filteredOn("POST /graph/v1.0/me/events"::equals).hasSize(2);
    }

    @Test
    @DisplayName("a Zoom call puts its link in the invite's location and body, asks no Teams, and reads back as Zoom")
    void aZoomCallCarriesItsLink() throws Exception {
        String grantId = MailboxGrants.mintDirect("microsoft");
        when(mailboxTokens.accessToken(grantId)).thenReturn("access-for-yara");

        gateway.createEvent(grantId, new NewCalendarEvent("Call", Instant.parse("2026-10-06T06:00:00Z"),
                Instant.parse("2026-10-06T06:30:00Z"), "priya@client.example", MeetingVideo.ZOOM,
                "https://us05web.zoom.us/j/85746065432?pwd=abc123"));

        JsonNode body = JSON.readTree(microsoft.bodyOf("POST /graph/v1.0/me/events"));
        assertThat(body.has("isOnlineMeeting")).isFalse();
        assertThat(body.path("location").path("displayName").asString(""))
                .isEqualTo("https://us05web.zoom.us/j/85746065432?pwd=abc123");
        assertThat(body.path("body").path("content").asString("")).contains("https://us05web.zoom.us/j/85746065432");
        assertThat(microsoft.requested()).doesNotContain("GET /graph/v1.0/me/calendar");
        CalendarEvent read = MicrosoftMailboxGateway.eventOf(JSON.readTree("""
                {"id":"AAMkZoom","iCalUId":"040000008200E0zoom","subject":"Call","type":"singleInstance",
                 "isAllDay":false,"isCancelled":false,
                 "start":{"dateTime":"2026-10-06T06:00:00.0000000","timeZone":"UTC"},
                 "end":{"dateTime":"2026-10-06T06:30:00.0000000","timeZone":"UTC"},
                 "location":{"displayName":"https://us05web.zoom.us/j/85746065432?pwd=abc123"},
                 "onlineMeeting":null,"attendees":[{"emailAddress":{"address":"priya@client.example"}}]}"""));
        assertThat(read.joinUrl()).isEqualTo("https://us05web.zoom.us/j/85746065432?pwd=abc123");
        assertThat(read.conferencingProvider()).isEqualTo("Zoom");
    }

    @Test
    @DisplayName("a revoke forgets the token held in memory; Graph has nothing to call")
    void revokeForgetsTheToken() {
        String grantId = MailboxGrants.mintDirect("microsoft");

        gateway.revoke(new ReleasedGrant(grantId, null));

        verify(mailboxTokens).forget(grantId);
        assertThat(microsoft.requested()).isEmpty();
    }

    private static String decoded(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** Microsoft's answers, as its documentation records them, and every request it was sent. */
    private static final class RecordedMicrosoft {

        private static final String TEAMS_EVENT = """
                {"id":"AAMkTeams","iCalUId":"040000008200E00074C5B7101A82E0080000000075A1",
                 "subject":"Confidential: first conversation","type":"singleInstance",
                 "isAllDay":false,"isCancelled":false,
                 "start":{"dateTime":"2026-10-06T06:00:00.0000000","timeZone":"UTC"},
                 "end":{"dateTime":"2026-10-06T06:30:00.0000000","timeZone":"UTC"},
                 "attendees":[{"type":"required","emailAddress":{"name":"Priya Raman","address":"priya@client.example"}}],
                 "organizer":{"emailAddress":{"name":"Yara Haddad","address":"yara.haddad@meridian.example"}},
                 "isOnlineMeeting":true,"onlineMeetingProvider":"teamsForBusiness",
                 "onlineMeeting":{"joinUrl":"https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc%40thread.v2/0"}}""";

        private final List<String> requested = new CopyOnWriteArrayList<>();
        private final Map<String, String> bodies = new ConcurrentHashMap<>();
        private final Map<String, String> authorizations = new ConcurrentHashMap<>();
        private final Map<String, String> prefers = new ConcurrentHashMap<>();
        private volatile String lastForm = "";
        private volatile String lastQuery = "";
        private volatile String lastPath = "";
        private volatile int refreshStatus = 200;
        private volatile int sendStatus = 202;
        private volatile String refreshBody;
        private volatile int createEventStatus = 201;
        private volatile boolean scheduleError;
        private volatile boolean offersTeams = true;

        void answer(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String key = exchange.getRequestMethod() + " " + path;
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requested.add(key);
            bodies.put(key, body);
            lastPath = path;
            lastQuery = exchange.getRequestURI().getQuery() == null ? "" : exchange.getRequestURI().getQuery();
            authorizations.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            prefers.putIfAbsent(path, String.join(", ", exchange.getRequestHeaders().getOrDefault("Prefer", List.of())));

            if (path.endsWith("/oauth2/v2.0/token")) {
                lastForm = body;
                if (body.contains("grant_type=refresh_token") && refreshBody != null) {
                    respond(exchange, refreshStatus, refreshBody);
                    return;
                }
                respond(exchange, 200, """
                        {"token_type":"Bearer","scope":"Mail.ReadWrite Mail.Send User.Read Calendars.ReadWrite",
                         "expires_in":4632,"ext_expires_in":4632,"access_token":"eyJ0eXAi.access",
                         "refresh_token":"M.C123_refresh"}""");
            } else if (key.equals("GET /graph/v1.0/me/calendarView")) {
                respond(exchange, 200, lastQuery.contains("skip=250") ? """
                        {"value":[
                          {"id":"AAMkPage2","subject":"Debrief","type":"singleInstance","isAllDay":false,
                           "isCancelled":false,
                           "start":{"dateTime":"2026-10-08T06:00:00.0000000","timeZone":"UTC"},
                           "end":{"dateTime":"2026-10-08T06:30:00.0000000","timeZone":"UTC"},
                           "attendees":[{"emailAddress":{"address":"priya@client.example"}}],
                           "organizer":{"emailAddress":{"address":"yara.haddad@meridian.example"}},
                           "onlineMeeting":null,"onlineMeetingProvider":"unknown"}
                        ]}""" : """
                        {"@odata.nextLink":"https://evil.example/v1.0/me/calendarView?startDateTime=2026-07-05T00%%3A00%%3A00Z&%%24skip=250",
                         "value":[
                          %s,
                          {"id":"AAMkWeeklyOccurrence","subject":"Weekly","type":"occurrence",
                           "seriesMasterId":"AAMkWeekly","isAllDay":false,"isCancelled":false,
                           "start":{"dateTime":"2026-10-07T06:00:00.0000000","timeZone":"UTC"},
                           "end":{"dateTime":"2026-10-07T06:30:00.0000000","timeZone":"UTC"},
                           "attendees":[{"emailAddress":{"address":"priya@client.example"}}]},
                          {"id":"AAMkOffsite","subject":"Offsite","type":"singleInstance","isAllDay":true,
                           "start":{"dateTime":"2026-10-09T00:00:00.0000000","timeZone":"UTC"},
                           "end":{"dateTime":"2026-10-10T00:00:00.0000000","timeZone":"UTC"}},
                          {"id":"AAMkCancelled","subject":"Moved","type":"singleInstance","isCancelled":true,
                           "start":{"dateTime":"2026-10-09T06:00:00.0000000","timeZone":"UTC"},
                           "end":{"dateTime":"2026-10-09T06:30:00.0000000","timeZone":"UTC"}}
                        ]}""".formatted(TEAMS_EVENT));
            } else if (key.equals("POST /graph/v1.0/me/calendar/getSchedule")) {
                respond(exchange, 200, scheduleError ? """
                        {"value":[{"scheduleId":"yara.haddad@meridian.example",
                          "error":{"message":"The mailbox could not be found","responseCode":"ErrorMailRecipientNotFound"}}]}""" : """
                        {"value":[{"scheduleId":"yara.haddad@meridian.example","availabilityView":"0220",
                          "scheduleItems":[
                            {"status":"busy","start":{"dateTime":"2026-10-06T05:00:00.0000000","timeZone":"UTC"},
                             "end":{"dateTime":"2026-10-06T06:00:00.0000000","timeZone":"UTC"}},
                            {"status":"free","start":{"dateTime":"2026-10-06T07:00:00.0000000","timeZone":"UTC"},
                             "end":{"dateTime":"2026-10-06T08:00:00.0000000","timeZone":"UTC"}},
                            {"status":"tentative","start":{"dateTime":"2026-10-07T05:00:00.0000000","timeZone":"UTC"},
                             "end":{"dateTime":"2026-10-07T05:30:00.0000000","timeZone":"UTC"}},
                            {"status":"workingElsewhere","start":{"dateTime":"2026-10-07T07:00:00.0000000","timeZone":"UTC"},
                             "end":{"dateTime":"2026-10-07T08:00:00.0000000","timeZone":"UTC"}},
                            {"status":"unknown","start":{"dateTime":"2026-10-08T05:00:00.0000000","timeZone":"UTC"},
                             "end":{"dateTime":"2026-10-08T05:30:00.0000000","timeZone":"UTC"}}]}]}""");
            } else if (key.equals("GET /graph/v1.0/me/calendar")) {
                respond(exchange, 200, offersTeams
                        ? "{\"allowedOnlineMeetingProviders\":[\"teamsForBusiness\"]}"
                        : "{\"allowedOnlineMeetingProviders\":[]}");
            } else if (key.equals("POST /graph/v1.0/me/events")) {
                respond(exchange, createEventStatus, createEventStatus == 201 ? TEAMS_EVENT
                        : "{\"error\":{\"code\":\"ServiceUnavailable\"}}");
            } else if (path.equals("/graph/v1.0/me")) {
                respond(exchange, 200, """
                        {"@odata.context":"https://graph.microsoft.com/v1.0/$metadata#users(mail,userPrincipalName)/$entity",
                         "mail":"yara.haddad@meridian.example","userPrincipalName":"yhaddad@meridian.onmicrosoft.com"}""");
            } else if (key.equals("POST /graph/v1.0/me/messages")) {
                respond(exchange, 201, """
                        {"id":"AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-draft",
                         "conversationId":"AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==",
                         "internetMessageId":"<draft@meridian.example>","isDraft":true}""");
            } else if (path.endsWith("/createReply")) {
                respond(exchange, 201, """
                        {"id":"AAkALgAAAAAAHYQDEapmEc2byACqAC-EWg0A-reply",
                         "conversationId":"AAQkADAwATM0MDAAMS1iNTcwLWI2NTEtMDACLTAwCgAQAAmsg==","isDraft":true}""");
            } else if (path.endsWith("/send")) {
                respond(exchange, sendStatus, "");
            } else if (path.equals("/graph/v1.0/me/mailFolders/sentitems")) {
                respond(exchange, 200, "{\"id\":\"AAMkSentItems\"}");
            } else if (exchange.getRequestMethod().equals("PATCH") || exchange.getRequestMethod().equals("DELETE")) {
                respond(exchange, exchange.getRequestMethod().equals("PATCH") ? 200 : 204,
                        exchange.getRequestMethod().equals("PATCH") ? "{\"id\":\"patched\"}" : "");
            } else if (key.equals("GET /graph/v1.0/me/messages")) {
                respond(exchange, 200, """
                        {"value":[
                          {"receivedDateTime":"2026-09-20T09:00:00Z",
                           "from":{"emailAddress":{"name":"Old","address":"someone-earlier@client.example"}}},
                          {"receivedDateTime":"2026-10-01T09:00:00Z","isDraft":false,"parentFolderId":"AAMkSentItems",
                           "from":{"emailAddress":{"name":"Yara Haddad","address":"yhaddad@meridian.onmicrosoft.com"}}},
                          {"receivedDateTime":"2026-10-02T08:00:00Z","isDraft":true,"parentFolderId":"AAMkDrafts",
                           "from":{"emailAddress":{"name":"Yara Haddad","address":"yara.haddad@meridian.example"}}},
                          {"receivedDateTime":"2026-10-02T11:30:00Z",
                           "from":{"emailAddress":{"name":"Priya Raman","address":"priya@client.example"}}},
                          {"receivedDateTime":"2026-10-02T11:31:00Z",
                           "from":{"emailAddress":{"name":"Mail Delivery","address":"postmaster@client.example"}}}
                        ]}""");
            } else {
                respond(exchange, 404, "{\"error\":{\"code\":\"ErrorItemNotFound\"}}");
            }
        }

        void sendAnswers(int status) {
            this.sendStatus = status;
        }

        void createEventAnswers(int status) {
            this.createEventStatus = status;
        }

        void scheduleAnswersError() {
            this.scheduleError = true;
        }

        void calendarOffersTeams(boolean offers) {
            this.offersTeams = offers;
        }

        void clearRequests() {
            requested.clear();
        }

        void refreshAnswers(int status, String body) {
            this.refreshStatus = status;
            this.refreshBody = body;
        }

        Map<String, String> lastForm() {
            return UriComponentsBuilder.newInstance().query(lastForm).build().getQueryParams().toSingleValueMap()
                    .entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                            entry -> URLDecoder.decode(entry.getValue(), StandardCharsets.UTF_8)));
        }

        List<String> requested() {
            return List.copyOf(requested);
        }

        String bodyOf(String key) {
            return bodies.get(key);
        }

        String authorizationOf(String path) {
            return authorizations.get(path);
        }

        String preferOf(String path) {
            return prefers.get(path);
        }

        String lastQuery() {
            return lastQuery;
        }

        String lastPath() {
            return lastPath;
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        }
    }
}
