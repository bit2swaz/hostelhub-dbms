package com.hostelhub.service;

/** An error with a message that is safe to show. Database rejections keep the SQLException as the cause. */
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
