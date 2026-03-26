package com.yomahub.liteflowhelper.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * LiteFlow EL formatter used to keep nested expressions readable after XML formatting.
 */
public final class LiteFlowElFormatter {

    private static final int INLINE_LENGTH_THRESHOLD = 40;

    private LiteFlowElFormatter() {
    }

    public static String formatChainBody(String rawText, String tagIndent, String indentUnit) {
        String normalized = normalizeLineSeparators(rawText);
        String expression = normalized.trim();
        if (expression.isEmpty()) {
            return normalized;
        }

        String formatted = formatExpression(expression, indentUnit);
        String bodyIndent = tagIndent + indentUnit;
        String indentedBody = indentLines(formatted, bodyIndent);
        return "\n" + indentedBody + "\n" + tagIndent;
    }

    public static String formatExpression(String rawText, String indentUnit) {
        String normalized = normalizeLineSeparators(rawText).trim();
        if (normalized.isEmpty()) {
            return "";
        }

        List<Token> tokens = tokenize(normalized);
        if (tokens.isEmpty()) {
            return normalized;
        }

        boolean[] multilineCall = detectMultilineCalls(tokens, normalized);
        StringBuilder result = new StringBuilder();
        Deque<Boolean> multilineStack = new ArrayDeque<>();
        int multilineDepth = 0;
        Token previous = null;

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            switch (token.type) {
                case LPAREN -> {
                    appendToken(result, previous, token);
                    result.append(token.text);

                    boolean multiline = multilineCall[i];
                    multilineStack.push(multiline);
                    if (multiline) {
                        multilineDepth++;
                        appendNewLine(result, multilineDepth, indentUnit);
                    }
                }
                case RPAREN -> {
                    boolean multiline = !multilineStack.isEmpty() && multilineStack.pop();
                    if (multiline) {
                        multilineDepth = Math.max(0, multilineDepth - 1);
                        appendNewLine(result, multilineDepth, indentUnit);
                    }
                    appendToken(result, previous, token);
                    result.append(token.text);
                }
                case COMMA -> {
                    result.append(token.text);
                    if (!multilineStack.isEmpty() && multilineStack.peek()) {
                        appendNewLine(result, multilineDepth, indentUnit);
                    } else {
                        result.append(' ');
                    }
                }
                case SEMICOLON -> {
                    result.append(token.text);
                    if (hasMoreTokens(tokens, i + 1)) {
                        appendNewLine(result, 0, indentUnit);
                    }
                }
                case COMMENT -> {
                    appendToken(result, previous, token);
                    result.append(token.text);
                    if (token.text.startsWith("//") && hasMoreTokens(tokens, i + 1)) {
                        appendNewLine(result, multilineDepth, indentUnit);
                    }
                }
                default -> {
                    appendToken(result, previous, token);
                    result.append(token.text);
                }
            }

            previous = token;
        }

