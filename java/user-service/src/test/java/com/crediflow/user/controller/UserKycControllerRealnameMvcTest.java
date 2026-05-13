package com.crediflow.user.controller;

import com.crediflow.common.exception.ErrorCode;
import com.crediflow.common.exception.GlobalExceptionHandler;
import com.crediflow.user.dto.UserKycStep2Response;
import com.crediflow.user.entity.UserKyc;
import com.crediflow.user.realname.service.RealnameVerificationService;
import com.crediflow.user.service.UserKycService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserKycControllerRealnameMvcTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserKycService userKycService;

    @Mock
    private RealnameVerificationService realnameVerificationService;

    @BeforeEach
    void setUp() {
        UserKycController controller = new UserKycController();
        ReflectionTestUtils.setField(controller, "userKycService", userKycService);
        ReflectionTestUtils.setField(controller, "realnameVerificationService", realnameVerificationService);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void step3RejectedWhenRealnameNotVerified() throws Exception {
        UserKyc kyc = new UserKyc();
        kyc.setStepStatus(2);
        kyc.setRealnameStatus("FAILED");
        when(userKycService.getByUserId(1L)).thenReturn(kyc);

        mockMvc.perform(
                        post("/api/app/user/kyc/step3")
                                .param("userId", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.REALNAME_NOT_VERIFIED.getCode()));
    }

    @Test
    void step2DelegatesToVerificationService() throws Exception {
        when(realnameVerificationService.submitStep2(eq(1L), anyString(), anyString(), eq("k1")))
                .thenReturn(new UserKycStep2Response("1101**********1234", "VERIFIED", "MOCK-1"));

        String body = objectMapper.writeValueAsString(java.util.Map.of("realName", "测 试", "idCardNo", "110101199001011234"));
        mockMvc.perform(
                        post("/api/app/user/kyc/step2")
                                .param("userId", "1")
                                .header("Idempotency-Key", "k1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.realnameStatus").value("VERIFIED"));
    }
}
