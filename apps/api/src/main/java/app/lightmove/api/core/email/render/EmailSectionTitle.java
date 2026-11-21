package app.lightmove.api.core.email.render;

import app.lightmove.api.core.email.constant.EmailTone;

/** A small label over the cards that follow it, with an optional aside on the right ("Oldest first"). */
public record EmailSectionTitle(String label, EmailTone tone, String aside) implements EmailBlock {}
