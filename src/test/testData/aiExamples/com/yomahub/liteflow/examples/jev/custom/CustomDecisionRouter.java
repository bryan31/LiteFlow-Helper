package com.yomahub.liteflow.examples.jev.custom;

import com.yomahub.liteflow.agent.jev.JevChoiceResult;
import com.yomahub.liteflow.agent.jev.JevSwitchComponent;
import com.yomahub.liteflow.annotation.LiteflowComponent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Uses this request's question and options, independently of the customer-service router. */
@LiteflowComponent("customDecisionRouter")
public class CustomDecisionRouter extends JevSwitchComponent {
    @Override
    protected Object state() {
        return getContextBean(CustomDecisionContext.class).getQuestion();
    }

    @Override
    protected String instructions() {
        return "请根据输入中的问题和上下文，从候选选项中选择最符合要求的一项。没有合适选项或信息不足时选择均不适用。";
    }

    @Override
    protected Map<String, String> choices() {
        Map<String, String> choices = new LinkedHashMap<>();
        for (CustomDecisionContext.Option option : getContextBean(CustomDecisionContext.class).getOptions()) {
            choices.put(option.id(), option.label());
        }
        return choices;
    }

    @Override
    protected void onDecision(JevChoiceResult result) {
        getContextBean(CustomDecisionContext.class).setDecision(result);
    }
}
