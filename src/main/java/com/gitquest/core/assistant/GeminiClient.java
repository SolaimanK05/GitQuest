package com.gitquest.core.assistant;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Talks to the Gemini REST API (generateContent) over plain HTTPS via the JDK's built-in
 * {@link HttpClient} — no new dependency needed. Blocking; callers must run this off the
 * JavaFX Application Thread (see {@code CommandService}), same rule as every JGit call in this app.
 */
public final class GeminiClient {

    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    // 503 (model overloaded -- Google's own documented "high demand" response) and 429 (rate
    // limited) are both transient; retrying with backoff is Google's own recommended mitigation,
    // not a workaround this app invented.
    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_BACKOFF_MILLIS = 1000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * @param systemInstruction fixed persona plus the current repo state — rebuilt fresh by the
     *                          caller on every call, since the repo can change between messages
     * @param history           the conversation so far, oldest first, NOT including the new
     *                          user message
     * @param newUserMessage    what the user just typed
     */
    public String generateReply(String systemInstruction, List<ChatMessage> history, String newUserMessage) throws Exception {
        if (!GeminiConfig.isConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY is not set.");
        }
        String requestBody = buildRequestBody(systemInstruction, history, newUserMessage);
        String url = String.format(ENDPOINT_TEMPLATE, GeminiConfig.model());
        // x-goog-api-key header, not ?key=<...> in the URL -- current Google guidance, and keeps
        // the key out of server access logs (see GeminiConfig javadoc re: never hardcode/commit it).
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", GeminiConfig.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        IOException lastTimeoutOrConnectionError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException requestFailure) {
                // A timeout or connection drop during a "high demand" spell looks just like a
                // slow/overloaded model from the caller's side -- retry it the same as a 503.
                lastTimeoutOrConnectionError = requestFailure;
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException("Gemini API request failed after " + MAX_ATTEMPTS
                            + " attempts (model likely overloaded): " + requestFailure.getMessage(), requestFailure);
                }
                backoff(attempt);
                continue;
            }
            if (response.statusCode() == 200) {
                return extractReplyText(response.body());
            }
            boolean retryable = response.statusCode() == 503 || response.statusCode() == 429;
            if (!retryable || attempt == MAX_ATTEMPTS) {
                throw new IllegalStateException("Gemini API error (HTTP " + response.statusCode() + "): " + summarizeError(response.body()));
            }
            backoff(attempt);
        }
        throw new IllegalStateException("Gemini API is unreachable after " + MAX_ATTEMPTS + " attempts.", lastTimeoutOrConnectionError);
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MILLIS * (1L << (attempt - 1))); // 1s, 2s, 4s
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request interrupted while retrying", e);
        }
    }

    private static String buildRequestBody(String systemInstruction, List<ChatMessage> history, String newUserMessage) {
        StringBuilder json = new StringBuilder();
        json.append("{\"system_instruction\":{\"parts\":[{\"text\":\"")
                .append(MiniJson.escape(systemInstruction)).append("\"}]},\"contents\":[");
        boolean first = true;
        for (ChatMessage message : history) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendTurn(json, message.role(), message.text());
        }
        if (!first) {
            json.append(',');
        }
        appendTurn(json, "user", newUserMessage);
        // "low" thinking is Google's own recommendation for straightforward Q&A/fact-retrieval
        // (medium is the default) -- this is a chat tutor answering Git questions, not solving
        // agentic coding tasks, and the extra reasoning time was contributing to request timeouts.
        json.append("],\"generationConfig\":{\"thinkingConfig\":{\"thinkingLevel\":\"low\"}}}");
        return json.toString();
    }

    private static void appendTurn(StringBuilder json, String role, String text) {
        json.append("{\"role\":\"").append(role).append("\",\"parts\":[{\"text\":\"")
                .append(MiniJson.escape(text)).append("\"}]}");
    }

    @SuppressWarnings("unchecked")
    private static String extractReplyText(String responseBody) {
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(responseBody);
        List<Object> candidates = (List<Object>) root.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates — the message may have been blocked by a safety filter.");
        }
        Map<String, Object> firstCandidate = (Map<String, Object>) candidates.get(0);
        Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
        if (content == null) {
            throw new IllegalStateException("Gemini returned an empty response (likely blocked by a safety filter).");
        }
        List<Object> parts = (List<Object>) content.get("parts");
        StringBuilder text = new StringBuilder();
        if (parts != null) {
            for (Object part : parts) {
                Map<String, Object> partMap = (Map<String, Object>) part;
                Object partText = partMap.get("text");
                if (partText != null) {
                    text.append(partText);
                }
            }
        }
        return text.toString();
    }

    @SuppressWarnings("unchecked")
    private static String summarizeError(String errorBody) {
        try {
            Map<String, Object> root = (Map<String, Object>) MiniJson.parse(errorBody);
            Map<String, Object> error = (Map<String, Object>) root.get("error");
            Object message = error != null ? error.get("message") : null;
            return message != null ? message.toString() : errorBody;
        } catch (Exception e) {
            return errorBody;
        }
    }
}
