package com.soften.support.gemini_resumo.service;

import org.springframework.http.HttpStatus;

public class GeminiIntegrationException extends RuntimeException {

    private final String clientMessage;
    private final HttpStatus httpStatus;

    public GeminiIntegrationException(String clientMessage, HttpStatus httpStatus, Throwable cause) {
        super(cause);
        this.clientMessage = clientMessage;
        this.httpStatus = httpStatus;
    }

    public GeminiIntegrationException(String clientMessage, HttpStatus httpStatus, String technicalMessage, Throwable cause) {
        super(technicalMessage, cause);
        this.clientMessage = clientMessage;
        this.httpStatus = httpStatus;
    }

    public String getClientMessage() {
        return clientMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
