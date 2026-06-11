package com.archive.common;

/**
 * 乐观锁冲突业务异常.
 *
 * @author Mavis
 */
public class OptimisticLockException extends RuntimeException {

    public OptimisticLockException(String message) {
        super(message);
    }

    public OptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
