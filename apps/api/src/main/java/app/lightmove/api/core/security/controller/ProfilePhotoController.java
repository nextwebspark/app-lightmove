package app.lightmove.api.core.security.controller;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.dto.UserResponse;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.service.UserProfileService;
import app.lightmove.api.core.storage.service.EmailImageService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Settings → Profile's photo. The caller's own row, from the principal, so no RBAC action applies — as with
 * {@code PATCH /auth/me}; a bearer route rather than under {@code /auth}, since a multipart body has no CSRF header to echo.
 */
@RestController
@RequiredArgsConstructor
public class ProfilePhotoController {

    private final UserProfileService profiles;
    private final AuthResponseAssembler assembler;

    @PostMapping(path = "/api/v1/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse upload(@AuthenticationPrincipal AuthPrincipal principal,
                               @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (file.getSize() > EmailImageService.MAX_UPLOAD_BYTES) {
            throw ApiException.of(ErrorCode.EMAIL_IMAGE_TOO_LARGE);
        }
        profiles.uploadPhoto(principal.userId(), principal.workspaceId(), bytesOf(file), request);
        return assembler.userIn(principal.userId(), principal.workspaceId());
    }

    @DeleteMapping("/api/v1/me/photo")
    public UserResponse remove(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest request) {
        profiles.removePhoto(principal.userId(), principal.workspaceId(), request);
        return assembler.userIn(principal.userId(), principal.workspaceId());
    }

    private static byte[] bytesOf(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            byte[] bytes = content.readNBytes((int) EmailImageService.MAX_UPLOAD_BYTES + 1);
            if (bytes.length > EmailImageService.MAX_UPLOAD_BYTES) {
                throw ApiException.of(ErrorCode.EMAIL_IMAGE_TOO_LARGE);
            }
            return bytes;
        } catch (IOException unreadable) {
            throw ApiException.of(ErrorCode.EMAIL_IMAGE_UNREADABLE);
        }
    }
}
