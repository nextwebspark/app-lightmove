package app.lightmove.api.notetaker.model;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.notetaker.constant.InterviewQuestionSection;
import app.lightmove.api.notetaker.constant.InterviewQuestionSource;
import app.lightmove.api.notetaker.constant.RecordGap;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One prepared question of an interview plan (V154). A fit question names its competency by the brief's panel and
 * name, as a scorecard score does; a gap question names the record's gap; a clarifier carries why it is asked.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InterviewQuestion(String id, InterviewQuestionSection section, CompetencyPanel panel, String competency,
                                RecordGap gap, String text, String listenFor, String why, boolean included,
                                InterviewQuestionSource source) {}
