package com.talentpipe.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link UserDirectoryService}. Pure Mockito — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class UserDirectoryServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDirectoryService userDirectory;

    private void userExists(UUID tenantId, UserStatus status) {
        User user = new User(tenantId, mock(Role.class), "ada@acme.io", "hash", "Ada", "Lovelace", status);
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));
    }

    @Test
    void activeUserOfTheTenant_isAnActiveMember() {
        userExists(TENANT, UserStatus.ACTIVE);

        assertThat(userDirectory.isActiveMember(TENANT, USER)).isTrue();
    }

    @Test
    void activeUserOfAnotherTenant_isNotAMember_sameAnswerAsAnUnknownUser() {
        userExists(UUID.randomUUID(), UserStatus.ACTIVE);

        assertThat(userDirectory.isActiveMember(TENANT, USER)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void userOfTheTenantWhoIsNotActive_isNotAnActiveMember(UserStatus status) {
        userExists(TENANT, status);

        assertThat(userDirectory.isActiveMember(TENANT, USER)).isFalse();
    }

    @Test
    void unknownUser_isNotAMember() {
        when(userRepository.findById(USER)).thenReturn(Optional.empty());

        assertThat(userDirectory.isActiveMember(TENANT, USER)).isFalse();
    }

    @Test
    void platformOperatorWithNoTenant_isNotAMemberOfAnyWorkspace() {
        userExists(null, UserStatus.ACTIVE);

        assertThat(userDirectory.isActiveMember(TENANT, USER)).isFalse();
    }

    @Test
    void nullArguments_answerFalseWithoutQuerying() {
        assertThat(userDirectory.isActiveMember(null, USER)).isFalse();
        assertThat(userDirectory.isActiveMember(TENANT, null)).isFalse();

        verify(userRepository, never()).findById(any());
    }
}
