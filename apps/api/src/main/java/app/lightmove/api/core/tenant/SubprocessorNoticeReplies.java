package app.lightmove.api.core.tenant;

/** What came back from one sub-processor notice across every workspace: counts only (V248). */
public record SubprocessorNoticeReplies(long emailed, long owed, long objections) {

    public static final SubprocessorNoticeReplies NONE = new SubprocessorNoticeReplies(0, 0, 0);
}
