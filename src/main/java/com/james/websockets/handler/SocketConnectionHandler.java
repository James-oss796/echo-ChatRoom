package com.james.websockets.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocketConnectionHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRooms = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public SocketConnectionHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage incoming) throws Exception {
        JsonNode input = mapper.readTree(incoming.getPayload());
        String type = text(input, "type", "message");

        if ("join".equals(type)) {
            String roomId = safeRoom(text(input, "roomId", "general"));
            sessionRooms.put(session.getId(), roomId);
            ObjectNode joined = mapper.createObjectNode();
            joined.put("type", "joined");
            joined.put("roomId", roomId);
            if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(joined)));
            return;
        }

        String roomId = safeRoom(text(input, "roomId", sessionRooms.getOrDefault(session.getId(), "general")));
        String name = clean(text(input, "name", "Guest"), 80);
        String body = clean(text(input, "message", ""), 2000);
        String avatarUrl = clean(text(input, "avatarUrl", ""), 1000);
        if (body.isBlank()) return;

        sessionRooms.put(session.getId(), roomId);
        jdbc.update(
                "INSERT INTO chat_messages (room_id, sender_name, message_text, avatar_url) VALUES (?, ?, ?, ?)",
                roomId, name.isBlank() ? "Guest" : name, body, avatarUrl
        );

        ObjectNode output = mapper.createObjectNode();
        output.put("type", "message");
        output.put("roomId", roomId);
        output.put("name", name.isBlank() ? "Guest" : name);
        output.put("message", body);
        output.put("avatarUrl", avatarUrl);
        output.put("createdAt", Instant.now().toString());
        String payload = mapper.writeValueAsString(output);

        // Broadcast to the selected room, including sender; this keeps all clients consistent.
        for (WebSocketSession client : sessions.values()) {
            if (client.isOpen() && roomId.equals(sessionRooms.get(client.getId()))) {
                try { client.sendMessage(new TextMessage(payload)); }
                catch (Exception ignored) { /* a disconnected client will be cleaned up below */ }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        sessionRooms.remove(session.getId());
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText(fallback);
    }

    private String clean(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private String safeRoom(String value) {
        String room = clean(value == null ? "general" : value, 80).toLowerCase();
        return room.matches("[a-z0-9_-]+") ? room : "general";
    }
}
