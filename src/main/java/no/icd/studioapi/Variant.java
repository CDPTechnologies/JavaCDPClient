/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

import no.icd.studioapi.proto.Studioapi.CDPValueType;

/** Simple variant class for holding different type Node values. */
public class Variant {
  
  private final CDPValueType valueType;
  private final Object value;
  private final double timestamp;
  
  /** Constructor is internal, use Variant.Builder to construct Variants. */
  Variant(CDPValueType valueType, Object value, double timestamp) {
    this.valueType = valueType;
    this.value = value;
    this.timestamp = timestamp;
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
  
  /** Get the value type of the Variant. */
  public CDPValueType getValueType() {
    return valueType;
  }
  
  /** Get the value timestamp. @returns 0.0 if no timestamp was specified. */
  public double getTimestamp() {
    return timestamp;
  }

  /** Get the Variant's value as a printable String. */
  public String toString() {
    if (valueType == CDPValueType.eUNDEFINED) return "<invalid variant>";
    return value.toString();
  }
  
  
  // accounted value types:
  // double, float, char, boolean, String
  // unaccounted value types:
  // (unsigned) int, (unsigned) short, unsigned char, i64 / ui64
  
  /** Builder class for constructing immutable Variant objects. */
  public static class Builder {
    private final CDPValueType valueType;
    private Object value;
    private double timestamp;
    
    /** Construct a variant builder with the given value type. */
    public Builder(CDPValueType valueType) {
      this.valueType = valueType;
    }
    
    /** 
     * Parse and set a value from a String. 
     * @throws IllegalArgumentException if value couldn't be parsed.
     */
    public Builder parse(String strValue) {
      switch (valueType) {
      case eUNDEFINED:
        value = "";
        break;
      case eDOUBLE:
        value = new Double(strValue);
        break;
      case eUINT64:
        value = new Long(strValue); // sign bit represents top bit
        break;
      case eINT64:
        value = new Long(strValue);
        break;
      case eFLOAT:
        value = new Float(strValue);
        break;
      case eUINT:
        value = new Integer(strValue); // sign bit represents top bit
        break;
      case eINT:
        value = new Integer(strValue);
        break;
      case eUSHORT:
        Integer v = new Integer(strValue);
        if (v.intValue() < 0 || v.intValue() > 65535)
          throw new IllegalArgumentException("unsigned short out of bounds");
        value = v;
        break;
      case eSHORT:
        value = new Short(strValue);
        break;
      case eUCHAR:
        value = new Integer(strValue.charAt(0));
        if (strValue.charAt(0) > 255 || strValue.charAt(0) < 0)
          throw new IllegalArgumentException("unsigned char out of bounds");
        break;
      case eCHAR:
        value = new Byte(strValue);
        break;
      case eBOOL:
        value = new Boolean(strValue);
        break;
      case eSTRING:
        value = strValue;
        break;
      }
      return this;
    }
    
    public Builder setTimestamp(double timestamp) {
      this.timestamp = timestamp;
      return this;
    }
    
    public Variant build() {
      return new Variant(valueType, value, timestamp);
    }
  }

}
