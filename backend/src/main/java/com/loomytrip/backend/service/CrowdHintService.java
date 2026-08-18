package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.response.CrowdHintResponse;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CrowdHintService {

    private static final double UNIFORM_BASELINE = 0.25;
    private static final double HIGH_THRESHOLD = 1.05;
    private static final double LOW_THRESHOLD = 0.95;

    /** Mirrors ML/data/processed/crowd_seasonal_index.csv (national quarterly proxy). */
    private static final Map<Integer, Double> SEASONAL_INDEX = Map.of(
            1, 0.2334,
            2, 0.2445,
            3, 0.2556,
            4, 0.2665
    );

    public CrowdHintResponse getHint(LocalDate date) {
        int quarter = ((date.getMonthValue() - 1) / 3) + 1;
        double index = SEASONAL_INDEX.getOrDefault(quarter, UNIFORM_BASELINE);
        String level = classify(index);
        String note = "Q" + quarter + " has historically accounted for about "
                + Math.round(index * 100) + "% of Singapore's annual tourism receipts — a "
                + switch (level) {
                    case "high" -> "busier-than-average";
                    case "low" -> "quieter-than-average";
                    default -> "roughly average";
                }
                + " season nationwide. This is a national quarterly proxy, not a per-attraction forecast.";
        return new CrowdHintResponse(quarter, index, level, note);
    }

    private static String classify(double index) {
        if (index > UNIFORM_BASELINE * HIGH_THRESHOLD) {
            return "high";
        }
        if (index < UNIFORM_BASELINE * LOW_THRESHOLD) {
            return "low";
        }
        return "medium";
    }
}
