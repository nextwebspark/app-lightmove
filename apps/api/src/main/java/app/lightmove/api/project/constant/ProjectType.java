package app.lightmove.api.project.constant;

/**
 * What a mandate is engaged to deliver, which decides how many milestones it has and what its health
 * is measured against.
 */
public enum ProjectType {

    /** A mapped executive universe, delivered as a map. One milestone: when that map is due. */
    MAPPING,

    /** A full search, run through to a shortlist. Two milestones: mapping, then shortlist delivery. */
    EXECUTIVE_SEARCH
}
