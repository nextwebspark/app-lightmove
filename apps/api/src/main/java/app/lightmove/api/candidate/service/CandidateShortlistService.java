package app.lightmove.api.candidate.service;

import app.lightmove.api.core.llm.model.PromptGuardSpec;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Asks the recruiter {@link ChatClient} to weigh one candidate against one brief, its system prompt set per call so the
 * shared bean stays reusable; stateless. Both texts are free text, so the call is guarded through {@link LlmCallPolicy}.
 */
@Service
public class CandidateShortlistService {

    /** Names this feature in the shared client's log line. */
    private static final String PROMPT_ID = "recruiter-shortlist";

    private final ChatClient chatClient;
    private final Resource systemPrompt;
    private final LlmCallPolicy llmCalls;
    private final Consumer<ChatClient.AdvisorSpec> guardedAdvisors;

    // Hand-written: Lombok cannot put @Value on a generated constructor parameter.
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
