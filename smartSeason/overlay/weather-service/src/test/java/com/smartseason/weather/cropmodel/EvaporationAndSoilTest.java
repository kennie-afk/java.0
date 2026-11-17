package com.smartseason.weather.cropmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EvaporationAndSoilTest {

    @Test
    void highlandEvaporationIsInTheUsualRange() {
        // Equatorial highlands, a clear dry-season day: reference ET is normally 4-6 mm.
        double et = Evaporation.hargreaves(-0.3, LocalDate.of(2026, 2, 1), 26, 11);
        assertTrue(et > 3.5 && et < 6.5, "got " + et);
    }

    @Test
    void aWiderTemperatureRangeMeansMoreEvaporation() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        assertTrue(Evaporation.hargreaves(0, d, 28, 10) > Evaporation.hargreaves(0, d, 24, 14));
    }

    @Test
    void textureIsReadFromClayAndSand() {
        assertEquals("CLAY", SoilProfile.fromClaySand(52, 15).texture());
        assertEquals("SAND", SoilProfile.fromClaySand(4, 90).texture());
        assertEquals("SANDY_LOAM", SoilProfile.fromClaySand(12, 74).texture());
        assertEquals("CLAY_LOAM", SoilProfile.fromClaySand(32, 30).texture());
        assertEquals("LOAM", SoilProfile.fromClaySand(20, 40).texture());
        assertEquals(SoilProfile.Source.MEASURED, SoilProfile.fromClaySand(20, 40).source());
    }

    @Test
    void userSoilNamesAreAcceptedAndJunkIsNot() {
        assertEquals("CLAY_LOAM", SoilProfile.fromName("clay loam").orElseThrow().texture());
        assertTrue(SoilProfile.fromName("granite").isEmpty());
        assertTrue(SoilProfile.fromName(null).isEmpty());
    }

    @Test
    void soilGridsAnswerIsWeightedByLayerThickness() throws Exception {
        // Shaped like the SoilGrids v2 reply: g/kg with a divisor of 10 to percent.
        String json = """
                {"properties":{"layers":[
                 {"name":"clay","unit_measure":{"d_factor":10},"depths":[
                   {"range":{"top_depth":0,"bottom_depth":10},"values":{"mean":300}},
                   {"range":{"top_depth":10,"bottom_depth":30},"values":{"mean":400}}]},
                 {"name":"sand","unit_measure":{"d_factor":10},"depths":[
                   {"range":{"top_depth":0,"bottom_depth":10},"values":{"mean":250}},
                   {"range":{"top_depth":10,"bottom_depth":30},"values":{"mean":200}}]}]}}
                """;
        double[] cs = SoilService.parse(new ObjectMapper().readTree(json)).orElseThrow();
        assertEquals((30.0 * 10 + 40.0 * 20) / 30, cs[0], 1e-9);
        assertEquals((25.0 * 10 + 20.0 * 20) / 30, cs[1], 1e-9);
        assertTrue(SoilService.parse(new ObjectMapper().readTree("{}")).isEmpty());
    }
}
