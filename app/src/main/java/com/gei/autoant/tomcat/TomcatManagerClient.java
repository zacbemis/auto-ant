package com.gei.autoant.tomcat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TomcatManagerClient {
    public TomcatManagerResponse reload(String managerUrl, String contextPath, String user, String password) throws IOException {
        return command(managerUrl, "reload", contextPath, user, password);
    }

    public TomcatManagerResponse stop(String managerUrl, String contextPath, String user, String password) throws IOException {
        return command(managerUrl, "stop", contextPath, user, password);
    }

    public TomcatManagerResponse start(String managerUrl, String contextPath, String user, String password) throws IOException {
        return command(managerUrl, "start", contextPath, user, password);
    }

    public TomcatManagerResponse list(String managerUrl, String user, String password) throws IOException {
        return request(commandUrl(managerUrl, "list", null), user, password);
    }

    public boolean waitUntilRunning(String managerUrl, String contextPath, String user, String password,
                                    int timeoutSeconds, int pollMillis) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L;
        while (true) {
            TomcatManagerResponse response = list(managerUrl, user, password);
            if (contextState(response, contextPath) == ContextState.RUNNING) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(pollMillis);
        }
    }

    public boolean isRunning(String listBody, String contextPath) {
        return parseContextState(listBody, contextPath) == ContextState.RUNNING;
    }

    public ContextState contextState(TomcatManagerResponse response, String contextPath) {
        if (response == null || !response.successful()) {
            return ContextState.UNKNOWN_ERROR;
        }
        return parseContextState(response.body(), contextPath);
    }

    public ContextState parseContextState(String listBody, String contextPath) {
        if (listBody == null || contextPath == null || contextPath.isBlank()) {
            return ContextState.UNKNOWN_ERROR;
        }
        String[] lines = listBody.split("\\R", -1);
        if (lines.length < 1 || !lines[0].trim().startsWith("OK - ")) {
            return ContextState.UNKNOWN_ERROR;
        }
        ContextState found = null;
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.split(":", 4);
            if (fields.length < 4 || fields[0].isBlank() || fields[1].isBlank()) {
                return ContextState.UNKNOWN_ERROR;
            }
            if (!fields[0].equals(contextPath)) {
                continue;
            }
            ContextState parsed = switch (fields[1]) {
                case "running" -> ContextState.RUNNING;
                case "stopped" -> ContextState.STOPPED;
                default -> ContextState.UNKNOWN_ERROR;
            };
            if (found != null || parsed == ContextState.UNKNOWN_ERROR) {
                return ContextState.UNKNOWN_ERROR;
            }
            found = parsed;
        }
        return found == null ? ContextState.MISSING : found;
    }

    private TomcatManagerResponse command(String managerUrl, String command, String contextPath, String user, String password) throws IOException {
        return request(commandUrl(managerUrl, command, contextPath), user, password);
    }

    private TomcatManagerResponse request(URL url, String user, String password) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        if (user != null && !user.isBlank()) {
            String credentials = user + ":" + (password == null ? "" : password);
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encoded);
        }

        int statusCode = connection.getResponseCode();
        String body = readBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        return new TomcatManagerResponse(statusCode, body);
    }

    private URL commandUrl(String managerUrl, String command, String contextPath) throws IOException {
        String base = managerUrl.endsWith("/") ? managerUrl.substring(0, managerUrl.length() - 1) : managerUrl;
        if (contextPath == null) {
            return new URL(base + "/" + command);
        }
        String encodedPath = URLEncoder.encode(contextPath, StandardCharsets.UTF_8.name());
        return new URL(base + "/" + command + "?path=" + encodedPath);
    }

    private String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!builder.isEmpty()) {
                    builder.append(System.lineSeparator());
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }

    public enum ContextState {
        RUNNING,
        STOPPED,
        MISSING,
        UNKNOWN_ERROR
    }
}
