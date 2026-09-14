export function clean(value) {
  if (value === null || value === undefined || /^(null|none|n\/a)?$/i.test(String(value).trim())) return null;
  return String(value).trim();
}
export function normalize(row) {
  const r = Object.fromEntries(Object.entries(row).map(([k,v])=>[k.trim().toLowerCase(),v]));
  const country = {'United States':'US','Euro Area':'EU'}[r.country];
  if (!country || !clean(r.calendarid) || !clean(r.date) || !clean(r.event)) return null;
  const date = /(?:Z|[+-]\d\d:\d\d)$/i.test(r.date) ? r.date : r.date+'Z';
  const time = new Date(date);
  if (!Number.isFinite(time.getTime())) return null;
  return {id:String(r.calendarid),title:r.event,country,time:time.toISOString(),
    importance:Number(r.importance)||1,previous:clean(r.previous),forecast:clean(r.forecast),
    actual:clean(r.actual),revised:clean(r.revised),reference:clean(r.reference)||'',
    source:clean(r.source)||'Trading Economics',tentative:Number(r.datespan||0)!==0};
}
export function reminderDue(event, minutes, now) {
  const due = Date.parse(event.time)-minutes*60000;
  return event.importance===3 && !event.tentative && event.actual===null &&
    now>=due && now-due<60000 && now<Date.parse(event.time);
}
export function initialRelease(previous, next, now) {
  const age = now-Date.parse(next.time);
  return previous && previous.actual===null && next.actual!==null && next.importance===3 && age>=-60000 && age<86400000;
}
export function dateRange(now=new Date()) {
  const monday=new Date(Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),now.getUTCDate()));
  monday.setUTCDate(monday.getUTCDate()-((monday.getUTCDay()+6)%7));
  const start=new Date(monday),end=new Date(monday);
  // Margin covers local weeks across UTC offsets.
  start.setUTCDate(start.getUTCDate()-2);end.setUTCDate(end.getUTCDate()+9);
  return [start.toISOString().slice(0,10),end.toISOString().slice(0,10)];
}
