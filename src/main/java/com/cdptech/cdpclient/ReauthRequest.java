/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

/** A connection's re-authentication request. The connection remembers the prompt whose answer it last received. */
interface ReauthRequest extends AuthRequest {

  CompositeAuthRequest getAnsweringPrompt();

  void setAnsweringPrompt(CompositeAuthRequest prompt);
}
