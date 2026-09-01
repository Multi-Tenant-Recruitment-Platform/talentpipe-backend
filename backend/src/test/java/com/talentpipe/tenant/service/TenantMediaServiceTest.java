package com.talentpipe.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.UnsupportedMediaException;
import com.talentpipe.common.storage.FileStorageService;
import com.talentpipe.common.storage.StorageProperties;
import com.talentpipe.tenant.dto.CompanyProfileResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit tests for {@link TenantMediaService}.
 * Pure Mockito — no Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class TenantMediaServiceTest {

    @Mock
    private FileStorageService storageService;

    @Mock
    private TenantService tenantService;

    @Mock
    private StorageProperties storageProperties;

    @InjectMocks
    private TenantMediaService tenantMediaService;

    private UUID tenantId;
    private CompanyProfileResponse profileNoImages;
    private CompanyProfileResponse profileWithLogo;
    private CompanyProfileResponse profileWithCover;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        profileNoImages = buildProfile(null, null);
        profileWithLogo = buildProfile("https://cdn.example.com/logo.png", null);
        profileWithCover = buildProfile(null, "https://cdn.example.com/cover.jpg");
    }

    // ---------------------------------------------------------------- uploadLogo

    @Test
    void uploadLogo_validPngFile_noExistingLogo_storesAndUpdates() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", new byte[100]);

        when(storageProperties.maxLogoBytes()).thenReturn(2_097_152L);
        when(tenantService.getProfile(tenantId)).thenReturn(profileNoImages);
        when(storageService.store(eq(tenantId), eq("logo"), anyString(), any(), anyString()))
                .thenReturn("https://cdn.example.com/logo.png");
        when(tenantService.updateLogoUrl(tenantId, "https://cdn.example.com/logo.png"))
                .thenReturn(profileWithLogo);

        CompanyProfileResponse result = tenantMediaService.uploadLogo(tenantId, file);

        assertThat(result.logoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void uploadLogo_validFile_existingLogoDeleted() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "new-logo.png", "image/png", new byte[100]);

        when(storageProperties.maxLogoBytes()).thenReturn(2_097_152L);
        when(tenantService.getProfile(tenantId)).thenReturn(profileWithLogo);
        when(storageService.store(eq(tenantId), eq("logo"), anyString(), any(), anyString()))
                .thenReturn("https://cdn.example.com/new-logo.png");
        when(tenantService.updateLogoUrl(tenantId, "https://cdn.example.com/new-logo.png"))
                .thenReturn(profileWithLogo);

        tenantMediaService.uploadLogo(tenantId, file);

        verify(storageService).delete("https://cdn.example.com/logo.png");
    }

    @Test
    void uploadLogo_unsupportedMimeType_throwsUnsupportedMediaException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> tenantMediaService.uploadLogo(tenantId, file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("application/pdf");
    }

    @Test
    void uploadLogo_nullContentType_throwsUnsupportedMediaException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.bin", null, new byte[100]);

        assertThatThrownBy(() -> tenantMediaService.uploadLogo(tenantId, file))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void uploadLogo_fileTooLarge_throwsIllegalArgumentException() {
        byte[] bigFile = new byte[3_000_000];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.png", "image/png", bigFile);

        when(storageProperties.maxLogoBytes()).thenReturn(2_097_152L);

        assertThatThrownBy(() -> tenantMediaService.uploadLogo(tenantId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Logo")
                .hasMessageContaining("2 MB");
    }

    @Test
    void uploadLogo_allowsWebpMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.webp", "image/webp", new byte[100]);

        when(storageProperties.maxLogoBytes()).thenReturn(2_097_152L);
        when(tenantService.getProfile(tenantId)).thenReturn(profileNoImages);
        when(storageService.store(eq(tenantId), eq("logo"), anyString(), any(), anyString()))
                .thenReturn("https://cdn.example.com/logo.webp");
        when(tenantService.updateLogoUrl(tenantId, "https://cdn.example.com/logo.webp"))
                .thenReturn(profileWithLogo);

        CompanyProfileResponse result = tenantMediaService.uploadLogo(tenantId, file);

        assertThat(result).isNotNull();
    }

    @Test
    void uploadLogo_allowsSvgMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.svg", "image/svg+xml", new byte[100]);

        when(storageProperties.maxLogoBytes()).thenReturn(2_097_152L);
        when(tenantService.getProfile(tenantId)).thenReturn(profileNoImages);
        when(storageService.store(eq(tenantId), eq("logo"), anyString(), any(), anyString()))
                .thenReturn("https://cdn.example.com/logo.svg");
        when(tenantService.updateLogoUrl(tenantId, "https://cdn.example.com/logo.svg"))
                .thenReturn(profileWithLogo);

        tenantMediaService.uploadLogo(tenantId, file);

        verify(storageService).store(eq(tenantId), eq("logo"), anyString(), any(), eq("image/svg+xml"));
    }

    @Test
    void uploadLogo_suspendedTenant_throwsBeforeTouchingStorage() {
        // The suspension check must run before the file is written to disk —
        // otherwise a rejected upload would still leave an orphaned file
        // (there is no transaction to roll that back on this side).
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", new byte[100]);
        doThrow(new BusinessRuleException("Profile updates are not allowed for suspended tenants"))
                .when(tenantService).assertProfileEditable(tenantId);

        assertThatThrownBy(() -> tenantMediaService.uploadLogo(tenantId, file))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(storageService);
    }

    // ---------------------------------------------------------------- uploadCover

    @Test
    void uploadCover_validJpegFile_noExistingCover_storesAndUpdates() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", new byte[200]);

        when(storageProperties.maxCoverBytes()).thenReturn(4_194_304L);
        when(tenantService.getProfile(tenantId)).thenReturn(profileNoImages);
        when(storageService.store(eq(tenantId), eq("cover"), anyString(), any(), anyString()))
                .thenReturn("https://cdn.example.com/cover.jpg");
        when(tenantService.updateCoverImageUrl(tenantId, "https://cdn.example.com/cover.jpg"))
                .thenReturn(profileWithCover);

        CompanyProfileResponse result = tenantMediaService.uploadCover(tenantId, file);

        assertThat(result.coverImageUrl()).isEqualTo("https://cdn.example.com/cover.jpg");
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void uploadCover_existingCoverDeleted_beforeStoringNew() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "new-cover.jpg", "image/jpeg", new byte[200]);

        when(storageProperties.maxCoverBytes()).thenReturn(4_194_304L);
        when(tenantService.getProfile(tenantId)).thenReturn(profileWithCover);
        when(storageService.store(eq(tenantId), eq("cover"), anyString(), any(), anyString()))
                .thenReturn("https://cdn.example.com/new-cover.jpg");
        when(tenantService.updateCoverImageUrl(tenantId, "https://cdn.example.com/new-cover.jpg"))
                .thenReturn(profileWithCover);

        tenantMediaService.uploadCover(tenantId, file);

        verify(storageService).delete("https://cdn.example.com/cover.jpg");
    }

    @Test
    void uploadCover_unsupportedMimeType_throwsUnsupportedMediaException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.gif", "image/gif", new byte[200]);

        assertThatThrownBy(() -> tenantMediaService.uploadCover(tenantId, file))
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessageContaining("image/gif");
    }

    @Test
    void uploadCover_fileTooLarge_throwsIllegalArgumentException() {
        byte[] bigFile = new byte[5_000_000];
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", bigFile);

        when(storageProperties.maxCoverBytes()).thenReturn(4_194_304L);

        assertThatThrownBy(() -> tenantMediaService.uploadCover(tenantId, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cover image")
                .hasMessageContaining("4 MB");
    }

    @Test
    void uploadCover_suspendedTenant_throwsBeforeTouchingStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.jpg", "image/jpeg", new byte[200]);
        doThrow(new BusinessRuleException("Profile updates are not allowed for suspended tenants"))
                .when(tenantService).assertProfileEditable(tenantId);

        assertThatThrownBy(() -> tenantMediaService.uploadCover(tenantId, file))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(storageService);
    }

    // ---------------------------------------------------------------- deleteLogo

    @Test
    void deleteLogo_withExistingLogo_deletesFromStorageAndClearsUrl() {
        when(tenantService.getProfile(tenantId)).thenReturn(profileWithLogo);
        doNothing().when(tenantService).clearLogoUrl(tenantId);

        tenantMediaService.deleteLogo(tenantId);

        verify(storageService).delete("https://cdn.example.com/logo.png");
        verify(tenantService).clearLogoUrl(tenantId);
    }

    @Test
    void deleteLogo_withNoExistingLogo_skipsStorageDeletion() {
        when(tenantService.getProfile(tenantId)).thenReturn(profileNoImages);
        doNothing().when(tenantService).clearLogoUrl(tenantId);

        tenantMediaService.deleteLogo(tenantId);

        verify(storageService, never()).delete(anyString());
        verify(tenantService).clearLogoUrl(tenantId);
    }

    @Test
    void deleteLogo_suspendedTenant_throwsAndSkipsStorageDeletion() {
        doThrow(new BusinessRuleException("Profile updates are not allowed for suspended tenants"))
                .when(tenantService).assertProfileEditable(tenantId);

        assertThatThrownBy(() -> tenantMediaService.deleteLogo(tenantId))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(storageService);
    }

    // ---------------------------------------------------------------- deleteCover

    @Test
    void deleteCover_withExistingCover_deletesFromStorageAndClearsUrl() {
        when(tenantService.getProfile(tenantId)).thenReturn(profileWithCover);
        doNothing().when(tenantService).clearCoverImageUrl(tenantId);

        tenantMediaService.deleteCover(tenantId);

        verify(storageService).delete("https://cdn.example.com/cover.jpg");
        verify(tenantService).clearCoverImageUrl(tenantId);
    }

    @Test
    void deleteCover_withNoExistingCover_skipsStorageDeletion() {
        when(tenantService.getProfile(tenantId)).thenReturn(profileNoImages);
        doNothing().when(tenantService).clearCoverImageUrl(tenantId);

        tenantMediaService.deleteCover(tenantId);

        verify(storageService, never()).delete(anyString());
        verify(tenantService).clearCoverImageUrl(tenantId);
    }

    @Test
    void deleteCover_suspendedTenant_throwsAndSkipsStorageDeletion() {
        doThrow(new BusinessRuleException("Profile updates are not allowed for suspended tenants"))
                .when(tenantService).assertProfileEditable(tenantId);

        assertThatThrownBy(() -> tenantMediaService.deleteCover(tenantId))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(storageService);
    }

    // ---------------------------------------------------------------- helpers

    private CompanyProfileResponse buildProfile(String logoUrl, String coverImageUrl) {
        return new CompanyProfileResponse(
                tenantId, "Acme Corp", "acme", "STANDARD", "ACTIVE",
                logoUrl, coverImageUrl, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Instant.now());
    }
}
