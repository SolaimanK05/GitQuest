package com.gitquest.core.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal hand-rolled JSON reader/writer for talking to the Gemini REST API — kept in-house
 * rather than pulling in a JSON library dependency. Not a general-purpose JSON library, just
 * enough to build Gemini's request shape and safely read its response shape, including
 * arbitrary LLM-generated text that may itself contain quotes/newlines/unicode.
 */
final class MiniJson {

    private MiniJson() {
    }

    static String escape(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    static Object parse(String json) {
        return new Parser(json).parseValue();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object parseValue() {
            skipWhitespace();
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char next = s.charAt(i++);
                if (next == '}') {
                    break;
                }
                if (next != ',') {
                    throw new IllegalStateException("Malformed JSON object at " + i);
                }
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char next = s.charAt(i++);
                if (next == ']') {
                    break;
                }
                if (next != ',') {
                    throw new IllegalStateException("Malformed JSON array at " + i);
                }
            }
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'u' -> {
                            String hex = s.substring(i, i + 4);
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                        default -> throw new IllegalStateException("Bad escape at " + i);
                    }
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw new IllegalStateException("Bad literal at " + i);
        }

        Object parseNull() {
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            throw new IllegalStateException("Bad literal at " + i);
        }

        Double parseNumber() {
            int start = i;
            while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            return Double.parseDouble(s.substring(start, i));
        }

        void expect(char c) {
            skipWhitespace();
            if (s.charAt(i) != c) {
                throw new IllegalStateException("Expected '" + c + "' at " + i);
            }
            i++;
        }

        char peek() {
            return s.charAt(i);
        }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
