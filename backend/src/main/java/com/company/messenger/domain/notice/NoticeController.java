package com.company.messenger.domain.notice;
import com.company.messenger.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService service;
    /** Returns persisted company notices with bounded pagination and delivery metadata. */
    @GetMapping
    public ApiResponse<NoticePageResponse> getNotices(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.getNotices(page, size));
    }
}
