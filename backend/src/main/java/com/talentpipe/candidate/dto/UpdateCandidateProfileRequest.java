package com.talentpipe.candidate.dto;

import com.talentpipe.candidate.validator.ValidNic;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

/**
 * Multipart request payload for updating the authenticated candidate's profile.
 */
public class UpdateCandidateProfileRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    @NotBlank(message = "Identity card number is required")
    @ValidNic
    private String identityCardNumber;

    private String phone;

    private boolean addToTalentPool;

    private MultipartFile photo;

    private Boolean removePhoto;

    private MultipartFile cv;

    private Boolean removeCv;

    public UpdateCandidateProfileRequest() {
    }

    public UpdateCandidateProfileRequest(String fullName, String email, String identityCardNumber,
                                         String phone, boolean addToTalentPool) {
        this.fullName = fullName;
        this.email = email;
        this.identityCardNumber = identityCardNumber;
        this.phone = phone;
        this.addToTalentPool = addToTalentPool;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIdentityCardNumber() {
        return identityCardNumber;
    }

    public void setIdentityCardNumber(String identityCardNumber) {
        this.identityCardNumber = identityCardNumber;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isAddToTalentPool() {
        return addToTalentPool;
    }

    public void setAddToTalentPool(boolean addToTalentPool) {
        this.addToTalentPool = addToTalentPool;
    }

    public MultipartFile getPhoto() {
        return photo;
    }

    public void setPhoto(MultipartFile photo) {
        this.photo = photo;
    }

    public Boolean getRemovePhoto() {
        return removePhoto;
    }

    public void setRemovePhoto(Boolean removePhoto) {
        this.removePhoto = removePhoto;
    }

    public MultipartFile getCv() {
        return cv;
    }

    public void setCv(MultipartFile cv) {
        this.cv = cv;
    }

    public Boolean getRemoveCv() {
        return removeCv;
    }

    public void setRemoveCv(Boolean removeCv) {
        this.removeCv = removeCv;
    }
}
