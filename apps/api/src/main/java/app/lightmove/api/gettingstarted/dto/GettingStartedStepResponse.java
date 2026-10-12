package app.lightmove.api.gettingstarted.dto;

import app.lightmove.api.gettingstarted.constant.GettingStartedStep;
import java.time.Instant;

public record GettingStartedStepResponse(GettingStartedStep step, boolean done, boolean skipped, Instant completedAt) {}
