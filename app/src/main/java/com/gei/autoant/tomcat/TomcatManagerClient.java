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
        URL url = reloadUrl(managerUrl, contextPath);
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

    private URL reloadUrl(String managerUrl, String contextPath) throws IOException {
        String base = managerUrl.endsWith("/") ? managerUrl.substring(0, managerUrl.length() - 1) : managerUrl;
        String encodedPath = URLEncoder.encode(contextPath, StandardCharsets.UTF_8.name());
        return new URL(base + "/reload?path=" + encodedPath);
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
}