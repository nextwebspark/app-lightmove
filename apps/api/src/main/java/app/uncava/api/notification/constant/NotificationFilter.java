package app.uncava.api.notification.constant;

import app.uncava.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The bell's two tabs. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum NotificationFilter implements ApiValueEnum {

    UNREAD("unread"),
    ALL("all");

    private final String value;
}
