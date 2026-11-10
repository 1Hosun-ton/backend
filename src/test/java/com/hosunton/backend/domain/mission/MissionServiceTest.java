package com.hosunton.backend.domain.mission;

import com.hosunton.backend.domain.expense.*;
import com.hosunton.backend.domain.user.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Transactional
class MissionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
    @Autowired MissionRepository missions;
    @Autowired MissionRecommendationRepository recommendations;
    @Autowired ExpenseRepository expenses;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired com.hosunton.backend.global.security.jwt.JwtProvider jwtProvider;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    MissionService service;
    Long userId;
    @BeforeEach void setup() {
        userId = users.save(User.create("mission-" + System.nanoTime() + "@test.com", "테스트", "hash", NOW)).getId();
        service = service(CLOCK);
    }
    private MissionService service(Clock clock) {
        return new MissionService(missions, recommendations, expenses, new MissionJudge(missions), clock);
    }
    private void expense(LocalDate date) {
        expenses.save(Expense.create(userId, date, ExpenseItem.DELIVERY, 1000, ExpenseReason.NEED, false, null, null, NOW));
    }
    private Mission mission(LocalDate start, int done) {
        Mission m = missions.saveAndFlush(Mission.create(userId, null, PatternGroup.DEFAULT, MissionLevel.BASIC, "미션", start, NOW));
        em.createNativeQuery("update missions set done_days = :done where id = :id").setParameter("done", done).setParameter("id", m.getId()).executeUpdate();
        em.clear();
        return missions.findById(m.getId()).orElseThrow();
    }
    @Test void 누적_경계와_추천_JSONB_재사용_및_정확히_7일_만료() {
        for (int i = 0; i < 4; i++) expense(TODAY);
        var four = service.getOverview(userId);
        assertThat(four.report()).isNull();
        assertThat(four.recommendations()).isEmpty();
        expense(TODAY);
        var five = service.getOverview(userId);
        assertThat(five.recommendations()).hasSize(3);
        em.flush(); em.clear();
        var again = service.getOverview(userId);
        assertThat(again.recommendationSetId()).isEqualTo(five.recommendationSetId());
        assertThat(again.recommendations()).isEqualTo(five.recommendations());
        var expired = service(Clock.fixed(NOW.plus(7, ChronoUnit.DAYS), CLOCK.getZone())).getOverview(userId);
        assertThat(expired.recommendationSetId()).isNotEqualTo(five.recommendationSetId());
    }
    @Test void 마지막날은_진행중이며_기록_부족해도_미션은_유지() {
        var m = mission(TODAY.minusDays(6), 5);
        var response = service.getOverview(userId);
        assertThat(response.mission().missionId()).isEqualTo(m.getId());
        assertThat(response.mission().status()).isEqualTo(MissionStatus.IN_PROGRESS);
        assertThat(response.report()).isNull();
        assertThat(missions.findById(m.getId()).orElseThrow().getJudgedAt()).isNull();
    }
    @Test void 실천_4일은_실패하고_5일은_달성하며_판정은_한번만() {
        var failed = mission(TODAY.minusDays(7), 4);
        var response = service.getOverview(userId);
        assertThat(response.mission().status()).isEqualTo(MissionStatus.FAILED);
        assertThat(response.mission().feedbackRequired()).isTrue();
        assertThat(missions.findById(failed.getId()).orElseThrow().getJudgedAt()).isEqualTo(NOW);
        var achieved = mission(TODAY.minusDays(7), 5);
        service.getOverview(userId);
        assertThat(missions.findById(achieved.getId()).orElseThrow().getResult()).isEqualTo(MissionResult.ACHIEVED);
        service(Clock.fixed(NOW.plusSeconds(60), CLOCK.getZone())).getOverview(userId);
        assertThat(missions.findById(achieved.getId()).orElseThrow().getJudgedAt()).isEqualTo(NOW);
    }
    @Test void 주간_실천율_귀속과_Home_조회() {
        for (int i = 0; i < 5; i++) expense(TODAY);
        mission(TODAY.minusDays(3), 3); // 지난주 시작
        assertThat(service.getOverview(userId).report().missionCompletionRate()).isNull();
        var home = new MissionQueryService(missions, new MissionJudge(missions), CLOCK).getActiveMission(userId);
        assertThat(home.status()).isEqualTo(MissionStatus.IN_PROGRESS);
        assertThat(service.getOverview(userId).recommendations()).isEmpty();
        missions.deleteAll(); missions.flush();
        mission(TODAY, 3);
        assertThat(service.getOverview(userId).report().missionCompletionRate()).isEqualTo(42.86);
    }
    @Test void 실제_JWT로_overview_응답을_확인한다() throws Exception {
        for (int i = 0; i < 5; i++) expense(TODAY);
        String token = jwtProvider.createAccessToken(userId).value();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/missions/overview")
                .header("Authorization", "Bearer " + token))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.cumulativeExpenseCount").value(5))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.recommendations.length()").value(3))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.recommendations[0].level").value("BASIC"));
    }

    private com.hosunton.backend.domain.mission.dto.MissionStartRequest startRequest(Instant createdAt) {
        var set = recommendations.saveAndFlush(MissionRecommendation.create(userId,
                MissionRecommender.recommend(PatternGroup.DEFAULT, MissionLevel.BASIC), MissionLevel.BASIC, createdAt));
        return new com.hosunton.backend.domain.mission.dto.MissionStartRequest(set.getId(), 0);
    }
    private void error(Runnable action, com.hosunton.backend.global.exception.ErrorCode code) {
        org.assertj.core.api.Assertions.assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(com.hosunton.backend.global.exception.BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
    @Test void 시작_소유권과_만료_및_저장된_추천만_사용() {
        var expired = startRequest(NOW.minus(7, ChronoUnit.DAYS));
        error(() -> service.start(userId, expired), com.hosunton.backend.global.exception.ErrorCode.RECOMMENDATION_EXPIRED);
        var request = startRequest(NOW);
        error(() -> service.start(-1L, request), com.hosunton.backend.global.exception.ErrorCode.NOT_FOUND);
        error(() -> service.start(userId, new com.hosunton.backend.domain.mission.dto.MissionStartRequest(request.recommendationSetId(), 3)),
                com.hosunton.backend.global.exception.ErrorCode.VALIDATION_ERROR);
        var m = service.start(userId, request);
        assertThat(m.startedOn()).isEqualTo(TODAY);
        assertThat(m.endsOn()).isEqualTo(TODAY.plusDays(6));
        assertThat(m.text()).isEqualTo(MissionCatalog.text(PatternGroup.DEFAULT, MissionLevel.BASIC));
        assertThat(recommendations.findById(request.recommendationSetId()).orElseThrow().isSelected()).isTrue();
        error(() -> service.start(userId, request), com.hosunton.backend.global.exception.ErrorCode.RECOMMENDATION_EXPIRED);
        var other = startRequest(NOW);
        error(() -> service.start(userId, other), com.hosunton.backend.global.exception.ErrorCode.MISSION_ALREADY_IN_PROGRESS);
        assertThat(recommendations.findById(other.recommendationSetId()).orElseThrow().isSelected()).isFalse();
    }
    @Test void 체크_마지막날_허용_다음날_거부_메모_보존과_중복() {
        var m = mission(TODAY.minusDays(6), 5);
        error(() -> service.check(-1L, m.getId(), null), com.hosunton.backend.global.exception.ErrorCode.NOT_FOUND);
        var checked = service.check(userId, m.getId(), new com.hosunton.backend.domain.mission.dto.MissionCheckRequest("기록"));
        assertThat(checked.doneDays()).isEqualTo((short) 6);
        assertThat(checked.checkedOn()).isEqualTo(TODAY);
        error(() -> service.check(userId, m.getId(), null), com.hosunton.backend.global.exception.ErrorCode.ALREADY_CHECKED_TODAY);
        var tomorrow = service(Clock.offset(CLOCK, Duration.ofDays(1)));
        error(() -> tomorrow.check(userId, m.getId(), null), com.hosunton.backend.global.exception.ErrorCode.MISSION_NOT_ACTIVE);
        assertThat(missions.findById(m.getId()).orElseThrow().getMemo()).isEqualTo("기록");
    }
    @Test void 메모_생략하면_유지하고_보내면_덮어쓴다() {
        var m = mission(TODAY, 0);
        service.check(userId, m.getId(), new com.hosunton.backend.domain.mission.dto.MissionCheckRequest("첫날"));
        var tomorrow = service(Clock.offset(CLOCK, Duration.ofDays(1)));
        assertThat(tomorrow.check(userId, m.getId(), null).memo()).isEqualTo("첫날");
        var next = service(Clock.offset(CLOCK, Duration.ofDays(2)));
        assertThat(next.check(userId, m.getId(), new com.hosunton.backend.domain.mission.dto.MissionCheckRequest("새 메모")).memo()).isEqualTo("새 메모");
    }
    @Test void 실패_사유는_실패만_허용하며_재입력_가능() {
        var request = new com.hosunton.backend.domain.mission.dto.MissionDifficultyRequest(MissionDifficulty.FORGOT);
        var m = mission(TODAY, 0);
        error(() -> service.updateDifficulty(userId, m.getId(), request), com.hosunton.backend.global.exception.ErrorCode.MISSION_NOT_FAILED);
        error(() -> service.updateDifficulty(-1L, m.getId(), request), com.hosunton.backend.global.exception.ErrorCode.NOT_FOUND);
        var later = service(Clock.offset(CLOCK, Duration.ofDays(7)));
        assertThat(later.updateDifficulty(userId, m.getId(), request).feedbackRequired()).isFalse();
        assertThat(later.updateDifficulty(userId, m.getId(), new com.hosunton.backend.domain.mission.dto.MissionDifficultyRequest(MissionDifficulty.NO_TIME)).difficulty()).isEqualTo(MissionDifficulty.NO_TIME);
        var achieved = mission(TODAY.minusDays(7), 5);
        error(() -> service.updateDifficulty(userId, achieved.getId(), request), com.hosunton.backend.global.exception.ErrorCode.MISSION_NOT_FAILED);
    }
    @Test void JWT로_시작_체크_사유와_요청_검증() throws Exception {
        var request = startRequest(NOW);
        String token = "Bearer " + jwtProvider.createAccessToken(userId).value();
        var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/missions")
                .header("Authorization", token).contentType("application/json")
                .content("{\"recommendationSetId\":" + request.recommendationSetId() + ",\"optionIndex\":0}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andReturn();
        var id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).path("data").path("missionId").asLong();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/missions/" + id + "/checks")
                .header("Authorization", token))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.doneDays").value(1));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/missions/" + id + "/difficulty")
                .header("Authorization", token).contentType("application/json").content("{\"difficulty\":\"FORGOT\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code").value("MISSION_NOT_FAILED"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/missions")
                .header("Authorization", token).contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/missions/" + id + "/checks")
                .header("Authorization", token).contentType("application/json").content("{\"memo\":\"" + "a".repeat(101) + "\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void 동시_시작과_체크는_각각_한번만_성공() throws Exception {
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        // setup의 사용자 생성은 별도 repository 트랜잭션으로 커밋된다. 테스트 후 본인 데이터만 CASCADE 삭제.
        var request = tx.execute(status -> startRequest(NOW));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var outcomes = concurrent(pool, () -> tx.execute(status -> service.start(userId, request)));
            assertThat(outcomes.stream().filter(v -> v instanceof com.hosunton.backend.domain.mission.dto.MissionStartResponse).count()).isEqualTo(1);
            assertThat(outcomes).contains(com.hosunton.backend.global.exception.ErrorCode.RECOMMENDATION_EXPIRED);
            Long id = tx.execute(status -> missions.findByUserIdAndResultIsNull(userId).orElseThrow().getId());
            var checks = concurrent(pool, () -> tx.execute(status -> service.check(userId, id, null)));
            assertThat(checks.stream().filter(v -> v instanceof com.hosunton.backend.domain.mission.dto.MissionCheckResponse).count()).isEqualTo(1);
            assertThat(checks).contains(com.hosunton.backend.global.exception.ErrorCode.ALREADY_CHECKED_TODAY);
            assertThat(tx.execute(status -> missions.findById(id).orElseThrow().getDoneDays()).shortValue()).isEqualTo((short) 1);
        } finally {
            pool.shutdownNow();
            tx.executeWithoutResult(status -> users.deleteById(userId));
        }
    }
    private java.util.List<Object> concurrent(java.util.concurrent.ExecutorService pool, java.util.concurrent.Callable<?> action) throws Exception {
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<Object> task = () -> {
            barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
            try { return action.call(); }
            catch (com.hosunton.backend.global.exception.BusinessException e) { return e.getErrorCode(); }
        };
        var first = pool.submit(task);
        var second = pool.submit(task);
        return java.util.List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS), second.get(20, java.util.concurrent.TimeUnit.SECONDS));
    }
}