        return result.toString().trim();
    }

    private static boolean hasMoreTokens(List<Token> tokens, int startIndex) {
        for (int i = startIndex; i < tokens.size(); i++) {
            if (!tokens.get(i).text.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static void appendToken(StringBuilder result, Token previous, Token current) {
        if (needsSpace(previous, current, result)) {
            result.append(' ');
        }
    }

    private static boolean needsSpace(Token previous, Token current, StringBuilder result) {
        if (previous == null || result.length() == 0) {
            return false;
        }

        char lastChar = result.charAt(result.length() - 1);
        if (lastChar == '\n' || lastChar == ' ' || lastChar == '\t') {
            return false;
        }

        if (previous.type == TokenType.DOT || current.type == TokenType.DOT) {
            return false;
        }
        if (current.type == TokenType.LPAREN || previous.type == TokenType.LPAREN) {
            return false;
        }
        if (current.type == TokenType.RPAREN || current.type == TokenType.COMMA || current.type == TokenType.SEMICOLON) {
            return false;
        }
        if (previous.type == TokenType.COMMA || previous.type == TokenType.SEMICOLON) {
            return false;
        }
        return true;
    }

    private static void appendNewLine(StringBuilder result, int depth, String indentUnit) {
        trimTrailingSpaces(result);
        if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
            result.append('\n');
        }
        result.append(indentUnit.repeat(Math.max(depth, 0)));
    }

    private static void trimTrailingSpaces(StringBuilder result) {
        while (result.length() > 0) {
            char c = result.charAt(result.length() - 1);
            if (c != ' ' && c != '\t') {
                break;
            }
            result.deleteCharAt(result.length() - 1);
        }
    }

    private static boolean[] detectMultilineCalls(List<Token> tokens, String source) {
        boolean[] multilineCall = new boolean[tokens.size()];
        int[] matchingParens = new int[tokens.size()];
        Arrays.fill(matchingParens, -1);

        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type == TokenType.LPAREN) {
                stack.push(i);
            } else if (tokens.get(i).type == TokenType.RPAREN && !stack.isEmpty()) {
                int leftIndex = stack.pop();
                matchingParens[leftIndex] = i;
            }
        }

        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).type != TokenType.LPAREN || matchingParens[i] < 0) {
                continue;
            }

            int rightIndex = matchingParens[i];
            Token left = tokens.get(i);
            Token right = tokens.get(rightIndex);
            String innerText = source.substring(left.endOffset, right.startOffset);

            boolean hasNestedCall = false;
            int compactLength = 0;
            for (int j = i + 1; j < rightIndex; j++) {
                Token token = tokens.get(j);
                if (token.type == TokenType.LPAREN) {
                    hasNestedCall = true;
                }
                if (token.type != TokenType.COMMENT) {
                    compactLength += token.text.length();
                }
            }

            multilineCall[i] = innerText.contains("\n") || hasNestedCall || compactLength > INLINE_LENGTH_THRESHOLD;
        }

        return multilineCall;
    }

    private static String indentLines(String text, String indent) {
        String[] lines = normalizeLineSeparators(text).split("\n", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                result.append('\n');
            }
            result.append(indent).append(lines[i]);
        }
        return result.toString();
    }

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int length = source.length();
        int index = 0;

        while (index < length) {
            char current = source.charAt(index);

            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }

            if (current == '/' && index + 1 < length) {
                char next = source.charAt(index + 1);
                if (next == '/') {
                    int start = index;
                    index += 2;
                    while (index < length && source.charAt(index) != '\n') {
                        index++;
                    }
                    tokens.add(new Token(TokenType.COMMENT, source.substring(start, index), start, index));
                    continue;
                }
                if (next == '*') {
                    int start = index;
                    index += 2;
                    while (index + 1 < length && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                        index++;
                    }
                    index = Math.min(length, index + 2);
                    tokens.add(new Token(TokenType.COMMENT, source.substring(start, index), start, index));
                    continue;
                }
            }

            if (current == '{' && index + 1 < length && source.charAt(index + 1) == '{') {
                int start = index;
                index += 2;
                while (index + 1 < length && !(source.charAt(index) == '}' && source.charAt(index + 1) == '}')) {
                    index++;
                }
                index = Math.min(length, index + 2);
                tokens.add(new Token(TokenType.PLACEHOLDER, source.substring(start, index), start, index));
                continue;
            }

            if (current == '\'' || current == '"') {
                int start = index;
                char quote = current;
                index++;
                while (index < length) {
                    char c = source.charAt(index);
                    if (c == '\\' && index + 1 < length) {
                        index += 2;
                        continue;
                    }
                    index++;
                    if (c == quote) {
                        break;
                    }
                }
                tokens.add(new Token(TokenType.STRING, source.substring(start, index), start, index));
                continue;
            }

            TokenType specialType = switch (current) {
                case '(' -> TokenType.LPAREN;
                case ')' -> TokenType.RPAREN;
                case ',' -> TokenType.COMMA;
                case ';' -> TokenType.SEMICOLON;
                case '.' -> TokenType.DOT;
                case '=' -> TokenType.EQUALS;
                default -> null;
            };

            if (specialType != null) {
                tokens.add(new Token(specialType, String.valueOf(current), index, index + 1));
                index++;
                continue;
            }

            int start = index;
            while (index < length) {
                char c = source.charAt(index);
                if (Character.isWhitespace(c) || c == '(' || c == ')' || c == ',' || c == ';' || c == '.' || c == '=') {
                    break;
                }
                if (c == '/' && index + 1 < length) {
                    char next = source.charAt(index + 1);
                    if (next == '/' || next == '*') {
                        break;
                    }
                }
                if (c == '{' && index + 1 < length && source.charAt(index + 1) == '{') {
                    break;
                }
                index++;
            }
            tokens.add(new Token(TokenType.TEXT, source.substring(start, index), start, index));
        }

        return tokens;
    }

    private static String normalizeLineSeparators(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private enum TokenType {
        TEXT,
        STRING,
        PLACEHOLDER,
        COMMENT,
        LPAREN,
        RPAREN,
        COMMA,
        SEMICOLON,
        DOT,
        EQUALS
    }

    private static final class Token {
        private final TokenType type;
        private final String text;
        private final int startOffset;
        private final int endOffset;

        private Token(TokenType type, String text, int startOffset, int endOffset) {
            this.type = type;
            this.text = text;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }
}
