package app.lightmove.api.core.security.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's clock, Settings → Profile's: what their mailboxes send by and their digest goes in. There is no
 * fallback zone: every user holds one, so a user who cannot be found is gone, not a person in a default zone.
 */
@Service
@RequiredArgsConstructor
public class UserTimezones {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID userId) {
        return users.findById(userId).map(User::zone).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }
}
