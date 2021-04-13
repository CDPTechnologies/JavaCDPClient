/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

interface Protocol {
  void parse(byte[] buf);
}
