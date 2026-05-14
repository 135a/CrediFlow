package com.crediflow.user.realname.provider;

import com.crediflow.common.trace.TraceIdContext;
import com.crediflow.user.realname.config.RealnameProperties;
import com.crediflow.user.realname.json.JsonPaths;
import com.crediflow.user.realname.model.RealnameVerifyCommand;
import com.crediflow.user.realname.model.RealnameVerifyResult;
import com.crediflow.user.realname.signature.RealnameSignatureStrategy;
import com.crediflow.user.realname.signature.RealnameSignatureStrategySelector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class HttpRealnameProvider implements RealnameProvider {

    private final RealnameProperties properties;
    private final RealnameSignatureStrategySelector strategySelector;
    private final ObjectMapper objectMapper;

    public HttpRealnameProvider(
            RealnameProperties properties,
            RealnameSignatureStrategySelector strategySelector,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.strategySelector = strategySelector;
        this.objectMapper = objectMapper;
    }

    @Override
    public RealnameVerifyResult verify(RealnameVerifyCommand command) {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            return RealnameVerifyResult.retryLater("CFG_BASE_URL", "实名通道未配置");
        }
        long ts = System.currentTimeMillis();
        String bodyNoSig = applyTemplate(properties.getRequestBodyTemplate(), command, ts, "");
        RealnameSignatureStrategy strategy = strategySelector.resolve();
        String signature = strategy.sign(bodyNoSig, command, ts);
        String body = applyTemplate(properties.getRequestBodyTemplate(), command, ts, signature);

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        rf.setReadTimeout((int) properties.getReadTimeout().toMillis());
        RestClient client = RestClient.builder().requestFactory(rf).build();

        try {
            String raw = client.post()
                    .uri(properties.getBaseUrl())
                    .headers(h -> {
                        h.setContentType(MediaType.APPLICATION_JSON);
                        String tid = TraceIdContext.getTraceId();
                        if (StringUtils.hasText(tid)) {
                            h.set("X-Trace-Id", tid);
                        }
                        properties.getExtraHeaders().forEach((k, v) -> {
                            if (StringUtils.hasText(k) && v != null) {
                                h.add(k, expandHeader(v, command, ts));
                            }
                        });
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResponse(raw);
        } catch (HttpServerErrorException e) {
            return RealnameVerifyResult.retryLater("HTTP_" + e.getStatusCode().value(), "通道繁忙");
        } catch (ResourceAccessException e) {
            return RealnameVerifyResult.retryLater("NET_TIMEOUT", "网络超时");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() >= 500) {
                return RealnameVerifyResult.retryLater("HTTP_" + e.getStatusCode().value(), "通道繁忙");
            }
            String bodyStr = e.getResponseBodyAsString();
            RealnameVerifyResult parsed = tryParseFailure(bodyStr);
            if (parsed != null) {
                return parsed;
            }
            return RealnameVerifyResult.retryLater("HTTP_" + e.getStatusCode().value(), "请稍后重试");
        } catch (Exception e) {
            return RealnameVerifyResult.retryLater("HTTP_ERROR", "请稍后重试");
        }
    }

    private RealnameVerifyResult tryParseFailure(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            return parseBody(root);
        } catch (Exception ignored) {
            return null;
        }
    }

    private RealnameVerifyResult parseResponse(String raw) throws Exception {
        if (!StringUtils.hasText(raw)) {
            return RealnameVerifyResult.retryLater("EMPTY_BODY", "空响应");
        }
        JsonNode root = objectMapper.readTree(raw);
        return parseBody(root);
    }

    private RealnameVerifyResult parseBody(JsonNode root) {
        JsonNode matchedNode = JsonPaths.at(root, properties.getResponseMatchedJsonPath());
        if (matchedNode == null || matchedNode.isNull()) {
            return RealnameVerifyResult.retryLater("PARSE_UNKNOWN", "请稍后重试");
        }
        boolean matched = JsonPaths.asBoolean(matchedNode, false);
        boolean idValid = JsonPaths.asBoolean(JsonPaths.at(root, properties.getResponseIdValidJsonPath()), true);
        String txn = JsonPaths.asText(JsonPaths.at(root, properties.getResponseTxnNoJsonPath()));
        String msg = JsonPaths.asText(JsonPaths.at(root, properties.getResponseMessageJsonPath()));

        if (matched && idValid) {
            return RealnameVerifyResult.success(txn == null ? "" : txn);
        }
        return RealnameVerifyResult.terminal(matched, idValid, txn, "PROVIDER_NO_MATCH", msg == null ? "核验未通过" : msg);
    }

    private String applyTemplate(String tpl, RealnameVerifyCommand c, long ts, String signature) {
        if (tpl == null) {
            return "{}";
        }
        return tpl.replace("{{realName}}", escapeJson(c.realName()))
                .replace("{{idCardNo}}", escapeJson(c.idCardNo()))
                .replace("{{timestamp}}", Long.toString(ts))
                .replace("{{appKey}}", escapeJson(properties.getAppKey()))
                .replace("{{signature}}", escapeJson(signature));
    }

    private String expandHeader(String v, RealnameVerifyCommand c, long ts) {
        return v.replace("{{appKey}}", properties.getAppKey() == null ? "" : properties.getAppKey())
                .replace("{{timestamp}}", Long.toString(ts))
                .replace("{{realName}}", c.realName() == null ? "" : c.realName());
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
