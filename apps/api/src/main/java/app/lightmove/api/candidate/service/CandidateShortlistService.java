package app.lightmove.api.candidate.service;

import app.lightmove.api.core.llm.config.ChatClientConfig;
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
 */
@Service
public class CandidateShortlistService {

    /** Names this feature in the shared client's log line. */
    private static final String PROMPT_ID = "recruiter-shortlist";

    private final ChatClient chatClient;
    private final Resource systemPrompt;

    // Hand-written rather than @RequiredArgsConstructor: Lombok cannot annotate a constructor
    // parameter with @Value, and the resource has to be loaded here rather than in ChatClientConfig
    // so that bean stays generic.
    public CandidateShortlistService(ChatClient chatClient,
                                     @Value("classpath:prompts/recruiter-shortlist-system.st") Resource systemPrompt) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
    }

    public String shortlist(String jobBrief, String candidateProfile) {
        return chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatClientConfig.PROMPT_ID, PROMPT_ID))
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
    }
}
