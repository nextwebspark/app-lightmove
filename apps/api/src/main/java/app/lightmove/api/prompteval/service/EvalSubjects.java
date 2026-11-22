package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.llm.service.EvalSubject;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The prompts an eval can run: each has a subject beside its caller. */
@Component
@RequiredArgsConstructor
public class EvalSubjects {

    private final List<EvalSubject> subjects;

    public Optional<EvalSubject> of(String promptId) {
        return subjects.stream().filter(subject -> subject.promptId().equals(promptId)).findFirst();
    }
}
