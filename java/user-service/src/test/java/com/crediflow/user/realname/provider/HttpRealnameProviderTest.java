package com.crediflow.user.realname.provider;

import com.crediflow.user.realname.config.RealnameProperties;
import com.crediflow.user.realname.model.RealnameVerifyCommand;
import com.crediflow.user.realname.model.RealnameVerifyResult;
import com.crediflow.user.realname.signature.NoOpRealnameSignatureStrategy;
import com.crediflow.user.realname.signature.RealnameSignatureStrategySelector;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRealnameProviderTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void successMapsToEffectiveSuccess() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"match\":true,\"idValid\":true,\"txnNo\":\"T-OK\"}"));

        HttpRealnameProvider http = buildProvider();
        RealnameVerifyResult r =
                http.verify(new RealnameVerifyCommand(1L, "张三", "110101199001011234"));
        assertThat(r.effectiveSuccess()).isTrue();
        assertThat(r.providerTxnNo()).isEqualTo("T-OK");
    }

    @Test
    void http500IsRetryable() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("err"));

        HttpRealnameProvider http = buildProvider();
        RealnameVerifyResult r =
                http.verify(new RealnameVerifyCommand(1L, "张三", "110101199001011234"));
        assertThat(r.retryable()).isTrue();
    }

    @Test
    void explicitMismatchIsTerminal() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"match\":false,\"idValid\":true,\"txnNo\":\"T-X\",\"message\":\"不一致\"}"));

        HttpRealnameProvider http = buildProvider();
        RealnameVerifyResult r =
                http.verify(new RealnameVerifyCommand(1L, "张三", "110101199001011234"));
        assertThat(r.terminalFailure()).isTrue();
        assertThat(r.retryable()).isFalse();
    }

    private HttpRealnameProvider buildProvider() {
        RealnameProperties props = new RealnameProperties();
        props.setMockSuccess(false);
        props.setBaseUrl(server.url("/").toString());
        props.setConnectTimeout(Duration.ofMillis(500));
        props.setReadTimeout(Duration.ofMillis(500));
        props.setProviderType("noop");
        Map<String, com.crediflow.user.realname.signature.RealnameSignatureStrategy> beans =
                Map.of("noopRealnameSignatureStrategy", new NoOpRealnameSignatureStrategy());
        RealnameSignatureStrategySelector sel = new RealnameSignatureStrategySelector(props, beans);
        return new HttpRealnameProvider(props, sel, new ObjectMapper());
    }
}
