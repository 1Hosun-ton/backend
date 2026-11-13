package com.hosunton.backend.global.security.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void 같은_입력은_항상_같은_해시() {
        assertThat(TokenHasher.sha256("token")).isEqualTo(TokenHasher.sha256("token"));
    }

    @Test
    void 다른_입력은_다른_해시() {
        assertThat(TokenHasher.sha256("token-a")).isNotEqualTo(TokenHasher.sha256("token-b"));
    }

    @Test
    void 결과는_64자_hex() {
        assertThat(TokenHasher.sha256("token")).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void SHA_256_알려진_값과_일치한다() {
        assertThat(TokenHasher.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}