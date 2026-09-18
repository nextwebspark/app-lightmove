package app.lightmove.api.positiontemplate.constant;

/** What an import does with one template in the file. */
public enum TemplateImportAction {

    CREATE,
    UPDATE,

    /** Workspace scope: the file changes a library template, so the firm takes its own copy of it. */
    CUSTOMISE,

    /** Identical to what the caller already has, so nothing is written — not even a customisation. */
    UNCHANGED,

    INVALID
}
