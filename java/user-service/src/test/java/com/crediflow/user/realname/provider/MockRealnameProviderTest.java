package com.crediflow.user.realname.provider;

import com.crediflow.user.realname.model.RealnameVerifyCommand;
import com.crediflow.user.realname.model.RealnameVerifyResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockRealnameProviderTest {

    private final MockRealnameProvider provider = new MockRealnameProvider();

    @Test
    void returnsMockPrefixedTxn() {
        RealnameVerifyResult r =
                provider.verify(new RealnameVerifyCommand(1L, "张三", "110101199001011234"));
        assertThat(r.effectiveSuccess()).isTrue();
        assertThat(r.providerTxnNo()).startsWith("MOCK-");
    }
}
