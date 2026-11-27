package es.ull.calidadaire.api;

/** Error al consultar la API. status = código HTTP (0 si fue un fallo de red). */
public class ApiException extends RuntimeException {
    private final int status;
    private final Integer retryAfterSeconds;

    public ApiException(int status, String message) { this(status, message, null, null); }

    public ApiException(int status, String message, Integer retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int status() { return status; }

    /** Segundos indicados por la cabecera Retry-After (HTTP 429), o null. */
    public Integer retryAfterSeconds() { return retryAfterSeconds; }
}
