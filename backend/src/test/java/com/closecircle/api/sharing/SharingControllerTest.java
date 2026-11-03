package com.closecircle.api.sharing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.closecircle.api.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SharingController.class)
@Import(SecurityConfig.class)
class SharingControllerTest {
    private static final UUID GROUP_ID = UUID.fromString("2c76b551-b127-49b7-bfe7-9606f1e35da2");
    private static final UUID USER_ID = UUID.fromString("3f91d50d-98ab-4f20-b450-5f3311b28b81");
    private static final String PATH = "/api/v1/groups/{groupId}/me/sharing";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SharingService sharing;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(PATH, GROUP_ID)).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsCallersPreferences() throws Exception {
        when(sharing.getPreferences(GROUP_ID, USER_ID)).thenReturn(new SharingPreferences(
                GROUP_ID, USER_ID, true, false, Instant.parse("2026-10-25T10:30:00Z")));

        mvc.perform(get(PATH, GROUP_ID).with(caller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.sharingEnabled").value(true))
                .andExpect(jsonPath("$.shareLiveLocation").value(false))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-25T10:30:00Z"));
    }

    @Test
    void updatesCallersPreferences() throws Exception {
        when(sharing.updatePreferences(GROUP_ID, USER_ID, false, true)).thenReturn(new SharingPreferences(
                GROUP_ID, USER_ID, false, true, Instant.parse("2026-10-25T10:30:00Z")));

        mvc.perform(put(PATH, GROUP_ID).with(caller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharingEnabled\":false,\"shareLiveLocation\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharingEnabled").value(false))
                .andExpect(jsonPath("$.shareLiveLocation").value(true));
    }

    @Test
    void rejectsIncompleteUpdate() throws Exception {
        mvc.perform(put(PATH, GROUP_ID).with(caller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sharingEnabled\":true}"))
                .andExpect(status().isBadRequest());

        verify(sharing, never()).updatePreferences(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void rejectsInvalidGroupId() throws Exception {
        mvc.perform(get(PATH, "not-a-uuid").with(caller())).andExpect(status().isBadRequest());
    }

    @Test
    void reportsNonMemberAsNotFound() throws Exception {
        when(sharing.getPreferences(GROUP_ID, USER_ID)).thenThrow(new GroupNotFoundException());

        mvc.perform(get(PATH, GROUP_ID).with(caller()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void rejectsTokenWhoseSubjectIsNotAUserId() throws Exception {
        mvc.perform(get(PATH, GROUP_ID).with(jwt().jwt(token -> token.subject("someone"))))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor caller() {
        return jwt().jwt(token -> token.subject(USER_ID.toString()));
    }
}
