package com.company.messenger.domain.channel;
import jakarta.validation.constraints.*;
import java.util.List;
/** Carries company employee IDs to add to a group after owner authorization. */
public record InviteMembersRequest(@NotEmpty @Size(max = 100) List<@NotBlank String> memberUserIds) { }
