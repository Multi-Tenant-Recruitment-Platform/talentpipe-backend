package com.talentpipe.candidate.dto;

import com.talentpipe.candidate.validator.ValidNic;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public candidate self-registration request payload (PB-006). */
public record CandidateRegisterRequest(

        @NotBlank(message = "Enter your full name.")
        @Size(max = 200, message = "Keep your full name to 200 characters or fewer.")
        String fullName,

        @NotBlank(message = "Enter your NIC number.")
        @ValidNic
        @Size(max = 20, message = "Enter your NIC number without extra characters, e.g. 200012345678 or 912345678V.")
        String identityCardNumber,

        @NotBlank(message = "Enter your address.")
        @Size(max = 500, message = "Keep your address to 500 characters or fewer.")
        String address,

        @NotBlank(message = "Enter your contact number.")
        @Size(max = 20, message = "Keep your contact number to 20 characters or fewer.")
        String contactNumber,

        @NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address, like you@example.com.")
        @Size(max = 255, message = "Keep the email address to 255 characters or fewer.")
        String email,

        @NotBlank(message = "Choose a password.")
        @Size(min = 8, max = 72, message = "Use a password of 8 to 72 characters.")
        String password
) {
}
