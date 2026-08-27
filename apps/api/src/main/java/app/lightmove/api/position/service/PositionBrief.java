package app.lightmove.api.position.service;

import app.lightmove.api.position.model.Position;
import app.lightmove.api.project.model.Project;

/**
 * A brief and the mandate it belongs to, loaded together. The mandate is here because two of the
 * fields the Position screen edits live on it — the role title and the one target date (V8) — so
 * every read and every write of a step needs both rows in hand.
 */
record PositionBrief(Project project, Position position) {
}
