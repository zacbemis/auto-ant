package com.gei.autoant.tomcat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomcatManagerClientTest {
    private final TomcatManagerClient client = new TomcatManagerClient();

    @Test void successRequiresHttpAndTomcatOkBody() {
        assertTrue(new TomcatManagerResponse(200, "OK - Listed applications").successful());
        assertFalse(new TomcatManagerResponse(200, "FAIL - Not authorized").successful());
        assertFalse(new TomcatManagerResponse(500, "OK - misleading").successful());
        assertFalse(new TomcatManagerResponse(200, "").successful());
    }

    @Test void parsesExactContextAndAllStatesFailClosed() {
        String list = "OK - Listed applications\n/app2:running:0:/x\n/app:stopped:0:/app";
        assertEquals(TomcatManagerClient.ContextState.STOPPED, client.parseContextState(list, "/app"));
        assertEquals(TomcatManagerClient.ContextState.RUNNING, client.parseContextState(list, "/app2"));
        assertEquals(TomcatManagerClient.ContextState.MISSING, client.parseContextState(list, "/missing"));
        assertEquals(TomcatManagerClient.ContextState.UNKNOWN_ERROR, client.parseContextState("FAIL - broken\n/app:stopped:0:/app", "/app"));
        assertEquals(TomcatManagerClient.ContextState.UNKNOWN_ERROR, client.parseContextState("OK - Listed\nmalformed", "/app"));
        assertEquals(TomcatManagerClient.ContextState.UNKNOWN_ERROR, client.contextState(new TomcatManagerResponse(200, "FAIL - broken"), "/app"));
    }

    @Test void realHttpResponseStillRequiresTomcatSuccessBody() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/manager/text/list", exchange -> {
            byte[] body = "FAIL - Manager rejected request".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            TomcatManagerResponse response = client.list("http://127.0.0.1:" + server.getAddress().getPort() + "/manager/text", "user", "secret");
            assertFalse(response.successful());
            assertEquals(TomcatManagerClient.ContextState.UNKNOWN_ERROR, client.contextState(response, "/app"));
        } finally {
            server.stop(0);
        }
    }
}
