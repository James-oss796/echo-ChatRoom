package com.james.websockets.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatRestController {
    private final JdbcTemplate jdbc;

    public ChatRestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/rooms")
    public List<Map<String, Object>> rooms() {
        // MVP room list. Add/remove rooms here without adding more backend complexity.
        return List.of(
                Map.of("id", "general", "name", "General", "description", "The main room"),
                Map.of("id", "tech", "name", "Tech & Build", "description", "Code, ideas and projects"),
                Map.of("id", "random", "name", "Off Topic", "description", "Everything else")
        );
    }

    @GetMapping("/rooms/{roomId}/messages")
    public List<Map<String, Object>> messages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "50") int limit) {
        String safeRoom = roomId == null ? "general" : roomId.toLowerCase();
        if (!safeRoom.matches("[a-z0-9_-]{1,80}")) safeRoom = "general";
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.queryForList(
                "SELECT id, room_id AS \"roomId\", sender_name AS name, message_text AS message, " +
                        "avatar_url AS \"avatarUrl\", created_at AS \"createdAt\" " +
                        "FROM (SELECT * FROM chat_messages WHERE room_id = ? ORDER BY created_at DESC LIMIT ?) m " +
                        "ORDER BY created_at ASC",
                safeRoom, safeLimit
        );
    }
}
