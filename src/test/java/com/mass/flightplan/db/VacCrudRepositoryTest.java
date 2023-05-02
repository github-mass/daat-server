package com.mass.flightplan.db;

import com.mass.flightplan.vac.RunwayInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.util.Pair;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class VacCrudRepositoryTest {

    @Autowired
    VacCrudRepository repository;

    @Test
    void testInsertOne() {
        AirportEntity ae = AirportEntity.builder()
                                        .code("LFPG")
                                        .name("PARIS CHARLES DE GAULLE")
                                        .altitude(392)
                                        .localPressure(14)
                                        .coordinates(new Point(2.5477777777777777, 49.00972222222222))
                                        .contactInfo("Aéroports de Paris. TEL : 01 48 62 25 25 / 71 72.")
                                        .runways(List.of(
                                            RunwayInfo.builder().code("09L").qfu(84).length(2700).width(60).paved(true).build(),
                                            RunwayInfo.builder().code("27R").qfu(264).length(2700).width(60).paved(true).build(),
                                            RunwayInfo.builder().code("09R").qfu(84).length(4200).width(45).paved(true).build(),
                                            RunwayInfo.builder().code("27L").qfu(264).length(4200).width(45).paved(true).build(),
                                            RunwayInfo.builder().code("08L").qfu(84).length(4142).width(45).paved(true).build(),
                                            RunwayInfo.builder().code("26R").qfu(264).length(4142).width(45).paved(true).build(),
                                            RunwayInfo.builder().code("08R").qfu(84).length(2700).width(60).paved(true).build(),
                                            RunwayInfo.builder().code("26L").qfu(264).length(2700).width(60).paved(true).build()
                                        ))
                                        .build();

        repository.save(ae);

        var read = repository.findById("LFPG");
        assertThat(read).isNotEmpty();
        assertThat(read.get()).isEqualTo(ae);
        System.out.println(read.get());
    }

}