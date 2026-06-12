package love.shirokasoke.webapi.webserver.Auth;

public class AuthResult {

    public enum Status {
        PASS,
        DENY,
        /** 此 provider 不处理此请求，交给下一个 */
        SKIP
    }

    private final Status status;
    private final String message;

    private AuthResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public static AuthResult pass() {
        return new AuthResult(Status.PASS, null);
    }

    public static AuthResult deny(String message) {
        return new AuthResult(Status.DENY, message);
    }

    public static AuthResult skip() {
        return new AuthResult(Status.SKIP, null);
    }
}
