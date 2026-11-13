package com.hosunton.backend.global.security;

import com.hosunton.backend.global.security.jwt.JwtProperties;
import com.hosunton.backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    //보호 경로는 존재하지 않는 경로 사용
    private static final String PROTECTED_PATH = "/api/v1/security-test-protected";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JwtProperties jwtProperties;

    private String expiredAccessToken() {
        // 설정된 유효 시간보다 1분 더 전에 발급 → 유효 시간 설정이 바뀌어도 항상 만료된 토큰
        Duration offset = jwtProperties.accessTokenExpiration().plusMinutes(1).negated();
        Clock past = Clock.offset(Clock.system(ZoneId.of("Asia/Seoul")), offset);
        return new JwtProvider(jwtProperties, past).createAccessToken(1L).value();
    }

    @Test
    void 토큰_없이_보호된_경로를_호출하면_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 위조된_토큰으로_호출하면_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 만료된_토큰으로_호출하면_401_TOKEN_EXPIRED() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + expiredAccessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void 정상_토큰이면_인증을_통과한다() throws Exception {
        String token = jwtProvider.createAccessToken(1L).value();

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 만료된_토큰이_붙어_있어도_공개_경로는_정상_처리된다() throws Exception {

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer " + expiredAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@example.com", "password": "123456"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }
}