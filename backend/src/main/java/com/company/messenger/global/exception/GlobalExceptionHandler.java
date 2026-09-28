package com.company.messenger.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadLimit(org.springframework.web.multipart.MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413).body(ErrorResponse.from(ErrorCode.FILE_TOO_LARGE));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(org.springframework.dao.DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(ErrorResponse.from(ErrorCode.CONFLICT));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status(exception.getErrorCode().getStatus())
                .body(ErrorResponse.from(exception.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "COMMON_002",
                exception.getBindingResult().getFieldError() != null
                        ? exception.getBindingResult().getFieldError().getDefaultMessage()
                        : "잘못된 요청입니다.",
                java.time.OffsetDateTime.now()
        ));
    }
}

