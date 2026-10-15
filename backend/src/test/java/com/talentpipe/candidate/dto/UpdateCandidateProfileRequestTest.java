package com.talentpipe.candidate.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UpdateCandidateProfileRequestTest {

    @Test
    void gettersAndSettersWorkAsExpected() {
        UpdateCandidateProfileRequest request = new UpdateCandidateProfileRequest();
        request.setFullName("Kasun Perera");
        request.setEmail("kasun@example.com");
        request.setIdentityCardNumber("200012345678");
        request.setPhone("+94 77 123 4567");
        request.setAddToTalentPool(true);

        MockMultipartFile photo = new MockMultipartFile("photo", "pic.jpg", "image/jpeg", new byte[]{1, 2});
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", new byte[]{3, 4});

        request.setPhoto(photo);
        request.setRemovePhoto(false);
        request.setCv(cv);
        request.setRemoveCv(false);

        assertThat(request.getFullName()).isEqualTo("Kasun Perera");
        assertThat(request.getEmail()).isEqualTo("kasun@example.com");
        assertThat(request.getIdentityCardNumber()).isEqualTo("200012345678");
        assertThat(request.getPhone()).isEqualTo("+94 77 123 4567");
        assertThat(request.isAddToTalentPool()).isTrue();
        assertThat(request.getPhoto()).isEqualTo(photo);
        assertThat(request.getRemovePhoto()).isFalse();
        assertThat(request.getCv()).isEqualTo(cv);
        assertThat(request.getRemoveCv()).isFalse();
    }

    @Test
    void parameterConstructorInitializesFields() {
        UpdateCandidateProfileRequest request = new UpdateCandidateProfileRequest(
                "Kasun Perera",
                "kasun@example.com",
                "200012345678",
                "+94 77 123 4567",
                true);

        assertThat(request.getFullName()).isEqualTo("Kasun Perera");
        assertThat(request.getEmail()).isEqualTo("kasun@example.com");
        assertThat(request.getIdentityCardNumber()).isEqualTo("200012345678");
        assertThat(request.getPhone()).isEqualTo("+94 77 123 4567");
        assertThat(request.isAddToTalentPool()).isTrue();
    }
}
