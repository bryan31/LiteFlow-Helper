package com.yomahub.liteflow.examples.agent.skillstream.cmp;

import com.yomahub.liteflow.agent.context.LiteFlowAgentContext;
import com.yomahub.liteflow.agent.harness.component.HarnessAgentComponent;
import com.yomahub.liteflow.agent.model.ModelSpec;
import com.yomahub.liteflow.agent.openai.DeepSeek;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.examples.agent.skillstream.chat.ArtifactStore;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.LinkedHashMap;
import java.util.Map;

@LiteflowComponent("skillStreamAgent")
public class SkillStreamAgentCmp extends HarnessAgentComponent {

    @Value("${example.agent.model:deepseek-v4-flash}")
    private String modelName;

    @Autowired
    private ArtifactStore artifactStore;

    @Override
    protected HarnessAgent.Builder customizeHarness(HarnessAgent.Builder builder) {
        return builder.artifactDeliveryTarget(artifactStore);
    }

    @Override
    protected ModelSpec<?> model() {
        return DeepSeek.of(modelName).stream(true);
    }

    @Override
    protected String systemPrompt() {
        return "你在 AgentScope 2 Harness 非 Docker 文件工作区中工作。按 Agent 工作流执行技能，"
                + "调用工具前使用用户提问所用的语言做简短说明；最终不要输出原始 JSON，"
                + "而要总结其中的数据并条理清晰地展现给用户。"
                + "可通过 execute 在宿主机执行 Shell、Python 和 Node；以相对路径读写当前会话工作区。"
                + "同一会话使用 workspace.root 下的固定执行目录，文件改动会写回会话存储；命令受白名单限制，多步操作请写成脚本执行。不要依赖上次命令的 cd 或环境变量。用户要求生成文件时，将文件写入工作区，再调用 deliver_artifact 交付产物。";
    }

    @Override
    protected String userPrompt(LiteFlowAgentContext context) {
        return this.getRequestData();
    }

    @Override
    protected void handleReply(Msg reply, LiteFlowAgentContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("reply", reply == null ? null : reply.getTextContent());
        output.put("usedSkills", context.getUsedSkills());
        getSlot().setResponseData(output);
    }

    @Override
    protected CompactionConfig compactionConfig() {
        return super.compactionConfig();
    }
}
