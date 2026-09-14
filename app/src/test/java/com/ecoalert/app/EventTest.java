package com.ecoalert.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
public class EventTest {
    @Test public void comparisonDoesNotInventMarketDirection() {
        assertEquals("↑ Supérieur au prévu",Event.comparison("3.2%","3.1%"));
        assertEquals("↓ Inférieur au prévu",Event.comparison("-0,2%","0,0%"));
        assertEquals("= Conforme au prévu",Event.comparison("0","0.0"));
        assertEquals("",Event.comparison(null,"3%"));
        assertEquals("Comparaison à interpréter",Event.comparison("2M","2000K"));
    }
    @Test public void weekIncludesSundayButNotNextMonday() {
        ZoneId z=ZoneId.of("Africa/Casablanca");
        LocalDate monday=LocalDate.of(2026,9,14);
        Event sunday=event(LocalDate.of(2026,9,20).atTime(23,30).atZone(z).toInstant());
        Event next=event(LocalDate.of(2026,9,21).atStartOfDay(z).toInstant());
        assertTrue(sunday.inPeriod(monday,z,true));
        assertFalse(next.inPeriod(monday,z,true));
        assertFalse(sunday.inPeriod(monday,z,false));
    }
    @Test public void dateUsesSelectedZone() {
        Event e=event(Instant.parse("2026-09-14T23:30:00Z"));
        assertTrue(e.inPeriod(LocalDate.of(2026,9,15),ZoneId.of("Africa/Casablanca"),false));
    }
    private Event event(Instant time) { return new Event("x","x","US",time,3,null,null,null,null,"","",false); }
}
