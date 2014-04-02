package no.icd.studioapi;

import java.lang.invoke.WrongMethodTypeException;

import no.icd.studioapi.proto.Studioapi.CDPValueType;

/** Simple variant class for holding different type Node values. */
public class Variant {
  
  private CDPValueType valueType;
  private Object value;
  private double timestamp;
  
  public Variant(double d) {
    valueType = CDPValueType.eDOUBLE;
    value = d;
  }
  
  public Variant(float f) {
    valueType = CDPValueType.eFLOAT;
    value = f;
  }
  
  /* byte is a more appropriate version of C++ char. */
  public Variant(byte c) {
    valueType = CDPValueType.eCHAR;
    value = c;
  }
  
  public Variant(boolean b) {
    valueType = CDPValueType.eBOOL;
    value = b;
  }
  
  public Variant(String str) {
    valueType = CDPValueType.eSTRING;
    value = str;
  }
  
  /**
   * Get the value of this variant.
   * @return A value of the requested type if it matches.
   * @throws ClassCastException if the requested type and getValueType() 
   *         don't match.
   * @throws IllegalArgumentException if the variant has no value.
   */
  @SuppressWarnings("unchecked")
  public <T> T getValue() {
    if (valueType == CDPValueType.eUNDEFINED)
      throw new IllegalArgumentException("Variant has no value!");
    
    return (T)value;
  }
  
  public CDPValueType getValueType() {
    return valueType;
  }
  
  public double getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(double timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public String toString() {
    if (valueType == CDPValueType.eUNDEFINED)
      return "<invalid variant>";
    
    return value.toString();
  }

}
