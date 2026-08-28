/**
 * <b>The vocabulary more than one feature is written in.</b>
 *
 * <p>A word here is not owned by the screen that happens to show it. Seniority is the clearest case: a
 * brief asks for an N-1 and a researcher records a candidate as an N-1, and those are the same claim
 * about the same ladder — so one enum answers both, and a tier added to it reaches every feature at
 * once instead of half of them.
 *
 * <p><b>Why not {@code core}.</b> {@code core} holds the machinery every feature runs on — security,
 * email, auditing, errors, rate limiting. This is not machinery; it is the domain's own language, and
 * filing it under plumbing would hide it from the person looking for what a mandate can say.
 *
 * <p>This is not a dumping ground. A value belongs here only once a second feature genuinely needs it;
 * until then it stays with the feature that owns it, which is where a reader looks first.
 */
package app.lightmove.api.common;
