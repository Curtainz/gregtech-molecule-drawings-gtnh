package com.rubenverg.moldraw.core.pack;

import java.io.*;
import java.math.BigDecimal;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import com.google.gson.*;

/** Strict JSONC parser. Comments and source spans are retained separately from exact JSON values. */
public final class Jsonc {

    public record Span(int start, int end) {}

    public record Token(String kind, String raw, int start, int end) {}

    public static final class Document {

        private final String source, text;
        private final JsonElement value;
        private final Map<String, Span> spans;
        private final Map<String, Integer> starts;
        private final List<Token> tokens;

        private Document(Parser parser, JsonElement value) {
            source = parser.source;
            text = parser.text;
            this.value = value;
            spans = Map.copyOf(parser.spans);
            starts = Map.copyOf(parser.starts);
            tokens = List.copyOf(parser.tokens);
        }

        public JsonElement value() {
            return copy(value);
        }

        public JsonObject object() {
            return copy(value).getAsJsonObject();
        }

        JsonElement raw() {
            return value;
        }

        public String source() {
            return source;
        }

        public String text() {
            return text;
        }

        public List<Token> tokens() {
            return tokens;
        }

        Map<String, Span> spans() {
            return spans;
        }

        Map<String, Integer> starts() {
            return starts;
        }

        public PackException error(String pointer, String message) {
            String p = pointer;
            while (!spans.containsKey(p) && !p.isEmpty()) p = p.substring(0, p.lastIndexOf('/'));
            int offset = starts.getOrDefault(
                pointer,
                spans.getOrDefault(p, new Span(0, 0))
                    .start());
            return failure(source, text, offset, pointer + ": " + message);
        }
    }

    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    private Jsonc() {}

