package app.lightmove.api.positiontemplate.constant;

/** Where a template in a workspace's list comes from, and whether the firm has changed it. */
public enum PositionTemplateOrigin {

    /** The LightMove library template, offered as is — library edits reach it. */
    LIBRARY,

    /** The firm's own copy of a library template, shadowing it; library edits no longer reach it. */
    CUSTOMISED,

    /** A template the firm wrote from scratch. */
    OWN,

    /** A library template the firm has taken out of its picker and its title matching. */
    HIDDEN
}
