package app.lightmove.api.position.service;

import app.lightmove.api.position.model.Position;
import app.lightmove.api.project.model.Project;

/**
 * A brief and the mandate it belongs to, loaded together. The mandate is here because the Position
 * screen reads two fields off it: the role title, which step one also edits, and the one target date
 * (V8), which the screen only displays — so a read of any step needs both rows in hand.
 */
record PositionBrief(Project project, Position position) {
}
