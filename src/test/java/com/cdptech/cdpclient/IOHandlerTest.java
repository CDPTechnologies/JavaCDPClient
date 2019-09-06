/**
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import com.cdptech.cdpclient.proto.StudioAPI;

import org.junit.Before;
import org.junit.Test;

public class IOHandlerTest {

  StudioAPI.VariantValue.Builder pbv;
  
  @Before
  public void setUp() throws Exception {
    pbv = StudioAPI.VariantValue.newBuilder().setNodeId(5);
  }

  @Test
  public void createVariant_shouldReadFloat() {
    Variant created = IOHandler.createVariant(pbv.setFValue(2.1234f).build(), 0);
    float value = created.getValue();
    assertEquals(2.1234f, value, 0.001);
  }
  
  @Test
  public void createVariant_shouldReadString() {
    Variant created = IOHandler.createVariant(pbv.setStrValue("test").build(), 0);
    String value = created.getValue();
    assertEquals("test", value);
  }

}
