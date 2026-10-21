package app.lightmove.api.screentext.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.screentext.constant.ScreenTextLocale;
import app.lightmove.api.screentext.dto.ScreenTextBundle;
import app.lightmove.api.screentext.dto.ScreenTextDraftRequest;
import app.lightmove.api.screentext.dto.ScreenTextEditorOverview;
import app.lightmove.api.screentext.dto.ScreenTextEntryView;
import app.lightmove.api.screentext.dto.ScreenTextPublishRequest;
import app.lightmove.api.screentext.dto.ScreenTextReleaseView;
import app.lightmove.api.screentext.service.ScreenTextEditorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Settings → Platform → Screen text, for a super admin. Gated on the platform action alone: nothing here is a workspace's. */
@RestController
@RequestMapping("/api/v1/platform/screen-text")
@RequiredArgsConstructor
public class ScreenTextLibraryController {

    private final ScreenTextEditorService editor;

    @GetMapping
    @RequirePlatformPermission(PlatformAction.SCREEN_TEXT_MANAGE)
    public ScreenTextEditorOverview overview(@RequestParam(defaultValue = "en") String locale) {
        return editor.overview(ScreenTextLocale.of(locale));
    }

    @GetMapping("/entries")
    @RequirePlatformPermission(PlatformAction.SCREEN_TEXT_MANAGE)
    public List<ScreenTextEntryView> entries(@RequestParam(defaultValue = "en") String locale,
                                             @RequestParam(required = false) String screen) {
        return editor.entries(ScreenTextLocale.of(locale), screen);
    }

    @PutMapping("/drafts/{key}")
    @RequirePlatformPermission(PlatformAction.SCREEN_TEXT_MANAGE)
    public ScreenTextEntryView saveDraft(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String key,
                                         @RequestParam(defaultValue = "en") String locale,
                                         @Valid @RequestBody ScreenTextDraftRequest request) {
        return editor.saveDraft(principal.userId(), ScreenTextLocale.of(locale), key, request);
    }

    @DeleteMapping("/drafts/{key}")
    @RequirePlatformPermission(PlatformAction.SCREEN_TEXT_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardDraft(@PathVariable String key, @RequestParam(defaultValue = "en") String locale) {
        editor.discardDraft(ScreenTextLocale.of(locale), key);
    }

    @GetMapping("/preview")
    @RequirePlatformPermission(PlatformAction.SCREEN_TEXT_MANAGE)
    public ScreenTextBundle preview(@RequestParam(defaultValue = "en") String locale) {
        return editor.preview(ScreenTextLocale.of(locale));
    }

    @GetMapping("/releases")
    @RequirePlatformPermission(PlatformAction.SCREEN_TEXT_MANAGE)
    public List<ScreenTextReleaseView> releases(@RequestParam(defaultValue = "en") String locale) {
        return editor.releases(ScreenTextLocale.of(locale));
    }

    @PostMapping("/releases")
    @RequirePlatformPermission(PlatformAction.SCREEN_TEXT_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenTextReleaseView publish(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam(defaultValue = "en") String locale,
                                         @Valid @RequestBody(required = false) ScreenTextPublishRequest request,
                                         HttpServletRequest httpRequest) {
        return editor.publish(principal.userId(), ScreenTextLocale.of(locale), request, httpRequest);
    }

    @PostMapping("/releases/{number}/restore")
    @RequirePlatformPermission(PlatformAction.SCREEN_TEXT_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenTextReleaseView restore(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable int number,
                                         @RequestParam(defaultValue = "en") String locale,
                                         HttpServletRequest httpRequest) {
        return editor.restore(principal.userId(), ScreenTextLocale.of(locale), number, httpRequest);
    }
}
