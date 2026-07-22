package com.gei.autoant.tomcat;

public record TomcatManagerResponse(int statusCode, String body) {
    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }
}