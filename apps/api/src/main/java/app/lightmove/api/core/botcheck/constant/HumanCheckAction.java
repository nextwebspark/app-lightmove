package app.lightmove.api.core.botcheck.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The form a human-check token was solved on; the SPA renders the widget with the same {@link #code}. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum HumanCheckAction {

    LOGIN("login"),
    SIGNUP("signup"),
    PASSWORD_RESET("password-reset"),
    INVITE_SIGNUP("invite-signup"),
    SSO("sso");

    private final String code;
}
