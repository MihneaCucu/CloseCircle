package com.closecircle.api.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SharingServiceTest {
    private final ShareRepository shares = mock(ShareRepository.class);
    private final SharingService service = new SharingService(shares);

    private final UUID groupId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @Test
    void getPreferencesRejectsNonMembers() {
        when(shares.isGroupMember(groupId, viewerId)).thenReturn(false);

        assertThatThrownBy(() -> service.getPreferences(groupId, viewerId))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void memberWithoutSavedSettingsIsNotSharing() {
        when(shares.isGroupMember(groupId, viewerId)).thenReturn(true);
        when(shares.findByGroupIdAndUserId(groupId, viewerId)).thenReturn(Optional.empty());

        var preferences = service.getPreferences(groupId, viewerId);

        assertThat(preferences.sharingEnabled()).isFalse();
        assertThat(preferences.shareLiveLocation()).isFalse();
        assertThat(preferences.updatedAt()).isNull();
    }

    @Test
    void updatePreferencesRejectsNonMembersWithoutWriting() {
        when(shares.isGroupMember(groupId, viewerId)).thenReturn(false);

        assertThatThrownBy(() -> service.updatePreferences(groupId, viewerId, true, true))
                .isInstanceOf(GroupNotFoundException.class);
        verify(shares, never()).upsert(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void updatePreferencesReportsMembershipRemovedDuringWriteAsNotFound() {
        when(shares.isGroupMember(groupId, viewerId)).thenReturn(true);
        doThrow(new DataIntegrityViolationException("fk"))
                .when(shares).upsert(groupId, viewerId, true, true);

        assertThatThrownBy(() -> service.updatePreferences(groupId, viewerId, true, true))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void nonMemberCannotViewLocations() {
        when(shares.isGroupMember(groupId, viewerId)).thenReturn(false);

        assertThat(service.canViewLocation(groupId, viewerId, memberId)).isFalse();
    }

    @Test
    void memberCanViewOwnLocation() {
        when(shares.isGroupMember(groupId, viewerId)).thenReturn(true);

        assertThat(service.canViewLocation(groupId, viewerId, viewerId)).isTrue();
    }

    @Test
    void memberCannotViewMemberWithoutSavedSettings() {
        when(shares.isGroupMember(groupId, viewerId)).thenReturn(true);
        when(shares.findByGroupIdAndUserId(groupId, memberId)).thenReturn(Optional.empty());

        assertThat(service.canViewLocation(groupId, viewerId, memberId)).isFalse();
    }

    @Test
    void findSharingMemberIdsReturnsRepositoryResult() {
        when(shares.findSharingUserIds(groupId)).thenReturn(List.of(memberId));

        assertThat(service.findSharingMemberIds(groupId)).containsExactly(memberId);
    }
}
