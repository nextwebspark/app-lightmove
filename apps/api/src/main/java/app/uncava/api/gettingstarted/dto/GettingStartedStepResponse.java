package app.uncava.api.gettingstarted.dto;

import app.uncava.api.gettingstarted.constant.GettingStartedStep;
import java.time.Instant;

public record GettingStartedStepResponse(GettingStartedStep step, boolean done, boolean skipped, Instant completedAt) {}
