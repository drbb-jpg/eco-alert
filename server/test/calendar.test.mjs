import test from 'node:test';
import assert from 'node:assert/strict';
import {normalize,reminderDue,initialRelease,dateRange} from '../src/calendar.mjs';
const raw={CalendarId:1,Country:'United States',Event:'CPI',Date:'2026-09-15T12:30:00',Importance:3,Previous:'0',Forecast:'3.1%',Actual:null};
test('UTC normalization, zero retained, consensus not replaced by model forecast',()=>{
 const e=normalize({...raw,TEForecast:'8%'});
 assert.equal(e.time,'2026-09-15T12:30:00.000Z');assert.equal(e.previous,'0');assert.equal(e.forecast,'3.1%');
 assert.equal(normalize({...raw,Forecast:null,TEForecast:'8%'}).forecast,null);
 assert.equal(normalize({...raw,Country:'Unknown'}),null);
});
test('stream keys and string nulls',()=>{
 const e=normalize({calendarId:2,country:'Euro Area',event:'CPI',date:'2026-09-15T12:30:00',importance:'3','forecast ':'null',actual:'0'});
 assert.equal(e.forecast,null);assert.equal(e.actual,'0');
});
test('reminder only in due window and never after release or tentative time',()=>{
 const e=normalize(raw), due=Date.parse(e.time)-15*60000;
 assert.equal(reminderDue(e,15,due-1),false);assert.equal(reminderDue(e,15,due),true);
 assert.equal(reminderDue(e,15,due+60000),false);
 assert.equal(reminderDue({...e,tentative:true},15,due),false);
 assert.equal(reminderDue({...e,actual:'0'},15,due),false);
});
test('only initial release sends a result; revisions and historic startup do not',()=>{
 const e=normalize(raw), next={...e,actual:'0'},now=Date.parse(e.time);
 assert.equal(Boolean(initialRelease(undefined,next,now)),false);
 assert.equal(Boolean(initialRelease(e,next,now)),true);
 assert.equal(Boolean(initialRelease(next,{...next,actual:'1'},now)),false);
 assert.equal(Boolean(initialRelease(e,next,now+86400001)),false);
});
test('range covers the Monday-Sunday week at UTC boundaries',()=>{
 assert.deepEqual(dateRange(new Date('2026-09-20T23:59:00Z')),['2026-09-12','2026-09-23']);
});
