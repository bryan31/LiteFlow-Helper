package com.yomahub.liteflow.examples.agent.skillstream.cmp;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

@LiteflowComponent("recordReply")
public class RecordReplyCmp extends NodeComponent {

    @Override
    public void process() {
        Object reply = this.getSlot().getResponseData();
        System.out.println("[recordReply] reply=" + reply);
        if (reply != null) {
            this.getSlot().setOutput("recordReply", reply);
        }
    }
}
