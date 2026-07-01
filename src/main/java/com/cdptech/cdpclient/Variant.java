/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import com.cdptech.cdpclient.proto.StudioAPI.CDPValueType;

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
  
  /**
   * The value's timestamp. A server-delivered value that carried no timestamp reports {@code Instant.EPOCH};
   * a Variant built via {@link Builder} without {@link Builder#setTimestamp} reports {@code null}.
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /** Get the Variant's value as a printable String. Unsigned types print their unsigned value. */
  public String toString() {
    if (valueType == CDPValueType.eUNDEFINED) return "<invalid variant>";
    if (valueType == CDPValueType.eUINT)
      return Integer.toUnsignedString((Integer) value);
    if (valueType == CDPValueType.eUINT64)
      return Long.toUnsignedString((Long) value);
    return value.toString();
  }

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
        value = Long.parseUnsignedLong(strValue); // sign bit represents top bit
        break;
      case eINT64:
        value = Long.valueOf(strValue);
        break;
      case eFLOAT:
        value = Float.valueOf(strValue);
        break;
      case eUINT:
        value = Integer.parseUnsignedInt(strValue); // sign bit represents top bit
        break;
      case eINT:
        value = Integer.valueOf(strValue);
        break;
      case eUSHORT:
        value = parseRangedInt(strValue, 0, 65535, "unsigned short");
        break;
      case eSHORT:
        value = parseRangedInt(strValue, Short.MIN_VALUE, Short.MAX_VALUE, "short");
        break;
      case eUCHAR:
        value = parseRangedInt(strValue, 0, 255, "unsigned char");
        break;
      case eCHAR:
        value = parseRangedInt(strValue, Byte.MIN_VALUE, Byte.MAX_VALUE, "char");
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

    /** Parse a decimal integer and bound it to [min, max], boxing as Integer so all narrow int types share one box. */
    private static Integer parseRangedInt(String strValue, int min, int max, String typeName) {
      int v = Integer.parseInt(strValue);
      if (v < min || v > max)
        throw new IllegalArgumentException(typeName + " out of bounds: " + strValue);
      return v;
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
