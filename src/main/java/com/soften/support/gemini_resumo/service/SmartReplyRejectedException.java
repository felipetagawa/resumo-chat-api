package com.soften.support.gemini_resumo.service;

/** The model answered only with stock phrases; the client should ask for another wording. */
public class SmartReplyRejectedException extends RuntimeException {
    public SmartReplyRejectedException() { super("Smart Reply contained only stock phrases."); }
}
