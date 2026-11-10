package com.hosunton.backend.domain.mission;

public final class MissionCatalog {
    private MissionCatalog() {}
    public static String text(PatternGroup group, MissionLevel level) {
        String[] texts = switch (group) {
            case STRESS_DELIVERY -> new String[]{"주문 전 물 한 잔 마시고 5분 뒤 결정하기", "주문 전 집에 있는 식재료 확인하기", "이번 주 배달 주문 2회 이하로 줄이기"};
            case IMPULSE_SHOPPING -> new String[]{"결제 전 \"정말 필요한가?\" 한 번 떠올리기", "충동구매 전 10분 기다려 보기", "구매 전 24시간 기다려 보기"};
            case HABIT_CAFE_FOOD -> new String[]{"결제 전 \"습관인가, 필요인가?\" 한 번 생각하기", "카페·식비 결제 전 오늘 이미 쓴 금액 확인하기", "이번 주 카페 방문 횟수를 정해 두고 지키기"};
            case DEFAULT -> new String[]{"하루 한 번 남은 예산 확인하기", "결제 전 이번 주 남은 예산 확인하기", "결제 전 \"후회할 것 같은지\" 미리 예상해 보기"};
        };
        return texts[level.ordinal()];
    }
}
