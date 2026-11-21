package com.giorgi.gymcrm.component;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.springframework.beans.factory.DisposableBean;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class WorkloadServiceStub implements DisposableBean {

    private static final WireMockServer SERVER = new WireMockServer(options().dynamicPort());

    static synchronized String baseUrl() {
        if (!SERVER.isRunning()) {
            SERVER.start();
        }
        return SERVER.baseUrl();
    }

    public void reset() {
        SERVER.resetAll();
    }

    public void reports(String username, int year, int month, long minutes) {
        SERVER.stubFor(get(urlEqualTo(path(username, year, month))).willReturn(okJson("""
                {"trainerUsername":"%s","year":%d,"month":%d,"trainingSummaryDuration":%d}"""
                .formatted(username, year, month, minutes))));
    }

    public void doesNotKnow(String username) {
        SERVER.stubFor(get(urlPathMatching("/workloads/" + username + "/.*"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"status\":404,\"detail\":\"Trainer not found: " + username + "\"}")));
    }

    public void isDown() {
        SERVER.stubFor(get(urlPathMatching("/workloads/.*")).willReturn(aResponse().withStatus(503)));
    }

    public void dropsConnections() {
        SERVER.stubFor(get(urlPathMatching("/workloads/.*"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    public void failsWithServerError() {
        SERVER.stubFor(get(urlPathMatching("/workloads/.*")).willReturn(aResponse().withStatus(500)));
    }

    public void answersAfter(Duration delay) {
        SERVER.stubFor(get(urlPathMatching("/workloads/.*"))
                .willReturn(okJson("{}").withFixedDelay((int) delay.toMillis())));
    }

    public void verifyCalls(int count, RequestPatternBuilder request) {
        SERVER.verify(count, request);
    }

    public List<LoggedRequest> received(RequestPatternBuilder request) {
        return SERVER.findAll(request);
    }

    public RequestPatternBuilder anyRead() {
        return getRequestedFor(urlPathMatching("/workloads/.*"));
    }

    private static String path(String username, int year, int month) {
        return "/workloads/%s/%d/%d".formatted(username, year, month);
    }

    @Override
    public void destroy() {
        SERVER.stop();
    }
}
