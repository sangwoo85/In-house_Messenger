package com.company.messenger.domain.message;
import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageMutationController {
    private final ChatService service;
    /** Retains the old edit route so unsupported edits fail with a clear policy error. */
    @PatchMapping("/{id}")
    public ApiResponse<MessageResponse> edit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, @Valid @RequestBody EditMessageRequest request) {
        return ApiResponse.ok(service.edit(user.userId(), id, request));
    }
    /** Removes unread messages owned by the authenticated sender. */
    @DeleteMapping("/{id}")
    public ApiResponse<MessageResponse> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.delete(user.userId(), id));
    }

    /** Creates or replaces the authenticated user's one emoji selection for this message. */
    @PutMapping("/{id}/reaction")
    public ApiResponse<MessageResponse> react(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                             @Valid @RequestBody ReactionRequest request) {
        return ApiResponse.ok(service.react(user.userId(), id, request.emoji()));
    }

    /** Cancels only the authenticated user's own selection. */
    @DeleteMapping("/{id}/reaction")
    public ApiResponse<MessageResponse> removeReaction(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.ok(service.removeReaction(user.userId(), id));
    }
}
