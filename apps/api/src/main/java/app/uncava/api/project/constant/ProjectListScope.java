package app.uncava.api.project.constant;

/** Whether a list of positions carries the archived ones: every caller says which, so none hides them by accident. */
public enum ProjectListScope {

    LIVE,

    WITH_ARCHIVED
}
