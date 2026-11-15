package com.closecircle.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.closecircle.api.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** SCRUM-11 acceptance criteria, checked through the HTTP API against a real database. */
class GroupIntegrationTest extends PostgresIntegrationTest {

    @Test
    void userCreatesAGroupWithAName() throws Exception {
        TestUser alex = registerUser("Alex");

        createGroup(alex, "  Weekend crew  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Weekend crew"))
                .andExpect(jsonPath("$.createdBy").value(alex.id().toString()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void creatorAutomaticallyBecomesOwnerMember() throws Exception {
        TestUser alex = registerUser("Alex");
        UUID groupId = groupId(createGroup(alex, "Family"));

        String role = jdbc.queryForObject(
                "SELECT member_role FROM group_members WHERE group_id = ? AND user_id = ?",
                String.class, groupId, alex.id());
        assertThat(role).isEqualTo("OWNER");
    }

    @Test
    void newGroupAppearsInTheCreatorsListButNotInOtherUsers() throws Exception {
        TestUser alex = registerUser("Alex");
        TestUser sam = registerUser("Sam");
        UUID groupId = groupId(createGroup(alex, "Hiking"));

        mvc.perform(get("/api/v1/groups").header("Authorization", alex.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].groupId").value(groupId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Hiking"))
                .andExpect(jsonPath("$.items[0].myRole").value("OWNER"))
                .andExpect(jsonPath("$.items[0].memberCount").value(1))
                .andExpect(jsonPath("$.nextCursor").isEmpty());

        mvc.perform(get("/api/v1/groups").header("Authorization", sam.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void listIsNewestFirstAndPagesWithoutGapsOrRepeats() throws Exception {
        TestUser alex = registerUser("Alex");
        List<String> created = new ArrayList<>();
        for (String name : List.of("First", "Second", "Third", "Fourth", "Fifth")) {
            created.add(0, groupId(createGroup(alex, name)).toString());
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            var request = get("/api/v1/groups").header("Authorization", alex.bearer()).param("limit", "2");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = body(mvc.perform(request).andExpect(status().isOk()));
            page.get("items").forEach(item -> seen.add(item.get("groupId").asText()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).containsExactlyElementsOf(created);
    }

    @Test
    void blankOrTooLongNameIsRejected() throws Exception {
        TestUser alex = registerUser("Alex");

        createGroup(alex, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        createGroup(alex, "x".repeat(101))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_LENGTH"));

        Integer groups = jdbc.queryForObject("SELECT count(*) FROM groups WHERE created_by = ?", Integer.class,
                alex.id());
        assertThat(groups).isZero();
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/groups")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCursorAndLimitAreBadRequests() throws Exception {
        TestUser alex = registerUser("Alex");

        mvc.perform(get("/api/v1/groups").header("Authorization", alex.bearer()).param("cursor", "garbage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get("/api/v1/groups").header("Authorization", alex.bearer()).param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private ResultActions createGroup(TestUser user, String name) throws Exception {
        return mvc.perform(post("/api/v1/groups")
                .header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", name))));
    }

    private UUID groupId(ResultActions created) throws Exception {
        return UUID.fromString(body(created.andExpect(status().isCreated())).get("groupId").asText());
    }
}
