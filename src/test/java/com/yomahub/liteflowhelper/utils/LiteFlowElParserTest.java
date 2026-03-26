package com.yomahub.liteflowhelper.utils;

import com.ql.util.express.ExpressRunner;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LiteFlowElParserTest {

    private static final ExpressRunner EXPRESS_RUNNER = new ExpressRunner();

    @Test
    public void parseShouldMaskPlaceholdersWithoutChangingExpressionLength() {
        String expression = "THEN({{business}}, actualNode, {{agentOpen}})";

        LiteFlowElParser.MaskedResult result = LiteFlowElParser.parse(expression);

        Assert.assertEquals(expression.length(), result.maskedText.length());
        Assert.assertFalse(result.maskedText.contains("{{business}}"));
        Assert.assertFalse(result.maskedText.contains("{{agentOpen}}"));
        Assert.assertTrue(result.maskedText.contains("actualNode"));
    }

    @Test
    public void dummyPlaceholderVarsShouldBeEasyToFilterAfterQlExpressParsing() throws Exception {
        String expression = "THEN({{business}}, actualNode, {{agentOpen}})";
        LiteFlowElParser.MaskedResult result = LiteFlowElParser.parse(expression);

        List<String> realVars = Arrays.stream(EXPRESS_RUNNER.getOutVarNames(result.maskedText))
                .filter(varName -> !LiteFlowElParser.isDummyPlaceholderVar(varName))
                .collect(Collectors.toList());

        Assert.assertEquals(1, realVars.size());
        Assert.assertEquals("actualNode", realVars.get(0));
    }

    @Test
    public void formatterShouldIndentNestedIfLikePythonStyle() {
        String input = """
                IF(
                x1,
                a,
                IF(
                x2,
                b,
                IF(x3, c, d)
                )
                );
                """;

        String expected = """
                IF(
                    x1,
                    a,
                    IF(
                        x2,
                        b,
                        IF(x3, c, d)
                    )
                );
                """;

        Assert.assertEquals(expected.strip(), LiteFlowElFormatter.formatExpression(input, "    "));
    }

    @Test
    public void formatterShouldKeepTrailingLineCommentsInline() {
        String input = """
                IF(
                a ,// comment for a
                b // comment for b end
                );
                """;

        String expected = """
                IF(
                    a, // comment for a
                    b // comment for b end
                );
                """;

        Assert.assertEquals(expected.strip(), LiteFlowElFormatter.formatExpression(input, "    "));
    }

    @Test
    public void formatterShouldSupportXmlComments() {
        String input = """
                THEN(
                a, <!-- comment a -->
                <!-- middle comment -->
                b
                );
                """;

        String expected = """
                THEN(
                    a, <!-- comment a -->
                    <!-- middle comment -->
                    b
                );
                """;

        Assert.assertEquals(expected.strip(), LiteFlowElFormatter.formatExpression(input, "    "));
    }
}
