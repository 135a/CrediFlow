package com.crediflow.user.realname.env;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealnameMockSafetyInitializerTest {

    @Test
    void prodWithMockAbortsInitialize() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty(RealnameMockSafetyInitializer.PROP_MOCK, "true");
        ctx.setEnvironment(env);
        assertThatThrownBy(() -> new RealnameMockSafetyInitializer().initialize(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境禁止");
    }

    @Test
    void devWithMockDoesNotThrow() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty(RealnameMockSafetyInitializer.PROP_MOCK, "true");
        ctx.setEnvironment(env);
        new RealnameMockSafetyInitializer().initialize(ctx);
        assertThat(ctx.isActive()).isFalse();
    }

    @Test
    void isProduction_trueForProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThat(RealnameMockSafetyInitializer.isProduction(env)).isTrue();
    }
}
