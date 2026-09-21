/**
 * <b>Assistant turn stream — the server telling an open panel what a running turn is doing.</b> A
 * browser holds one SSE stream per turn, sends the cursor it last saw, and receives every event
 * after it in order.
 *
 * <p>It goes through Postgres for the two reasons {@code core.stream} gives — Cloud Run runs up to
 * two instances with no sticky routing, and {@code pg_notify} issued inside a transaction is
 * delivered at commit — and for one this stream adds. <b>A project stream event means "refetch",
 * which is idempotent and losable; an assistant event is the content itself.</b> Losing one loses
 * part of an answer. So the notification is a wake-up carrying only {@code (turnId, seq)}, and every
 * byte a browser receives is read back from {@code app_lm_assistant_event} by cursor. That is what
 * makes the server's scheduled ~55s close a seam rather than a gap, and what makes reopening a
 * thread tomorrow show the whole trace rather than just a final answer.
 *
 * <p><b>The contract for whoever writes the SPA hook.</b> It is {@code useProjectStream} with three
 * differences:
 * <ul>
 *   <li>It carries an {@code afterSeq} cursor across reconnects. The reconnect logic itself is
 *       identical — an ordinary server close is told from a failure by whether the {@code connected}
 *       frame was seen.
 *   <li>It must <b>not</b> coalesce. The project hook's trailing timer is correct for "refetch"
 *       hints and wrong for content.
 *   <li>It must <b>ignore a kind it does not recognise</b> rather than falling back to a full
 *       refetch. The vocabulary grows with every tool the assistant learns — {@code tool.called} and
 *       {@code tool.result} arrive with the tool surface — and V65 gives {@code kind} no CHECK
 *       precisely so that growth needs no migration and no coordinated deploy.
 * </ul>
 *
 * <p><b>A turn only makes progress while the instance has CPU.</b> Cloud Run's default is
 * request-based allocation and {@code ops/gcp/deploy.sh} does not pass
 * {@code --no-cpu-throttling}, so background work is throttled whenever no request is in flight.
 * In practice one usually is — this stream is itself a request, held for as long as the panel is
 * open — but a user who asks a question and closes the laptop on a quiet deployment may find the
 * turn stalled and later reclaimed. That is a deliberate cost decision, not an oversight: the flag
 * bills for idle CPU continuously. Revisit it when traffic keeps instances awake anyway.
 */
package app.lightmove.api.assistant.stream;
