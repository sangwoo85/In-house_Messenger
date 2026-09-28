package com.company.messenger.domain.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A single emoji chosen from the server's supported reaction set. */
public record ReactionRequest(@NotBlank @Size(max = 16) String emoji) { }
