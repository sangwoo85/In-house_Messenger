package com.company.messenger.domain.message;
import jakarta.validation.constraints.*;
public record EditMessageRequest(@NotBlank @Size(max = 4000) String content) { }
