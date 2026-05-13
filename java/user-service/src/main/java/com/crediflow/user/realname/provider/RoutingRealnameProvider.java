package com.crediflow.user.realname.provider;

import com.crediflow.user.realname.config.RealnameProperties;
import com.crediflow.user.realname.model.RealnameVerifyCommand;
import com.crediflow.user.realname.model.RealnameVerifyResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class RoutingRealnameProvider implements RealnameProvider {

    private final RealnameProperties properties;
    private final MockRealnameProvider mockRealnameProvider;
    private final HttpRealnameProvider httpRealnameProvider;

    public RoutingRealnameProvider(
            RealnameProperties properties,
            MockRealnameProvider mockRealnameProvider,
            HttpRealnameProvider httpRealnameProvider) {
        this.properties = properties;
        this.mockRealnameProvider = mockRealnameProvider;
        this.httpRealnameProvider = httpRealnameProvider;
    }

    @Override
    public RealnameVerifyResult verify(RealnameVerifyCommand command) {
        if (properties.isMockSuccess()) {
            return mockRealnameProvider.verify(command);
        }
        return httpRealnameProvider.verify(command);
    }
}
