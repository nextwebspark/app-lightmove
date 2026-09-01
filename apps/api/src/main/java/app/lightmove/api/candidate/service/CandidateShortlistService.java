package app.lightmove.api.candidate.service;

import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Asks the recruiter {@link ChatClient} to weigh one candidate against one job brief, scoped by
 * the system prompt in {@code prompts/recruiter-shortlist-system.st} — set here, per call, rather
 * than on the shared {@code ChatClient} bean, so that bean stays reusable for a future feature with
 * a different prompt. Stateless — the caller supplies both texts, nothing is read from or written
 * to the database.
 *
 * <p>Both texts are free text a person wrote, so this call is guarded through {@link LlmCallPolicy}
 * like every other. It answers in prose, so there is no schema to hold it to — the guard is all of it.
 */
@Service
public class CandidateShortlistService {

    /** Names this feature in the shared client's log line. */
    private static final String PROMPT_ID = "recruiter-shortlist";

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final LlmCallPolicy llmCalls;
    private final Consumer<ChatClient.AdvisorSpec> guardedAdvisors;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, and the resource has to be loaded here rather than in ChatClientConfig
    // so that bean stays generic.
    public CandidateShortlistService(ChatClient chatClient,
                                     @Value("classpath:prompts/recruiter-shortlist-system.st") Resource systemPrompt,
                                     LlmCallPolicy llmCalls) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.llmCalls = llmCalls;
        this.guardedAdvisors = llmCalls.forPrompt(PromptGuardSpec.prose(PROMPT_ID));
    }

    public String shortlist(String jobBrief, String candidateProfile) {
        String answer = chatClient.prompt()
                .advisors(guardedAdvisors)
                .system(systemPrompt)
                .user(user -> user.text("""
                        Job brief:
                        {jobBrief}

                        Candidate profile:
                        {candidateProfile}
                        """)
                        .param("jobBrief", jobBrief)
                        .param("candidateProfile", candidateProfile))
                .call()
                .content();

        return llmCalls.requireModelAnswer(PROMPT_ID, answer);
    }
}
