/**
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import com.cdptech.cdpclient.proto.StudioAPI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NodeTest {

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void getLongName_ShouldIgnoreSystemNode() {
    Node n = new Node(0, StudioAPI.CDPNodeType.CDP_SYSTEM,
        StudioAPI.CDPValueType.eUNDEFINED,
        "CDP_SYSTEM", 0);
    assertEquals("CDP_SYSTEM", n.getLongName());
    
    Node app = new Node(1, StudioAPI.CDPNodeType.CDP_APPLICATION,
        StudioAPI.CDPValueType.eUNDEFINED, "Application", 0);
    n.addChild(app);
    
    assertEquals("Application", app.getLongName());
  }

}
