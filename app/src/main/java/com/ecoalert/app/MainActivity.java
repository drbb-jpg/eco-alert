package com.ecoalert.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.*;
import java.net.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(11,20,35), CARD=Color.rgb(21,34,53), INK=Color.rgb(236,243,251),
        MUTED=Color.rgb(166,185,209), MINT=Color.rgb(87,223,195), RED=Color.rgb(255,136,149);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<Event> events=new ArrayList<>();
    private SharedPreferences prefs;
    private LinearLayout root, content, list;
    private TextView status, next;
    private ZoneId zone;
    private boolean week=false, major=true, loading=false, active=false, settings=false, stale=true;
    private int country=0;
    private String lastUpdate="";
    private final Runnable tick=new Runnable() {
        public void run() {
            if (!active) return;
            updateNext();
            if (!demo() && !settings) refresh();
            handler.postDelayed(this,15000);
        }
    };
    private boolean demo() { return BuildConfig.API_BASE_URL.trim().isEmpty(); }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView label(String s,int size,int color) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0,dp(4),0,dp(4)); return t;
    }
    private GradientDrawable shape(int color) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(18)); return d;
    }
    private LinearLayout column() {
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private Button button(String s,Runnable action) {
        Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(INK);
        b.setOnClickListener(v->action.run()); return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); prefs=getSharedPreferences("eco",MODE_PRIVATE);
        zone=ZoneId.of(prefs.getString("zone","Africa/Casablanca"));
        if (saved!=null) { week=saved.getBoolean("week"); country=saved.getInt("country"); major=saved.getBoolean("major",true); }
        root=column(); root.setBackgroundColor(BG); root.setPadding(dp(18),dp(16),dp(18),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            if (Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(dp(18)+safe.left,dp(12)+safe.top,dp(18)+safe.right,dp(8)+safe.bottom);
            } else v.setPadding(dp(18),dp(12)+insets.getSystemWindowInsetTop(),dp(18),dp(8)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root); root.requestApplyInsets();
        if (demo()) makeDemo(); else readCache();
        showCalendar();
    }
    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b); b.putBoolean("week",week); b.putInt("country",country); b.putBoolean("major",major);
    }
    @Override protected void onResume() { super.onResume(); active=true; handler.post(tick); }
    @Override protected void onPause() { active=false; handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
    private void header() {
        root.removeAllViews();
        TextView title=label("éco alert",30,INK); title.setTypeface(null,Typeface.BOLD); root.addView(title);
        root.addView(label("Les rendez-vous qui comptent",14,MUTED));
        status=label("",12,MINT); root.addView(status); updateStatus();
        LinearLayout tabs=new LinearLayout(this);
        tabs.addView(button("Aujourd’hui",()->{ week=false; showCalendar(); }),new LinearLayout.LayoutParams(0,dp(52),1));
        tabs.addView(button("Semaine",()->{ week=true; showCalendar(); }),new LinearLayout.LayoutParams(0,dp(52),1));
        tabs.addView(button("Alertes",this::showSettings),new LinearLayout.LayoutParams(0,dp(52),1)); root.addView(tabs);
        ScrollView scroll=new ScrollView(this); content=column(); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void showCalendar() {
        settings=false; header();
        TextView heading=label(week?"Cette semaine":"Aujourd’hui",25,INK); heading.setTypeface(null,Typeface.BOLD); content.addView(heading);
        content.addView(label(LocalDate.now(zone).format(DateTimeFormatter.ofPattern("EEEE d MMMM",Locale.FRANCE))+" · "+zone.getId(),12,MUTED));
        next=label("",17,MINT); next.setPadding(dp(14),dp(12),dp(14),dp(12)); next.setBackground(shape(CARD)); content.addView(next);
        LinearLayout filters=new LinearLayout(this);
        filters.addView(button(new String[]{"Tous pays","États-Unis","Zone euro"}[country],()->{country=(country+1)%3;showCalendar();}),new LinearLayout.LayoutParams(0,dp(52),1));
        filters.addView(button(major?"Majeures":"Toutes importances",()->{major=!major;showCalendar();}),new LinearLayout.LayoutParams(0,dp(52),1));
        content.addView(filters);
        list=column(); content.addView(list); renderEvents(); updateNext();
        content.addView(label("Rouge : majeure · Orange : moyenne · Jaune : faible\nLes écarts numériques ne prédisent pas la direction de la Bourse.",12,MUTED));
        content.addView(button("Actualiser",()->{if(demo()){makeDemo();renderEvents();updateNext();}else refresh();}));
    }
    private boolean matches(Event e) {
        return (!major||e.importance==3) && (country==0||e.country.equals(country==1?"US":"EU"));
    }
    private void renderEvents() {
        if (settings || list==null) return;
        list.removeAllViews(); LocalDate today=LocalDate.now(zone); int count=0;
        for (Event e:events) {
            if (!e.inPeriod(today,zone,week)||!matches(e)) continue;
            count++;
            LinearLayout card=column(); card.setPadding(dp(16),dp(12),dp(16),dp(12)); card.setBackground(shape(CARD));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,dp(6),0,dp(6)); list.addView(card,params);
            String date=e.time.atZone(zone).format(DateTimeFormatter.ofPattern(week?"EEE d · HH:mm":"HH:mm",Locale.FRANCE));
            int impact=e.importance==3?RED:e.importance==2?Color.rgb(255,185,112):Color.rgb(238,213,117);
            card.addView(label(date+"  ·  "+(e.country.equals("US")?"États-Unis":"Zone euro")+"  ·  "+
                (e.importance==3?"● Majeure":e.importance==2?"● Moyenne":"● Faible")+(e.tentative?" · Horaire indicatif":""),12,impact));
            TextView name=label(e.title,19,INK); name.setTypeface(null,Typeface.BOLD); card.addView(name);
            if (!e.reference.trim().isEmpty()) card.addView(label(e.reference,12,MUTED));
            LinearLayout values=new LinearLayout(this);
            String[] labels={"Précédent","Prévu","Réel"}; String[] nums={e.previous,e.forecast,e.actual};
            for(int i=0;i<3;i++) {
                LinearLayout c=column(); c.addView(label(labels[i],12,MUTED));
                TextView value=label(Event.display(nums[i]),20,i==2?MINT:INK); value.setTypeface(null,Typeface.BOLD); c.addView(value);
                values.addView(c,new LinearLayout.LayoutParams(0,-2,1));
            }
            card.addView(values);
            if(e.revised!=null) card.addView(label("Précédent révisé : "+e.revised,12,MUTED));
            String comparison=Event.comparison(e.actual,e.forecast);
            if(!comparison.isEmpty()) card.addView(label(comparison,13,comparison.startsWith("↑")?Color.rgb(149,184,255):comparison.startsWith("↓")?Color.rgb(211,173,255):MUTED));
            if(e.actual==null) card.addView(label(e.time.isBefore(Instant.now())?"Résultat en attente":"À venir",12,MUTED));
            card.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(e.title)
                .setMessage("Publication : "+e.time.atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z"))+
                "\nSource : "+e.source+"\n\nPrécédent : "+Event.display(e.previous)+"\nPrévu (consensus) : "+Event.display(e.forecast)+
                "\nRéel : "+Event.display(e.actual)+"\n\n"+explain(e.title)+
                "\n\nUn résultat supérieur au consensus n’est pas automatiquement favorable aux actions.")
                .setPositiveButton("Fermer",null).show());
        }
        if(count==0) list.addView(label(loading?"Chargement…":stale&&!demo()?"Calendrier indisponible. Vérifiez la connexion.":"Aucune annonce pour ces filtres.",16,MUTED));
    }
    private String explain(String title) {
        String t=title.toLowerCase(Locale.ROOT);
        if(t.contains("inflation")||t.contains("cpi")||t.contains("pce")) return "Mesure l’évolution des prix. À lire avec le consensus, les composantes et les révisions.";
        if(t.contains("payroll")||t.contains("emploi")||t.contains("unemployment")) return "Indicateur du marché du travail. Lisez aussi les salaires et les révisions des mois précédents.";
        if(t.contains("rate")||t.contains("taux")) return "Décision de politique monétaire. Le communiqué et les perspectives peuvent compter autant que le taux annoncé.";
        if(t.contains("pmi")) return "Enquête d’activité. Le niveau, son évolution et les composantes complètent la comparaison au consensus.";
        return "Comparez le chiffre publié au consensus et au précédent. La réaction dépend aussi du contexte et des révisions.";
    }
    private void updateNext() {
        if(settings||next==null)return;
        for(Event e:events) if(matches(e)&&e.importance==3&&!e.tentative&&e.time.isAfter(Instant.now())
            &&e.inPeriod(LocalDate.now(zone),zone,true)) {
            long minutes=Duration.between(Instant.now(),e.time).toMinutes();
            next.setText("Prochaine majeure\n"+e.title+" · "+(minutes<1?"dans moins d’une minute":minutes<60?"dans "+minutes+" min":"dans "+(minutes/60)+" h "+minutes%60+" min"));return;
        }
        next.setText("Aucune prochaine annonce majeure cette semaine");
    }
    private void updateStatus() {
        if(status==null)return;
        status.setText(demo()?"DÉMONSTRATION · données fictives":(stale?"HORS LIGNE / DONNÉES ANCIENNES":"CONNECTÉ")+" · "+(lastUpdate.trim().isEmpty()?"en attente":lastUpdate));
        status.setTextColor(stale&&!demo()?RED:MINT);
    }
    private void showSettings() {
        settings=true; header(); content.addView(label("Vos alertes, à votre rythme",23,INK));
        content.addView(label("Les alertes concernent les annonces majeures des États-Unis et de la zone euro. Les filtres du calendrier ne modifient pas les alertes.",14,MUTED));
        boolean ready=EcoApplication.pushReady();
        if(!ready) content.addView(label("Alertes indisponibles dans cette version : configuration du service nécessaire.",14,RED));
        Switch before=new Switch(this); before.setText("Rappel avant l’annonce"); before.setTextColor(INK); before.setChecked(prefs.getBoolean("reminders",false)); before.setEnabled(ready); content.addView(before);
        before.setOnCheckedChangeListener((b,on)->setAlert("reminders",on));
        content.addView(button("Délai : "+prefs.getInt("minutes",15)+" minutes",()->new AlertDialog.Builder(this).setTitle("Rappeler avant")
            .setItems(new String[]{"5 minutes","15 minutes","30 minutes"},(d,n)->{prefs.edit().putInt("minutes",new int[]{5,15,30}[n]).apply();syncTopics();showSettings();}).show()));
        Switch after=new Switch(this); after.setText("Notification du résultat"); after.setTextColor(INK); after.setChecked(prefs.getBoolean("results",false)); after.setEnabled(ready); content.addView(after);
        after.setOnCheckedChangeListener((b,on)->setAlert("results",on));
        content.addView(button("Fuseau : "+zone.getId(),()->new AlertDialog.Builder(this).setTitle("Heure affichée")
            .setItems(new String[]{"Maroc","Téléphone","UTC"},(d,n)->{
                zone=n==0?ZoneId.of("Africa/Casablanca"):n==1?ZoneId.systemDefault():ZoneId.of("UTC");
                prefs.edit().putString("zone",zone.getId()).apply();showSettings();
            }).show()));
        content.addView(label("Les notifications nécessitent votre autorisation Android et une connexion. Un retard reste possible. Aucun compte personnel n’est nécessaire.",14,MUTED));
        content.addView(button("Confidentialité",()->new AlertDialog.Builder(this).setTitle("Confidentialité")
            .setMessage("Vos filtres et réglages restent sur votre téléphone. Si vous activez les alertes, Firebase utilise un identifiant technique pour les envoyer. Le serveur reçoit les requêtes du calendrier et peut voir votre adresse IP. Aucune clé de données financières n’est stockée dans l’application. Aucun outil publicitaire ou analytique n’est intégré.")
            .setPositiveButton("Fermer",null).show()));
    }
    private void setAlert(String key,boolean enabled) {
        prefs.edit().putBoolean(key,enabled).apply();
        if(enabled && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);
        else syncTopics();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==7) {
            if(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED) {
                prefs.edit().putBoolean("reminders",false).putBoolean("results",false).apply();
                Toast.makeText(this,"Notifications non autorisées",Toast.LENGTH_LONG).show();
            }
            syncTopics(); showSettings();
        }
    }
    private void syncTopics() {
        if(!EcoApplication.pushReady())return;
        FirebaseMessaging fm=FirebaseMessaging.getInstance();
        boolean before=prefs.getBoolean("reminders",false), after=prefs.getBoolean("results",false);
        fm.setAutoInitEnabled(before||after);
        // Do not generate a token for a user who has never opted in.
        if(!before&&!after&&!prefs.getBoolean("pushUsed",false))return;
        prefs.edit().putBoolean("pushUsed",true).apply();
        for(int m:new int[]{5,15,30}) {
            String topic="eco-before-"+m;
            var task=before&&m==prefs.getInt("minutes",15)?fm.subscribeToTopic(topic):fm.unsubscribeFromTopic(topic);
            task.addOnFailureListener(e->Toast.makeText(this,"Synchronisation des alertes échouée. Réessayez.",Toast.LENGTH_LONG).show());
        }
        var task=after?fm.subscribeToTopic("eco-results"):fm.unsubscribeFromTopic("eco-results");
        task.addOnSuccessListener(v->Toast.makeText(this,"Préférences d’alertes synchronisées",Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e->Toast.makeText(this,"Synchronisation des alertes échouée. Réessayez.",Toast.LENGTH_LONG).show());
    }
    private void refresh() {
        if(loading||demo())return; loading=true;
        io.execute(()->{
            HttpURLConnection c=null;
            try {
                URL url=new URL(BuildConfig.API_BASE_URL.replaceAll("/+$","")+"/events");
                if(!"https".equals(url.getProtocol()))throw new IOException("HTTPS requis");
                c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);
                if(c.getResponseCode()!=200)throw new IOException("Serveur indisponible");
                String body;
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>2000000)throw new IOException("Réponse trop grande");}
                    body=out.toString("UTF-8");
                }
                JSONObject json=new JSONObject(body); List<Event> parsed=parse(json.getJSONArray("events"));
                String updated=json.getString("updatedAt"); Instant instant=Instant.parse(updated);
                boolean old=json.optBoolean("stale",true)||Duration.between(instant,Instant.now()).getSeconds()>180;
                prefs.edit().putString("cache",body).apply();
                runOnUiThread(()->{if(isDestroyed())return;events.clear();events.addAll(parsed);stale=old;lastUpdate="mise à jour "+instant.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm:ss"));loading=false;updateStatus();renderEvents();updateNext();});
            } catch(Exception e) {runOnUiThread(()->{loading=false;stale=true;if(!isDestroyed()){updateStatus();renderEvents();}});}
            finally {if(c!=null)c.disconnect();}
        });
    }
    private static String optional(JSONObject o,String key) {
        String s=o.optString(key,"");return o.isNull(key)||s.trim().isEmpty()?null:s;
    }
    private List<Event> parse(JSONArray array)throws JSONException {
        List<Event> result=new ArrayList<>();
        for(int i=0;i<array.length();i++) {
            JSONObject e=array.getJSONObject(i);
            result.add(new Event(e.getString("id"),e.getString("title"),e.getString("country"),Instant.parse(e.getString("time")),
                e.getInt("importance"),optional(e,"previous"),optional(e,"forecast"),optional(e,"actual"),optional(e,"revised"),
                e.optString("reference",""),e.optString("source","Trading Economics"),e.optBoolean("tentative",false)));
        }
        result.sort(Comparator.comparing(e->e.time));return result;
    }
    private void readCache() {
        try { JSONObject c=new JSONObject(prefs.getString("cache","")); events.addAll(parse(c.getJSONArray("events")));lastUpdate="dernière réception "+c.getString("updatedAt"); }
        catch(Exception ignored){} stale=true;
    }
    private void makeDemo() {
        events.clear(); LocalDate today=LocalDate.now(zone);
        String[] names={"Inflation CPI · exemple","Ventes au détail · exemple","Décision de taux BCE · exemple","Emploi NFP · exemple","PMI manufacturier · exemple"};
        for(int i=0;i<5;i++) {
            LocalDate day=today.plusDays(i==0?0:i-1);
            events.add(new Event("demo-"+i,names[i],i==2?"EU":"US",day.atTime(i==0?9:14,30).atZone(zone).toInstant(),i==4?2:3,
                i==3?"180K":"3.0%",i==3?"170K":"3.1%",i==0?"3.2%":null,null,"Exemple fictif · aucun calendrier réel","Démonstration",false));
        }
        events.sort(Comparator.comparing(e->e.time));stale=false;
    }
}
