package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.EvalReading;
import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.prompteval.constant.CalibrationCheckStatus;
import app.lightmove.api.prompteval.constant.JudgePick;
import app.lightmove.api.prompteval.constant.RubricState;
import app.lightmove.api.prompteval.dto.EvalCalibrationAnswer;
import app.lightmove.api.prompteval.dto.EvalCalibrationPair;
import app.lightmove.api.prompteval.dto.EvalCalibrationPairs;
import app.lightmove.api.prompteval.dto.EvalCalibrationReading;
import app.lightmove.api.prompteval.dto.EvalRubricCriterionDetail;
import app.lightmove.api.prompteval.dto.EvalRubricDetail;
import app.lightmove.api.prompteval.dto.EvalRubricDifference;
import app.lightmove.api.prompteval.dto.EvalRubricSummary;
import app.lightmove.api.prompteval.dto.LabelPairRequest;
import app.lightmove.api.prompteval.model.CalibrationCheck;
import app.lightmove.api.prompteval.model.CalibrationCheckQueued;
import app.lightmove.api.prompteval.model.CalibrationLabel;
import app.lightmove.api.prompteval.model.CalibrationLabelling;
import app.lightmove.api.prompteval.model.CalibrationPair;
import app.lightmove.api.prompteval.model.CalibrationPick;
import app.lightmove.api.prompteval.model.EvalDataset;
import app.lightmove.api.prompteval.model.EvalRubric;
import app.lightmove.api.prompteval.model.RubricCriterion;
import app.lightmove.api.prompteval.model.RubricStanding;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import app.lightmove.api.promptregistry.service.PromptCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Whether a rubric's judged criteria count. A person labels {@link #PAIRS} pairs of answers by hand, the judge
 * makes its own picks on the same pairs, and the criteria count toward a run's result only while the two agree on
 * {@link #THRESHOLD_PERCENT} percent of the picks, taken over every criterion together.
 */
@Service
@RequiredArgsConstructor
public class JudgeCalibrationService {

    public static final int PAIRS = 20;
    public static final int THRESHOLD_PERCENT = 80;

    private static final double PERCENT = 100;

    private final JudgeCalibrationStore store;
    private final EvalRubricStore rubrics;
    private final EvalDatasetStore datasets;
    private final EvalSubjects subjects;
    private final EvalJudge judge;
    private final PromptCatalogService catalog;
    private final LlmBudgetGuard budget;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final ObjectMapper json;

    /** Where the rubric stands now. Read again whenever it is asked: a new live judge makes every rubric stale. */
    @Transactional(readOnly = true)
    public RubricStanding standingOf(EvalRubric rubric) {
        CalibrationLabelling labelling = store.labellingOf(rubric.id(), rubric.criteria().size());
        return standingOf(rubric, labelling);
    }

    Optional<EvalRubricSummary> summaryOf(EvalRubric rubric) {
        return Optional.ofNullable(rubric)
                .map(present -> new EvalRubricSummary(present.version(), standingOf(present).state()));
    }

    EvalRubricDetail detailOf(EvalDataset dataset, EvalRubric rubric) {
        CalibrationLabelling labelling = store.labellingOf(rubric.id(), rubric.criteria().size());
        RubricStanding standing = standingOf(rubric, labelling);
        Optional<CalibrationCheck> finished = store.latestFinishedCheck(rubric.id());
        Optional<CalibrationCheck> latest = Optional.ofNullable(standing.check());
        List<CalibrationPair> pairs = finished.isPresent() ? store.pairsOf(rubric.id()) : List.of();
        List<CalibrationLabel> labels = finished.isPresent() ? store.labelsOf(rubric.id()) : List.of();
        Map<String, CalibrationPick> picks = new HashMap<>();
        finished.ifPresent(check -> store.picksOf(check.id())
                .forEach(pick -> picks.put(pick.pairId() + ":" + pick.criterionKey(), pick)));

        List<EvalRubricCriterionDetail> criteria = new ArrayList<>();
        for (RubricCriterion criterion : rubric.criteria()) {
            List<CalibrationLabel> ofCriterion = labels.stream()
                    .filter(label -> label.criterionKey().equals(criterion.key()))
                    .toList();
            Integer agreed = finished.isEmpty() ? null : (int) ofCriterion.stream()
                    .filter(label -> isAgreed(label, picks))
                    .count();
            criteria.add(new EvalRubricCriterionDetail(criterion.key(), criterion.name(), criterion.better(), agreed,
                    finished.isEmpty() ? null : ofCriterion.size()));
        }
        Map<UUID, CalibrationPair> pairById = pairs.stream()
                .collect(Collectors.toMap(CalibrationPair::id, pair -> pair));
        Map<String, String> criterionNames = rubric.criteria().stream()
                .collect(Collectors.toMap(RubricCriterion::key, RubricCriterion::name));
        List<EvalRubricDifference> differences = labels.stream()
                .filter(label -> !isAgreed(label, picks) && pairById.containsKey(label.pairId())
                        && criterionNames.containsKey(label.criterionKey()))
                .sorted((one, other) -> Integer.compare(pairById.get(one.pairId()).position(),
                        pairById.get(other.pairId()).position()))
                .map(label -> {
                    CalibrationPick pick = picks.get(label.pairId() + ":" + label.criterionKey());
                    return new EvalRubricDifference(pairById.get(label.pairId()).caseKey(), label.criterionKey(),
                            criterionNames.get(label.criterionKey()), label.pick(),
                            pick == null ? null : pick.pick(), pick == null ? null : pick.reason());
                })
                .toList();

        Optional<ServedPrompt> live = judge.liveVersion();
        boolean going = latest.map(CalibrationCheck::isGoing).orElse(false);
        return new EvalRubricDetail(rubric.id(), rubric.version(), standing.state(), THRESHOLD_PERCENT, PAIRS,
                labelling.pairs(), labelling.labelled(), standing.agreed(), standing.picks(),
                standing.agreementPercent(), criteria, labelling.lastLabelledBy(), labelling.lastLabelledAt(),
                finished.map(CalibrationCheck::finishedAt).orElse(null),
                finished.map(CalibrationCheck::judgeVersion).orElse(null),
                live.map(ServedPrompt::version).orElse(null),
                live.map(ServedPrompt::modelId).orElseGet(() -> catalog.defaultModelOf(PlatformPrompt.EVAL_JUDGE)),
                live.map(version -> version.settings().orElse(catalog.defaultsOf(PlatformPrompt.EVAL_JUDGE))
                        .temperature()).orElse(null),
                going ? store.pairsJudged(latest.get().id()) : null,
                latest.map(check -> check.status() == CalibrationCheckStatus.FAILED).orElse(false),
                labelling.pairs() > 0 || store.drawablePairs(dataset.id()) >= PAIRS,
                differences);
    }

    /**
     * Draws the rubric's pairs the first time, from the answers two versions gave to the same dev case in a
     * finished run, so the held-back cases stay unseen. Opening it again answers the pairs drawn then.
     */
    public EvalCalibrationPairs startLabelling(UUID datasetId) {
        EvalDataset dataset = requireDataset(datasetId);
        EvalRubric rubric = requireRubric(datasetId);
        transactions.executeWithoutResult(status -> {
            store.lockRubric(rubric.id());
            if (store.labellingOf(rubric.id(), rubric.criteria().size()).pairs() > 0) {
                return;
            }
            if (store.draw(rubric.id(), datasetId, PAIRS) < PAIRS) {
                throw ApiException.of(ErrorCode.EVAL_CALIBRATION_NO_PAIRS);
            }
        });
        return pairsOf(dataset, rubric);
    }

    @Transactional(readOnly = true)
    public EvalCalibrationPairs pairs(UUID datasetId) {
        return pairsOf(requireDataset(datasetId), requireRubric(datasetId));
    }

    @Transactional
    public void label(UUID userId, UUID pairId, LabelPairRequest request, HttpServletRequest httpRequest) {
        EvalRubric rubric = store.rubricOfPair(pairId).flatMap(rubrics::find)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No labelling pair " + pairId));
        Set<String> keys = rubric.criteria().stream().map(RubricCriterion::key).collect(Collectors.toSet());
        if (!request.picks().keySet().equals(keys) || request.picks().containsValue(null)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "picks",
                    "Pick an answer or a tie on every criterion");
        }
        request.picks().forEach((criterion, pick) -> store.label(pairId, criterion, pick, userId));
        audit.event(PlatformEventType.EVAL_PAIR_LABELLED).actor(userId)
                .target("eval_calibration_pair", pairId)
                .detail("rubric", rubric.id())
                .detail("dataset", rubric.datasetId())
                .from(httpRequest)
                .record();
    }

    /** Queues a check of the judge's live version against the labels; {@link JudgeCalibrationWorker} makes the calls. */
    public void check(UUID userId, UUID datasetId, HttpServletRequest httpRequest) {
        EvalRubric rubric = requireRubric(datasetId);
        CalibrationLabelling labelling = store.labellingOf(rubric.id(), rubric.criteria().size());
        if (labelling.pairs() < PAIRS || labelling.labelled() < labelling.pairs()) {
            throw ApiException.of(ErrorCode.EVAL_CALIBRATION_INCOMPLETE);
        }
        if (store.latestCheck(rubric.id()).map(CalibrationCheck::isGoing).orElse(false)) {
            throw ApiException.of(ErrorCode.EVAL_JUDGE_CHECK_IN_PROGRESS);
        }
        ServedPrompt live = judge.liveVersion()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "The eval judge has no version serving"));
        budget.require(LlmBudget.EVAL_RUN, userId);
        UUID checkId;
        try {
            checkId = transactions.execute(status -> {
                UUID id = store.queueCheck(rubric.id(), live.version(), userId);
                events.publishEvent(new CalibrationCheckQueued(id));
                return id;
            });
        } catch (DuplicateKeyException racing) {
            throw ApiException.of(ErrorCode.EVAL_JUDGE_CHECK_IN_PROGRESS);
        }
        audit.event(PlatformEventType.EVAL_JUDGE_CHECK_STARTED).actor(userId)
                .target("eval_calibration_check", checkId)
                .detail("rubric", rubric.id())
                .detail("dataset", datasetId)
                .detail("judgeVersion", live.version())
                .detail("pairs", labelling.pairs())
                .from(httpRequest)
                .record();
    }

    private RubricStanding standingOf(EvalRubric rubric, CalibrationLabelling labelling) {
        Optional<CalibrationCheck> latest = store.latestCheck(rubric.id());
        Optional<CalibrationCheck> finished = store.latestFinishedCheck(rubric.id());
        Integer agreed = finished.map(CalibrationCheck::agreed).orElse(null);
        Integer picks = finished.map(CalibrationCheck::picks).orElse(null);
        CalibrationCheck shown = latest.orElse(null);
        if (latest.map(CalibrationCheck::isGoing).orElse(false)) {
            return new RubricStanding(RubricState.CHECKING, agreed, picks, shown);
        }
        if (labelling.pairs() < PAIRS || labelling.labelled() < labelling.pairs()) {
            return new RubricStanding(RubricState.UNLABELLED, agreed, picks, shown);
        }
        if (finished.isEmpty()) {
            return new RubricStanding(RubricState.UNCHECKED, null, null, shown);
        }
        Integer live = judge.liveVersion().map(ServedPrompt::version).orElse(null);
        if (live == null || live != finished.get().judgeVersion()
                || store.isLabelChangedSince(rubric.id(), finished.get().createdAt())) {
            return new RubricStanding(RubricState.STALE, agreed, picks, shown);
        }
        boolean enough = picks != null && picks > 0 && PERCENT * agreed / picks >= THRESHOLD_PERCENT;
        return new RubricStanding(enough ? RubricState.COUNTS : RubricState.SHOWN_ONLY, agreed, picks, shown);
    }

    private EvalCalibrationPairs pairsOf(EvalDataset dataset, EvalRubric rubric) {
        Optional<EvalSubject> subject = subjects.of(dataset.promptId());
        Map<UUID, Map<String, JudgePick>> picksByPair = new HashMap<>();
        store.labelsOf(rubric.id()).forEach(label -> picksByPair
                .computeIfAbsent(label.pairId(), pairId -> new LinkedHashMap<>())
                .put(label.criterionKey(), label.pick()));
        List<EvalCalibrationPair> pairs = store.pairsOf(rubric.id()).stream()
                .map(pair -> new EvalCalibrationPair(pair.id(), pair.position(), pair.caseKey(), pair.caseName(),
                        pair.split(), json.writerWithDefaultPrettyPrinter().writeValueAsString(pair.input()),
                        answerOf(subject, pair, pair.answerA()), answerOf(subject, pair, pair.answerB()),
                        picksByPair.getOrDefault(pair.id(), Map.of())))
                .toList();
        return new EvalCalibrationPairs(dataset.id(), dataset.name(), rubric.version(), rubric.criteria(), PAIRS,
                THRESHOLD_PERCENT, pairs);
    }

    /** An answer as a person reads it: its words, else what it scored, else the answer as stored. */
    private EvalCalibrationAnswer answerOf(Optional<EvalSubject> subject, CalibrationPair pair,
                                           JsonNode answer) {
        String prose = subject.map(present -> present.proseOf(answer)).orElse(null);
        List<EvalReading> readings = prose != null ? List.of()
                : subject.map(present -> present.readingsOf(pair.input(), pair.expected(), answer)).orElse(List.of());
        if (prose == null && readings.isEmpty()) {
            prose = json.writerWithDefaultPrettyPrinter().writeValueAsString(answer);
        }
        return new EvalCalibrationAnswer(prose, readings.stream()
                .map(reading -> new EvalCalibrationReading(reading.label(), reading.score(), reading.text()))
                .toList());
    }

    private static boolean isAgreed(CalibrationLabel label, Map<String, CalibrationPick> picks) {
        CalibrationPick pick = picks.get(label.pairId() + ":" + label.criterionKey());
        return pick != null && pick.pick() == label.pick();
    }

    private EvalDataset requireDataset(UUID datasetId) {
        return datasets.dataset(datasetId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No eval dataset " + datasetId));
    }

    private EvalRubric requireRubric(UUID datasetId) {
        return rubrics.latestOf(datasetId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Eval dataset " + datasetId
                        + " has no rubric"));
    }
}
