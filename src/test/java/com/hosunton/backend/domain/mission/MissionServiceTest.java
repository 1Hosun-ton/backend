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
}
