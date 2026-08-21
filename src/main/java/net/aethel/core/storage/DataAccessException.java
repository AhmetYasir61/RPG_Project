package net.aethel.core.storage;

/** Veri katmani hatalarini tek tip altinda toplar; cagiran taraf SQLException gormez. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
