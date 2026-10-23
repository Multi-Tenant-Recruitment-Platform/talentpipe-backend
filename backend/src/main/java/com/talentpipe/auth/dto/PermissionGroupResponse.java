package com.talentpipe.auth.dto;

import java.util.List;

/**
 * One group of tasks in the role editor ({@code GET /api/v1/permissions}).
 *
 * @param key         stable group identifier, e.g. {@code VACANCIES}
 * @param label       heading to show, e.g. "Vacancies"
 * @param permissions the permissions in the group, in display order
 */
public record PermissionGroupResponse(String key, String label, List<Item> permissions) {

    /**
     * One permission — one checkbox in the role editor.
     *
     * @param key      what a role stores and an endpoint checks, e.g. {@code jobs.publish}
     * @param requires keys that are switched on together with this one
     */
    public record Item(String key, String label, String description, List<String> requires) {
    }
}
