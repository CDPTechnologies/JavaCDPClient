/*
 * (c)2019 CDP Technologies AS
 */

package no.icd.studioapi;

import no.icd.studioapi.proto.StudioAPI.CDPValueType;

import java.time.Instant;

/** 
 * Simple variant class for holding different type Node values.
 * External creation of instances is through Variant.Builder only.
 */
public class Variant {
  
  private final CDPValueType valueType;
  private final Object value;
  private final Instant timestamp;
  
  /** Constructor is internal, use Variant.Builder to construct Variants. */
  Variant(CDPValueType valueType, Object value, Instant timestamp) {
    this.valueType = valueType;
    this.value = value;
    this.timestamp = timestamp;
  }

  Variant(CDPValueType valueType, Object value, long nanoTime) {
    this.valueType = valueType;
    this.value = value;
    this.timestamp = Instant.ofEpochSecond(0, nanoTime);
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
  public Instant getTimestamp() {
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
    private Instant timestamp;
    
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
        value = Double.valueOf(strValue);
        break;
      case eUINT64:
        value = Long.valueOf(strValue); // sign bit represents top bit
        break;
      case eINT64:
        value = Long.valueOf(strValue);
        break;
      case eFLOAT:
        value = Float.valueOf(strValue);
        break;
      case eUINT:
        value = Integer.valueOf(strValue); // sign bit represents top bit
        break;
      case eINT:
        value = Integer.valueOf(strValue);
        break;
      case eUSHORT:
        Integer v = Integer.valueOf(strValue);
        if (v.intValue() < 0 || v.intValue() > 65535)
          throw new IllegalArgumentException("unsigned short out of bounds");
        value = v;
        break;
      case eSHORT:
        value = Short.valueOf(strValue);
        break;
      case eUCHAR:
        value = Integer.valueOf(strValue.charAt(0));
        if (strValue.charAt(0) > 255 || strValue.charAt(0) < 0)
          throw new IllegalArgumentException("unsigned char out of bounds");
        break;
      case eCHAR:
        value = Byte.valueOf(strValue);
        break;
      case eBOOL:
        value = Boolean.valueOf(strValue);
        break;
      case eSTRING:
        value = strValue;
        break;
      }
      return this;
    }
    
    public Builder setTimestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }
    
    public Variant build() {
      return new Variant(valueType, value, timestamp);
    }
  }

}
