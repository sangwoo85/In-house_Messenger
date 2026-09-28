package com.company.messenger.domain.profile;

import com.company.messenger.domain.user.UserProfileResponse;
import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Authenticated profile-image endpoints, independent from chat attachment permissions. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfileImageController {
    private final ProfileImageService images;

    /** Replaces the current user's avatar with a decoded PNG/JPEG upload. */
    @PutMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserProfileResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(images.upload(user.userId(), file));
    }

    /** Removes the current user's avatar; another user's ID cannot be supplied. */
    @DeleteMapping("/me/profile-image")
    public ApiResponse<UserProfileResponse> delete(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(images.delete(user.userId()));
    }

    /** Downloads an authenticated coworker's normalized avatar. */
    @GetMapping("/{userId}/profile-image")
    public ResponseEntity<Resource> download(@PathVariable String userId) {
        return images.download(userId);
    }
}
