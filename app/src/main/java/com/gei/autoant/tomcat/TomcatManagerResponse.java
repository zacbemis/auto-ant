package com.gei.autoant.tomcat;

public record TomcatManagerResponse(int statusCode, String body) {
    public boolean successful() {
        if (statusCode < 200 || statusCode >= 300 || body == null) {
            return false;
        }
        String firstLine = body.lines().findFirst().orElse("").trim();
        return firstLine.startsWith("OK - ") && firstLine.length() > "OK - ".length();
    }
}