    public static Document read(Path file) throws IOException {
        if (Files.isSymbolicLink(file)) throw new PackException(file + ": symlinks are forbidden");
        byte[] bytes;
        try (var stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = stream.readNBytes(2_097_153);
        }
        if (bytes.length > 2_097_152) throw new PackException(file + ": file exceeds 2 MiB");
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
            return parse(file.toString(), text);
        } catch (CharacterCodingException invalid) {
            throw new PackException(file + ": invalid UTF-8");
        }
    }

    public static Document parse(String source, String text) {
        if (text.length() > 2_097_152 || text.getBytes(StandardCharsets.UTF_8).length > 2_097_152)
            throw new PackException(source + ": file exceeds 2 MiB");
        Parser parser = new Parser(source, text);
        JsonElement value = parser.value("", 0);
        if (parser.peek() != null) throw parser.fail(
            parser.peek()
                .start(),
            "Unexpected trailing token");
        return new Document(parser, value);
    }

    public static String child(String parent, String key) {
        return parent + "/"
            + key.replace("~", "~0")
                .replace("/", "~1");
    }

    public static JsonElement copy(JsonElement value) {
        if (value.isJsonObject()) {
            var result = new JsonObject();
            for (var e : value.getAsJsonObject()
                .entrySet()) result.add(e.getKey(), copy(e.getValue()));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new JsonArray();
            for (var e : value.getAsJsonArray()) result.add(copy(e));
            return result;
        }
        return value; // JsonPrimitive / JsonNull are immutable.
    }

    public static boolean sameValue(JsonElement a, JsonElement b) {
        if (a.isJsonNull() || b.isJsonNull()) return a.isJsonNull() && b.isJsonNull();
        if (a.isJsonObject() && b.isJsonObject()) {
            var left = a.getAsJsonObject();
            var right = b.getAsJsonObject();
            if (left.entrySet()
                .size()
                != right.entrySet()
                    .size())
                return false;
            for (var e : left.entrySet())
                if (!right.has(e.getKey()) || !sameValue(e.getValue(), right.get(e.getKey()))) return false;
            return true;
        }
        if (a.isJsonArray() && b.isJsonArray()) {
            var left = a.getAsJsonArray();
            var right = b.getAsJsonArray();
            if (left.size() != right.size()) return false;
            for (int i = 0; i < left.size(); i++) if (!sameValue(left.get(i), right.get(i))) return false;
            return true;
        }
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            var left = a.getAsJsonPrimitive();
            var right = b.getAsJsonPrimitive();
            if (left.isNumber() && right.isNumber()) return left.getAsBigDecimal()
                .compareTo(right.getAsBigDecimal()) == 0;
            if (left.isString() && right.isString()) return left.getAsString()
                .equals(right.getAsString());
            if (left.isBoolean() && right.isBoolean()) return left.getAsBoolean() == right.getAsBoolean();
        }
        return false;
    }

    private static PackException failure(String source, String text, int offset, String message) {
        int line = 1, column = 1;
        for (int i = text.startsWith("\uFEFF") ? 1 : 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else column++;
        }
        return new PackException(source + ":" + line + ":" + column + ": " + message);
    }

    private static final class Parser {

        final String source, text;
        final List<Token> tokens = new ArrayList<>(), significant = new ArrayList<>();
        final Map<String, Span> spans = new LinkedHashMap<>();
        final Map<String, Integer> starts = new LinkedHashMap<>();
        int position;

        Parser(String source, String text) {
            this.source = source;
            this.text = text;
            lex();
        }

        PackException fail(int offset, String message) {
            return failure(source, text, offset, message);
        }

        Token peek() {
            return position < significant.size() ? significant.get(position) : null;
        }

        Token take() {
            Token t = peek();
            if (t == null) throw fail(text.length(), "Unexpected end of input");
            position++;
            return t;
        }

        Token expect(String kind) {
            Token t = take();
            if (!t.kind.equals(kind)) throw fail(t.start, "Expected " + kind);
            return t;
        }

        void add(String kind, int from, int to) {
            Token t = new Token(kind, text.substring(from, to), from, to);
            tokens.add(t);
            if (!kind.equals("comment")) significant.add(t);
        }

        void lex() {
            int i = text.startsWith("\uFEFF") ? 1 : 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    i++;
                    continue;
                }
                int start = i;
                if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                    i += 2;
                    while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
                    add("comment", start, i);
                    continue;
                }
                if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < text.length() && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                    if (i + 1 >= text.length()) throw fail(start, "Unclosed block comment");
                    i += 2;
                    add("comment", start, i);
                    continue;
                }
                if ("{}[],:".indexOf(c) >= 0) {
                    add(String.valueOf(c), i, ++i);
                    continue;
                }
                if (c == '"') {
                    i++;
                    boolean closed = false;
                    while (i < text.length()) {
                        char x = text.charAt(i++);
                        if (x == '"') {
                            closed = true;
                            break;
                        }
                        if (x < 32) throw fail(i - 1, "Control character in string");
                        if (x == '\\') {
                            if (i >= text.length()) throw fail(start, "Unclosed string");
                            char e = text.charAt(i++);
                            if (e == 'u') {
                                for (int n = 0; n < 4; n++) {
                                    if (i >= text.length() || Character.digit(text.charAt(i++), 16) < 0)
                                        throw fail(i - 1, "Invalid Unicode escape");
                                }
                            } else if ("\"\\/bfnrt".indexOf(e) < 0) throw fail(i - 1, "Invalid string escape");
                        }
                    }
                    if (!closed) throw fail(start, "Unclosed string");
                    add("string", start, i);
                    continue;
                }
                boolean keyword = false;
                for (String word : List.of("true", "false", "null")) if (text.startsWith(word, i)) {
                    add(word, i, i + word.length());
                    i += word.length();
                    keyword = true;
                    break;
                }
                if (keyword) continue;
                Matcher number = NUMBER.matcher(text);
                number.region(i, text.length());
                if (number.lookingAt()) {
                    i = number.end();
                    if (i - start > 4096) throw fail(start, "Number exceeds 4096 characters");
                    add("number", start, i);
                    continue;
                }
                throw fail(i, "Unexpected character; only strict JSON plus comments/trailing commas is supported");
            }
        }

        JsonElement value(String pointer, int depth) {
            if (depth > 64) throw fail(peek() == null ? text.length() : peek().start, "JSON nesting exceeds 64");
            Token first = take();
            JsonElement result;
            switch (first.kind) {
                case "{" -> {
                    JsonObject object = new JsonObject();
                    Set<String> keys = new HashSet<>();
                    if (peek() != null && !peek().kind.equals("}")) while (true) {
                        Token key = expect("string");
                        String name = new JsonParser().parse(key.raw)
                            .getAsString();
                        if (!keys.add(name)) throw fail(key.start, "Duplicate property " + name);
                        expect(":");
                        String child = child(pointer, name);
                        object.add(name, value(child, depth + 1));
                        starts.put(child, key.start);
                        if (peek() == null || !peek().kind.equals(",")) break;
                        take();
                        if (peek() != null && peek().kind.equals("}")) break;
                    }
                    expect("}");
                    result = object;
                }
                case "[" -> {
                    JsonArray array = new JsonArray();
                    int index = 0;
                    if (peek() != null && !peek().kind.equals("]")) while (true) {
                        array.add(value(child(pointer, String.valueOf(index++)), depth + 1));
                        if (peek() == null || !peek().kind.equals(",")) break;
                        take();
                        if (peek() != null && peek().kind.equals("]")) break;
                    }
                    expect("]");
                    result = array;
                }
                case "string" -> result = new JsonParser().parse(first.raw);
                case "number" -> {
                    try {
                        BigDecimal number = new BigDecimal(first.raw);
                        if (Math.abs((long) number.scale()) > 4096 || (long) number.precision() - number.scale() > 4096)
                            throw fail(first.start, "Numeric magnitude exceeds 4096 digits");
                        result = new JsonPrimitive(number);
                    } catch (NumberFormatException invalid) {
                        throw fail(first.start, "Invalid exact number");
                    }
                }
                case "true" -> result = new JsonPrimitive(true);
                case "false" -> result = new JsonPrimitive(false);
                case "null" -> result = JsonNull.INSTANCE;
                default -> throw fail(first.start, "Expected a JSON value");
            }
            int end = significant.get(position - 1).end;
            spans.put(pointer, new Span(first.start, end));
            return result;
        }
    }
}
