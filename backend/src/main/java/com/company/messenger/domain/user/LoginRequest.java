package com.company.messenger.domain.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credentials are forwarded to the business system and never stored in the messenger database. */
public record LoginRequest(
        @JsonAlias("userId") @NotBlank @Size(max = 50) String emprId,
        @NotBlank @Size(max = 1000) String password
) { }
