package com.yomahub.liteflowhelper.utils;

import com.yomahub.liteflowhelper.toolwindow.model.NodeCategory;
import org.junit.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link LiteFlowElAnalyzer} 的单元测试：结构检查 + 语义节点类型检查。
 */
public class LiteFlowElAnalyzerTest {

    private final Function<String, NodeCategory> cat = name -> {
        switch (name) {
            case "sw": return NodeCategory.SWITCH;
            case "bool": return NodeCategory.BOOLEAN;
            case "for": return NodeCategory.FOR;
            case "iter": return NodeCategory.ITERATOR;
            case "cm": return NodeCategory.COMMON;
            case "fb": return NodeCategory.FALLBACK;
            default: return null; // 非已知节点（子变量/未知）
        }
    };

    private List<ElValidationFinding> analyze(String el) {
        return LiteFlowElAnalyzer.analyze(el, cat);
    }

    private static boolean hasError(List<ElValidationFinding> f) {
        return f.stream().anyMatch(x -> x.severity == ElValidationFinding.Severity.ERROR);
    }

    private static boolean hasWarning(List<ElValidationFinding> f) {
        return f.stream().anyMatch(x -> x.severity == ElValidationFinding.Severity.WARNING);
    }

    private static boolean hasErrorWith(List<ElValidationFinding> f, String sub) {
        return f.stream().anyMatch(x -> x.severity == ElValidationFinding.Severity.ERROR && x.message.contains(sub));
    }

    // ---- 结构检查 ----

    @Test
    public void unbalancedParenReportedAsError() {
        assertTrue(hasErrorWith(analyze("THEN(a"), "括号"));
        assertTrue(hasErrorWith(analyze("THEN(a))"), "括号"));
    }

    @Test
    public void balancedExpressionHasNoParenError() {
        assertFalse(hasErrorWith(analyze("THEN(a,b)"), "括号"));
    }

    @Test
    public void switchMissingToWarns() {
        assertTrue(hasWarning(analyze("SWITCH(sw)")));
    }

    @Test
    public void switchWithToOk() {
        assertFalse(hasWarning(analyze("SWITCH(sw).TO(cm)")));
    }

    @Test
    public void forMissingDoWarns() {
        assertTrue(hasWarning(analyze("FOR(for)")));
    }

    @Test
    public void forWithDoOk() {
        assertFalse(hasWarning(analyze("FOR(for).DO(cm)")));
    }

    @Test
    public void whileAndIteratorMissingDoWarn() {
        assertTrue(hasWarning(analyze("WHILE(bool)")));
        assertTrue(hasWarning(analyze("ITERATOR(iter)")));
    }

    @Test
    public void ifTooFewArgsError() {
        assertTrue(hasErrorWith(analyze("IF(bool)"), "IF"));
    }

    @Test
    public void ifTwoArgsOk() {
        assertFalse(hasErrorWith(analyze("IF(bool, cm)"), "IF"));
        assertFalse(hasErrorWith(analyze("IF(bool, cm, cm)"), "IF"));
    }

    // ---- 语义检查 ----

    @Test
    public void switchArgMustBeSwitchType() {
        // SWITCH 的条件位用了普通节点 → ERROR
        assertTrue(hasError(analyze("SWITCH(cm).TO(x)")));
        // 用 switch 节点 → 无类型错误
        assertFalse(hasError(analyze("SWITCH(sw).TO(x)")));
    }

    @Test
    public void ifConditionMustBeBoolean() {
        assertTrue(hasError(analyze("IF(cm, cm)")));
        assertFalse(hasError(analyze("IF(bool, cm)")));
    }

    @Test
    public void whileConditionMustBeBoolean() {
        assertTrue(hasError(analyze("WHILE(cm).DO(x)")));
        assertFalse(hasError(analyze("WHILE(bool).DO(x)")));
    }

    @Test
    public void forArgMustBeForTypeOrLiteral() {
        assertTrue(hasError(analyze("FOR(cm).DO(x)")));
        assertFalse(hasError(analyze("FOR(for).DO(x)")));
        // 整数字面量允许
        assertFalse(hasError(analyze("FOR(5).DO(x)")));
    }

    @Test
    public void iteratorArgMustBeIterator() {
        assertTrue(hasError(analyze("ITERATOR(cm).DO(x)")));
        assertFalse(hasError(analyze("ITERATOR(iter).DO(x)")));
    }

    @Test
    public void andOrNotOperandsMustBeBoolean() {
        assertTrue(hasError(analyze("AND(cm, bool)")));
        assertTrue(hasError(analyze("OR(cm, bool)")));
        assertTrue(hasError(analyze("NOT(cm)")));
        assertFalse(hasError(analyze("AND(bool, bool)")));
        assertFalse(hasError(analyze("NOT(bool)")));
    }

    @Test
    public void nestedAndInsideIfIsOk() {
        // IF(AND(a,b), c)：a/b 在 AND 槽位需 boolean，c 在 IF 分支位无限制
        assertFalse(hasError(analyze("IF(AND(bool, bool), cm)")));
    }

    @Test
    public void fallbackAndUnknownAreSkipped() {
        // FALLBACK 到处放行；未知节点（返回 null）不报类型错误
        assertFalse(hasError(analyze("SWITCH(fb).TO(x)")));
        assertFalse(hasError(analyze("SWITCH(unknownX).TO(y)")));
    }

    @Test
    public void cleanComplexExpressionHasNoFindings() {
        List<ElValidationFinding> f = analyze(
                "IF(AND(bool, bool), SWITCH(sw).TO(cm, cm), THEN(cm, FOR(5).DO(cm)));");
        assertFalse(hasError(f));
        assertFalse(hasWarning(f));
    }
}
