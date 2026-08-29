package app.lightmove.api.core.llm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Asks the recruiter {@link ChatClient} (system prompt in
 * {@code prompts/recruiter-shortlist-system.st}) to weigh one candidate against one job brief.
 * Stateless — the caller supplies both texts, nothing is read from or written to the database.
 */
@Service
@RequiredArgsConstructor
public class CandidateShortlistService {

    private final ChatClient chatClient;

    public String shortlist(String jobBrief, String candidateProfile) {
        return chatClient.prompt()
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
