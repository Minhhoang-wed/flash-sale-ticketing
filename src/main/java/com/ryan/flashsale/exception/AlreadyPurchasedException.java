package com.ryan.flashsale.exception;

public class AlreadyPurchasedException extends RuntimeException {
    public AlreadyPurchasedException(String message) {
        super(message);
    }
}
