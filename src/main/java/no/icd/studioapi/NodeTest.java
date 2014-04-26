package no.icd.studioapi;

import static org.junit.Assert.*;
import no.icd.studioapi.proto.Studioapi.CDPNodeType;
import no.icd.studioapi.proto.Studioapi.CDPValueType;

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
    Node n = new Node(0, CDPNodeType.CDP_SYSTEM, 
        CDPValueType.eUNDEFINED,
        "CDP_SYSTEM");
    assertEquals("CDP_SYSTEM", n.getLongName());
    
    Node app = new Node(1, CDPNodeType.CDP_APPLICATION,
        CDPValueType.eUNDEFINED, "Application");
    n.addChild(app);
    
    assertEquals("Application", app.getLongName());
  }

}
