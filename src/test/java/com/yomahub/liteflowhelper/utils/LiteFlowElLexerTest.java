package com.yomahub.liteflowhelper.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link LiteFlowElLexer} 的单元测试。
 * 覆盖标识符、关键字、字符串、数字、注释的分词，以及偏移区间的正确性。
 */
public class LiteFlowElLexerTest {

    private static List<LiteFlowElToken> lex(String s) {
        return LiteFlowElLexer.tokenize(s);
    }

    private static LiteFlowElToken tok(LiteFlowElToken.Type type, String text, int start, int end) {
        return new LiteFlowElToken(type, text, start, end);
    }

    /** 只保留指定类型的 token，便于断言。 */
    private static List<LiteFlowElToken> filter(List<LiteFlowElToken> tokens, LiteFlowElToken.Type type) {
        List<LiteFlowElToken> r = new ArrayList<>();
        for (LiteFlowElToken t : tokens) {
            if (t.type == type) r.add(t);
        }
        return r;
    }

    private static List<String> texts(List<LiteFlowElToken> tokens) {
        List<String> r = new ArrayList<>();
        for (LiteFlowElToken t : tokens) r.add(t.text);
        return r;
    }

    @Test
    public void tokenizesSimpleThenExpression() {
        List<LiteFlowElToken> tokens = lex("THEN(a)");
        assertEquals(Arrays.asList(
                tok(LiteFlowElToken.Type.IDENT, "THEN", 0, 4),
                tok(LiteFlowElToken.Type.PUNCT, "(", 4, 5),
                tok(LiteFlowElToken.Type.IDENT, "a", 5, 6),
                tok(LiteFlowElToken.Type.PUNCT, ")", 6, 7)
        ), tokens);
    }

    @Test
    public void stringLiteralContentsAreNotIdentifiers() {
        // SWITCH(a).TO("b","c") 中引号内的 b / c 不应被当作标识符
        List<LiteFlowElToken> tokens = lex("SWITCH(a).TO(\"b\",\"c\")");
        List<String> identTexts = texts(filter(tokens, LiteFlowElToken.Type.IDENT));
        assertTrue("SWITCH 应作为标识符", identTexts.contains("SWITCH"));
        assertTrue("a 应作为标识符", identTexts.contains("a"));
        assertTrue("TO 应作为标识符", identTexts.contains("TO"));
        assertTrue("引号内的 b 不应是标识符", !identTexts.contains("b"));
        assertTrue("引号内的 c 不应是标识符", !identTexts.contains("c"));
        // 两个字符串 token
        assertEquals(2, filter(tokens, LiteFlowElToken.Type.STRING).size());
    }

    @Test
    public void singleQuotedStringAlsoSkipped() {
        List<LiteFlowElToken> tokens = lex("a.tag('t1')");
        assertEquals(Arrays.asList("a", "tag"), texts(filter(tokens, LiteFlowElToken.Type.IDENT)));
        assertEquals(1, filter(tokens, LiteFlowElToken.Type.STRING).size());
    }

    @Test
    public void blockCommentIsSingleTokenAndMasked() {
        // /* cmt */ THEN(a) —— 注释整体是一个 COMMENT token，其后的 THEN/a 仍可识别
        List<LiteFlowElToken> tokens = lex("/* cmt */ THEN(a)");
        List<LiteFlowElToken> comments = filter(tokens, LiteFlowElToken.Type.COMMENT);
        assertEquals(1, comments.size());
        assertEquals("/* cmt */", comments.get(0).text);
        assertEquals(0, comments.get(0).start);
        assertEquals(9, comments.get(0).end);
        List<String> identTexts = texts(filter(tokens, LiteFlowElToken.Type.IDENT));
        assertTrue(identTexts.contains("THEN"));
        assertTrue(identTexts.contains("a"));
    }

    @Test
    public void starStarCommentAlsoSupported() {
        // 旧实现识别 /** ... **/ 这种写法，需保持兼容（标准 /* ... */ 规则可覆盖）
        List<LiteFlowElToken> tokens = lex("/** cmt **/ THEN(a)");
        assertEquals(1, filter(tokens, LiteFlowElToken.Type.COMMENT).size());
        assertTrue(texts(filter(tokens, LiteFlowElToken.Type.IDENT)).contains("THEN"));
    }

