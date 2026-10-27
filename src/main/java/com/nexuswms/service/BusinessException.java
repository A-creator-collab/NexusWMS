package com.nexuswms.service;

/** Expected, user-presentable failure (validation, state rule). Message is safe to show in the UI. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
