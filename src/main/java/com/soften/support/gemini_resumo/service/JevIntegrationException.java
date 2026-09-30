package com.soften.support.gemini_resumo.service;

import org.springframework.http.HttpStatus;

public class JevIntegrationException extends RuntimeException {

    private final String clientMessage;
    private final HttpStatus httpStatus;

    public JevIntegrationException(String clientMessage, HttpStatus httpStatus, String technicalMessage, Throwable cause) {
        super(technicalMessage, cause);
        this.clientMessage = clientMessage;
        this.httpStatus = httpStatus;
    }

    public JevIntegrationException(String clientMessage, HttpStatus httpStatus, String technicalMessage) {
        super(technicalMessage);
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
