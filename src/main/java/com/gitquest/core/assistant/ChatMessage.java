package com.gitquest.core.assistant;

/** One turn in the Git tutor conversation. {@code role} follows Gemini's own vocabulary: "user" or "model". */
public record ChatMessage(String role, String text) {

    public static ChatMessage user(String text) {
        return new ChatMessage("user", text);
    }

    public static ChatMessage model(String text) {
        return new ChatMessage("model", text);
    }
}
