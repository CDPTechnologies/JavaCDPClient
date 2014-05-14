/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

import static org.junit.Assert.*;
import no.icd.studioapi.proto.StudioAPI.PBVariantValue;

import org.junit.Before;
import org.junit.Test;

public class IOHandlerTest {
  
  PBVariantValue.Builder pbv;
  
  @Before
  public void setUp() throws Exception {
    pbv = PBVariantValue.newBuilder().setNodeID(5);
  }

  @Test
  public void createVariant_shouldReadFloat() {
    Variant created = IOHandler.createVariant(pbv.setFValue(2.1234f).build());
    float value = created.getValue();
    assertEquals(2.1234f, value, 0.001);
  }
  
  @Test
  public void createVariant_shouldReadString() {
    Variant created = IOHandler.createVariant(pbv.setStrValue("test").build());
    String value = created.getValue();
    assertEquals("test", value);
  }

}
