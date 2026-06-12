package love.shirokasoke.webapi.webserver.Auth;

import java.util.List;

public class BannedPreProvider extends TokenAuthProvider {

    private int priority = 1;

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public String getName() {
        return "BannedPrefix";
    }

    @Override
    public AuthResult authenticate(String uri, String method, List<String> authorization) {
        return needAuth(uri, method) ? AuthResult.deny("banned") : AuthResult.skip();
    }
}
