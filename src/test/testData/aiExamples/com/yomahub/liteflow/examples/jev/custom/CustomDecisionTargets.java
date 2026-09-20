package com.yomahub.liteflow.examples.jev.custom;

import com.yomahub.liteflow.core.NodeComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fixed branch IDs keep user-defined option text separate from executable flow definitions. */
@Configuration
public class CustomDecisionTargets {
    @Bean("customOption1") public NodeComponent option1() { return new SelectedOption(); }
    @Bean("customOption2") public NodeComponent option2() { return new SelectedOption(); }
    @Bean("customOption3") public NodeComponent option3() { return new SelectedOption(); }
    @Bean("customOption4") public NodeComponent option4() { return new SelectedOption(); }
    @Bean("customOption5") public NodeComponent option5() { return new SelectedOption(); }
    @Bean("customOption6") public NodeComponent option6() { return new SelectedOption(); }
    @Bean("customOption7") public NodeComponent option7() { return new SelectedOption(); }
    @Bean("customOption8") public NodeComponent option8() { return new SelectedOption(); }

    @Bean("customUndecided")
    public NodeComponent undecided() {
        return new NodeComponent() {
            @Override public void process() {
                getContextBean(CustomDecisionContext.class).complete(getNodeId(),
                        "本次暂不选择。可以补充问题或调整选项后重试。");
            }
        };
    }

    public static class SelectedOption extends NodeComponent {
        @Override
        public void process() {
            CustomDecisionContext context = getContextBean(CustomDecisionContext.class);
            CustomDecisionContext.Option selected = context.getOptions().stream()
                    .filter(option -> option.id().equals(getNodeId())).findFirst().orElseThrow();
            context.complete(getNodeId(), "已选择「" + selected.label() + "」。");
        }
    }
}
