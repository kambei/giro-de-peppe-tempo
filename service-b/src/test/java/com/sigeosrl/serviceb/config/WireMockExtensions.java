package com.sigeosrl.serviceb.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.util.Map;

public class WireMockExtensions implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer();
        wireMockServer.start(); 

        wireMockServer.stubFor(get(urlEqualTo("/hello"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            "Hello from Service A"
                        )));

        wireMockServer.stubFor(get(urlMatching(".*")).atPriority(10).willReturn(aResponse().proxiedFrom("http://localhost:8080")));

        return Map.of("quarkus.rest-client.\"org.acme.rest.client.ExtensionsService\".url", wireMockServer.baseUrl()); 
    }

    @Override
    public void stop() {
        if (null != wireMockServer) {
            wireMockServer.stop();  
        }
    }
}