package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.OutreachStepState;
import java.time.Instant;

/**
 * One step of a person's run as the drawer lists it. {@code at} is when it went or is due;
 * {@code notSentBecause} names why a step will never go — {@code REPLIED}, {@code BOUNCED} or a stop reason.
 */
public record OutreachStepStateResponse(int number, String subject, OutreachStepState state, Instant at,
                                        String notSentBecause) {}