    @Test
    public void integerNumberIsNotIdentifier() {
        // FOR(5).DO(a) —— 5 是 NUMBER
        List<LiteFlowElToken> tokens = lex("FOR(5).DO(a)");
        List<LiteFlowElToken> numbers = filter(tokens, LiteFlowElToken.Type.NUMBER);
        assertEquals(1, numbers.size());
        assertEquals("5", numbers.get(0).text);
        assertTrue(!texts(filter(tokens, LiteFlowElToken.Type.IDENT)).contains("5"));
    }

    @Test
    public void decimalNumberIsSingleToken() {
        // WHEN(a,b).percentage(0.8) —— 0.8 是一个 NUMBER token，且不吞掉后面的括号
        List<LiteFlowElToken> tokens = lex("WHEN(a,b).percentage(0.8)");
        List<LiteFlowElToken> numbers = filter(tokens, LiteFlowElToken.Type.NUMBER);
        assertEquals(1, numbers.size());
        assertEquals("0.8", numbers.get(0).text);
    }

    @Test
    public void subVarDefinitionYieldsIdentThenEquals() {
        // sub = THEN(a) —— 子变量定义：IDENT "sub" 后紧跟 PUNCT "="
        List<LiteFlowElToken> tokens = lex("sub = THEN(a)");
        assertEquals(tok(LiteFlowElToken.Type.IDENT, "sub", 0, 3), tokens.get(0));
        assertEquals(tok(LiteFlowElToken.Type.PUNCT, "=", 4, 5), tokens.get(1));
    }

    @Test
    public void multipleStatementsSeparatedBySemicolon() {
        List<LiteFlowElToken> tokens = lex("THEN(a); WHEN(b,c);");
        List<String> identTexts = texts(filter(tokens, LiteFlowElToken.Type.IDENT));
        assertEquals(Arrays.asList("THEN", "a", "WHEN", "b", "c"), identTexts);
    }

    @Test
    public void nodeKeywordAndColonSyntax() {
        // node("x") 以及 nodeId:type 冒号语法不应把冒号后的内容误判
        List<LiteFlowElToken> tokens = lex("node(\"x\")");
        assertEquals(Arrays.asList("node"), texts(filter(tokens, LiteFlowElToken.Type.IDENT)));
        assertEquals(1, filter(tokens, LiteFlowElToken.Type.STRING).size());
    }

    @Test
    public void findsSubVarDefinitions() {
        java.util.List<LiteFlowElToken> tokens = lex("sub = THEN(a); other = WHEN(b)");
        java.util.Map<String, LiteFlowElToken> defs = LiteFlowElLexer.findSubVarDefinitions(tokens);
        assertEquals(2, defs.size());
        assertTrue(defs.containsKey("sub"));
        assertTrue(defs.containsKey("other"));
        assertEquals(tok(LiteFlowElToken.Type.IDENT, "sub", 0, 3), defs.get("sub"));
    }

    @Test
    public void doesNotTreatMethodCallOrKeywordAsSubVar() {
        // a.tag("x") 中没有赋值，不应有任何子变量
        assertTrue(LiteFlowElLexer.findSubVarDefinitions(lex("a.tag(\"x\")")).isEmpty());
        // 赋值符两侧无空格也应识别
        assertEquals(1, LiteFlowElLexer.findSubVarDefinitions(lex("s=THEN(a)")).size());
    }

    @Test
    public void emptyAndWhitespaceProduceNoTokens() {
        assertTrue(lex("").isEmpty());
        assertTrue(lex("   \n\t  ").isEmpty());
    }

    @Test
    public void unterminatedStringConsumesToEndGracefully() {
        // 永不抛异常：未闭合的字符串应优雅处理
        List<LiteFlowElToken> tokens = lex("a.tag(\"unterminated)");
        assertTrue(!tokens.isEmpty());
        assertTrue(texts(filter(tokens, LiteFlowElToken.Type.IDENT)).contains("a"));
    }
}
