package com.obs.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Matches "METHOD /path/{param}" routes. Distinguishes unknown paths (404) from wrong methods (405). */
public final class Router {

    record Route(String method, Pattern pattern, List<String> names, Access access, Handler handler) {
    }

    public record Match(Route route, Map<String, String> pathParams) {
    }

    public static final class MethodNotAllowed extends RuntimeException {
        public MethodNotAllowed() {
            super(null, null, false, false);
        }
    }

    private static final Pattern PARAM = Pattern.compile("\\{(\\w+)}");

    private final List<Route> routes = new ArrayList<>();

    public Router get(String path, Access access, Handler handler) {
        return add("GET", path, access, handler);
    }

    public Router post(String path, Access access, Handler handler) {
        return add("POST", path, access, handler);
    }

    public Router put(String path, Access access, Handler handler) {
        return add("PUT", path, access, handler);
    }

    public Router delete(String path, Access access, Handler handler) {
        return add("DELETE", path, access, handler);
    }

    private Router add(String method, String path, Access access, Handler handler) {
        List<String> names = new ArrayList<>();
        Matcher m = PARAM.matcher(path);
        StringBuilder regex = new StringBuilder("^");
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(path.substring(last, m.start()))).append("([^/]+)");
            names.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(path.substring(last))).append("/?$");
        routes.add(new Route(method, Pattern.compile(regex.toString()), names, access, handler));
        return this;
    }

    /** Returns null when no route has this path; throws MethodNotAllowed when the path exists for other methods. */
    public Match match(String method, String path) {
        boolean pathExists = false;
        for (Route route : routes) {
            Matcher m = route.pattern().matcher(path);
            if (!m.matches()) {
                continue;
            }
            pathExists = true;
            if (route.method().equals(method)) {
                Map<String, String> params = new HashMap<>();
                for (int i = 0; i < route.names().size(); i++) {
                    params.put(route.names().get(i), m.group(i + 1));
                }
                return new Match(route, params);
            }
        }
        if (pathExists) {
            throw new MethodNotAllowed();
        }
        return null;
    }
}
