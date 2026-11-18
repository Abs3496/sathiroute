package com.routemesh.allocation.domain;

import com.routemesh.geo.domain.GeoPoint;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerAllocatorTest {
    private static final GeoPoint CUSTOMER = new GeoPoint(12.9716, 77.5946);
    private static final AllocationRequest NURSING = new AllocationRequest("NURSING", "HOME_NURSING", CUSTOMER,
            LocalTime.of(10, 0), LocalTime.of(11, 0));

    @Test
    void filtersBySkillStatusAvailabilityHoursAndServiceRadiusBeforeRanking() {
        var nearbyUnqualified = partner(1, 12.972, 77.595,
                Set.of(new PartnerCandidate.Skill("COUNSELLING", "HOME_NURSING", true)), true, true, 20, 0);
        var inactive = partner(2, 12.972, 77.595, skill("NURSING", "HOME_NURSING", true), false, true, 20, 0);
        var unavailable = partner(3, 12.972, 77.595, skill("NURSING", "HOME_NURSING", true), true, false, 20, 0);
        var tooFar = partner(4, 13.5, 77.9, skill("NURSING", "HOME_NURSING", true), true, true, 1, 0);
        var eligible = partner(5, 12.98, 77.60, skill("NURSING", "HOME_NURSING", true), true, true, 20, 1);
        var unverified = partner(6, 12.9717, 77.5947, skill("NURSING", "HOME_NURSING", false), true, true, 20, 0);

        assertThat(PartnerAllocator.rank(NURSING,
                List.of(nearbyUnqualified, inactive, unavailable, tooFar, eligible, unverified)))
                .extracting(r -> r.partner().partnerId())
                .containsExactly(eligible.partnerId());
    }

    @Test
    void ranksNearestFirstThenUsesWorkloadAndStableIdForTies() {
        var farther = partner(4, 12.99, 77.62, skill("NURSING", "HOME_NURSING", true), true, true, 20, 0);
        var loaded = partner(3, 12.972, 77.595, skill("NURSING", "HOME_NURSING", true), true, true, 20, 5);
        var lessLoaded = partner(2, 12.972, 77.595, skill("NURSING", "HOME_NURSING", true), true, true, 20, 1);
        var sameLoadLowerId = partner(1, 12.972, 77.595, skill("NURSING", "HOME_NURSING", true), true, true, 20, 1);

        assertThat(PartnerAllocator.rank(NURSING, List.of(farther, loaded, lessLoaded, sameLoadLowerId)))
                .extracting(r -> r.partner().partnerId())
                .containsExactly(sameLoadLowerId.partnerId(), lessLoaded.partnerId(), loaded.partnerId(), farther.partnerId());
    }

    private static PartnerCandidate partner(int id, double lat, double lng, Set<PartnerCandidate.Skill> skills,
                                            boolean active,
                                            boolean available, double radius, int workload) {
        return new PartnerCandidate(new UUID(0, id), new GeoPoint(lat, lng), skills, active, available,
                LocalTime.of(8, 0), LocalTime.of(18, 0), radius, workload);
    }

    private static Set<PartnerCandidate.Skill> skill(String category, String code, boolean verified) {
        return Set.of(new PartnerCandidate.Skill(category, code, verified));
    }
}
