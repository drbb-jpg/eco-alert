package com.ecoalert.app;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

public final class Event {
    public final String id, title, country, previous, forecast, actual, revised, reference, source;
    public final Instant time;
    public final int importance;
    public final boolean tentative;
    public Event(String id, String title, String country, Instant time, int importance,
                 String previous, String forecast, String actual, String revised, String reference, String source, boolean tentative) {
        this.id=id; this.title=title; this.country=country; this.time=time; this.importance=importance;
        this.previous=previous; this.forecast=forecast; this.actual=actual; this.revised=revised;
        this.reference=reference; this.source=source; this.tentative=tentative;
    }
    public boolean inPeriod(LocalDate today, ZoneId zone, boolean week) {
        LocalDate date = time.atZone(zone).toLocalDate();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return week ? !date.isBefore(monday) && date.isBefore(monday.plusDays(7)) : date.equals(today);
    }
    public static String display(String value) { return value == null || value.trim().isEmpty() ? "—" : value; }
    public static String comparison(String actual, String forecast) {
        if (actual == null || forecast == null) return "";
        // Compare only like-for-like simple numbers, percentages or magnitude suffixes.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("^([+-]?[0-9]+(?:[.,][0-9]+)?)\\s*(%|K|M|B|T)?$");
        var a=p.matcher(actual.trim()); var f=p.matcher(forecast.trim());
        if (!a.matches() || !f.matches() || !java.util.Objects.equals(a.group(2),f.group(2))) return "Comparaison à interpréter";
        int c = new java.math.BigDecimal(a.group(1).replace(',','.')).compareTo(new java.math.BigDecimal(f.group(1).replace(',','.')));
        return c>0 ? "↑ Supérieur au prévu" : c<0 ? "↓ Inférieur au prévu" : "= Conforme au prévu";
    }
}
