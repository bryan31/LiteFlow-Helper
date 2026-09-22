package com.yomahub.liteflowhelper.toolwindow.model;

import com.yomahub.liteflowhelper.utils.Clazz;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NodeTypeTest {

    @Test
    public void nodeIfComponentIsRecognizedAsBoolean() {
        assertEquals(NodeType.BOOLEAN_COMPONENT, NodeType.fromComponentClass(Clazz.NodeIfComponent));
        assertEquals(NodeCategory.BOOLEAN, NodeType.BOOLEAN_COMPONENT.toCategory());
    }
}
