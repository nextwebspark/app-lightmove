package app.lightmove.api.core.email.render;

import app.lightmove.api.core.email.constant.EmailTone;

/** One headline count — "9 overdue" — linking to where those items are. */
public record EmailChip(int count, String label, EmailTone tone, String href) {}
