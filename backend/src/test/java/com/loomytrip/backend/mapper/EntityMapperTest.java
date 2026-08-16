package com.loomytrip.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.loomytrip.backend.dto.response.PlanningSessionDetailResponse;
import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.DraftActivity;
import com.loomytrip.backend.entity.DraftPlace;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.PlanningSessionStatus;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripDay;
import com.loomytrip.backend.entity.TripPreference;
import com.loomytrip.backend.entity.TripSchedule;
import com.loomytrip.backend.entity.ValidationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityMapperTest {

    private final EntityMapper mapper = new EntityMapper();

    @Test
    void toTripSummary_derivesPastCurrentAndFutureStatuses() {
        assertThat(summaryFor(LocalDate.now().minusDays(3), 1).status()).isEqualTo("FINISHED");
        assertThat(summaryFor(LocalDate.now(), 1).status()).isEqualTo("ACTIVE");
        assertThat(summaryFor(LocalDate.now().plusDays(1), 2).status()).isEqualTo("NOT_STARTED");
    }

    @Test
    void toTripSummary_mapsPreferenceAndScheduleDetails() {
        Trip trip = trip(LocalDate.now(), 2);
        TripPreference preference = new TripPreference();
        preference.setTravelStyle("food");
        preference.setPreferTransport("transit");
        TripDay day = new TripDay();
        day.setId(10L);
        day.setDaySequence(2);
        day.setTrip(trip);
        Destination destination = new Destination();
        destination.setId(4L);
        destination.setName("Gardens by the Bay");
        destination.setLatitude(new BigDecimal("1.2816"));
        destination.setLongitude(new BigDecimal("103.8636"));
        TripSchedule schedule = new TripSchedule();
        schedule.setId(20L);
        schedule.setTripDay(day);
        schedule.setDestination(destination);
        schedule.setSequence(3);
        schedule.setStartTime(LocalTime.of(14, 0));
        schedule.setPlannedDurationMinutes(90);
        schedule.setLocked(true);
        schedule.setNote("Cloud Forest");

        TripSummaryResponse response = mapper.toTripSummary(trip, preference, List.of(schedule));

        assertThat(response.travelStyle()).isEqualTo("food");
        assertThat(response.preferTransport()).isEqualTo("transit");
        assertThat(response.schedules()).singleElement().satisfies(stop -> {
            assertThat(stop.destination().name()).isEqualTo("Gardens by the Bay");
            assertThat(stop.tripDay().daySequence()).isEqualTo(2);
            assertThat(stop.locked()).isTrue();
        });
    }

    @Test
    void toPlanningSessionDetail_mapsDraftActivitiesAndConfirmedTrip() {
        PlanningSession session = new PlanningSession();
        session.setId(1L);
        session.setTitle("Singapore weekend");
        session.setInitialBrief("MBS and Gardens");
        session.setStatus(PlanningSessionStatus.CONFIRMED);
        session.setDurationDays(2);
        session.setFailureCode(null);
        Trip confirmedTrip = trip(LocalDate.now(), 2);
        confirmedTrip.setId(99L);
        session.setConfirmedTrip(confirmedTrip);

        DraftPlace place = new DraftPlace();
        place.setId(5L);
        place.setName("MBS");
        place.setCategory("attraction");
        place.setValidationStatus(ValidationStatus.VALID);
        place.setSuggestedDay(2);
        place.setStartTime(LocalTime.of(10, 0));
        DraftActivity activity = new DraftActivity();
        activity.setId(6L);
        activity.setTitle("SkyPark");
        activity.setSuggestedDay(2);
        activity.setStartTime(LocalTime.of(10, 0));

        PlanningSessionDetailResponse response = mapper.toPlanningSessionDetail(
                session, List.of(place), Map.of(5L, List.of(activity)));

        assertThat(response.confirmedTripId()).isEqualTo(99L);
        assertThat(response.draftPlaces()).singleElement().satisfies(mappedPlace -> {
            assertThat(mappedPlace.name()).isEqualTo("MBS");
            assertThat(mappedPlace.activities()).extracting(a -> a.title()).containsExactly("SkyPark");
        });
    }

    private TripSummaryResponse summaryFor(LocalDate start, int duration) {
        return mapper.toTripSummary(trip(start, duration), null, List.of());
    }

    private static Trip trip(LocalDate start, int duration) {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setTripName("Trip");
        trip.setStartDate(start);
        trip.setDurationDays(duration);
        return trip;
    }
}
