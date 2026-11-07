package com.hosunton.backend.global.exception;

import com.hosunton.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 성공_응답은_data로_감싼다() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("hosun"));
    }

    @Test
    void 비즈니스_예외는_에러코드의_상태와_메시지를_반환한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.message").value("이미 가입된 이메일이에요"))
                .andExpect(jsonPath("$.error.fieldErrors").doesNotExist());
    }

    @Test
    void 검증_실패는_fieldErrors를_포함한다() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"));
    }

    @Test
    void 잘못된_JSON은_VALIDATION_ERROR() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 예상하지_못한_예외는_500() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        record NameResponse(String name) {}
        record NameRequest(@NotBlank(message = "이름을 입력해 주세요") String name) {}

        @GetMapping("/ok")
        ApiResponse<NameResponse> ok() {
            return ApiResponse.ok(new NameResponse("hosun"));
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody NameRequest request) {
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("boom");
        }
    }
}