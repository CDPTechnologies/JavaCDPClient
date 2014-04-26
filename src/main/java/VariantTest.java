import static org.junit.Assert.*;

import org.junit.Test;

import no.icd.studioapi.Variant;


public class VariantTest {

  @Test(expected=IllegalArgumentException.class)
  public void variantWithIllegalCharBounds_shouldThrow() {
    char c = 256;
    new Variant(c, true);
  }
  
  @Test
  public void testCorrectBounds() {
    Variant v = new Variant(245.5555d);
    assertEquals(245.5555d, v.getValue());
    char c = 24;
    v = new Variant(c);
  }

}
