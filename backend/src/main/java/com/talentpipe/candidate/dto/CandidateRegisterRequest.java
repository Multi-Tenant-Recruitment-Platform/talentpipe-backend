package com.talentpipe.candidate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public candidate self-registration request payload (PB-006). */
public record CandidateRegisterRequest(

        @NotBlank(message = "fullName is required")
        @Size(max = 200)
        String fullName,

        @NotBlank(message = "identityCardNumber is required")
        @Size(max = 30)
        String identityCardNumber,

        @NotBlank(message = "address is required")
        @Size(max = 500)
        String address,

        @NotBlank(message = "contactNumber is required")
        @Size(max = 20)
        String contactNumber,

        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        @Size(max = 255)
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password
) {
}
