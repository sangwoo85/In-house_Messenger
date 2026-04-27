package com.company.messenger.domain.file;

import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok(fileService.upload(authenticatedUser.userId(), file));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long fileId
    ) {
        return fileService.download(authenticatedUser.userId(), fileId);
    }
}

