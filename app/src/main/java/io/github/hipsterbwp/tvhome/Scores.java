package io.github.hipsterbwp.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Scores for the followed teams from ESPN's public score feed (no account or key; ESPN does not
 * document it, so it may change). Call {@link #load} off the main thread.
 */
final class Scores {
    /** A followed team: heading, ESPN sport path and team id. */
    static final String[][] TEAMS = {
            {"WILDCATS FOOTBALL", "football/college-football", "96"},
            {"WILDCATS BASKETBALL", "basketball/mens-college-basketball", "96"},
            {"STEELERS", "football/nfl", "pit"},
    };

    /** How far from a game a team stays on screen; outside this, its season is treated as over. */
    private static final long WINDOW_MS = 14L * 24 * 60 * 60 * 1000;
    /** A final score stays up this long before the next game takes its place. */
    private static final long FINAL_MS = 36L * 60 * 60 * 1000;

    static final class Game {
        String heading, homeName, awayName, homeScore, awayScore, status;
        boolean live, upcoming, homeWon, awayWon;
        long start;
    }

    /** One entry per team that is in season; nothing for teams between seasons or on errors. */
    static List<Game> load() {
        List<Game> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String[] t : TEAMS) {
            try {
                Game g = pick(t, now);
                if (g != null) out.add(g);
            } catch (Exception ignored) {
                // Feed unreachable or changed: leave this team out rather than show stale data.
            }
        }
        return out;
    }

    private static Game pick(String[] team, long now) throws Exception {
        String base = "https://site.api.espn.com/apis/site/v2/sports/" + team[1] + "/teams/" + team[2] + "/schedule";
        List<JSONObject> events = new ArrayList<>();
        for (String url : new String[] {base, base + "?seasontype=3"}) {
            JSONArray arr = new JSONObject(Net.get(url)).optJSONArray("events");
            for (int i = 0; arr != null && i < arr.length(); i++) events.add(arr.getJSONObject(i));
        }

        JSONObject live = null, last = null, next = null;
        long lastT = Long.MIN_VALUE, nextT = Long.MAX_VALUE;
        for (JSONObject e : events) {
            JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
            String state = comp.getJSONObject("status").getJSONObject("type").optString("state");
            long t = time(e.optString("date"));
            if ("in".equals(state)) live = e;
            else if ("post".equals(state) && t > lastT) { last = e; lastT = t; }
            else if ("pre".equals(state) && t > now - 6 * 3600_000L && t < nextT) { next = e; nextT = t; }
        }

        if (live != null) return game(team[0], live, refresh(team, live));
        boolean recentFinal = last != null && now - lastT < FINAL_MS;
        if (recentFinal) return game(team[0], last, null);
        if (next != null && nextT - now < WINDOW_MS) return game(team[0], next, null);
        if (last != null && now - lastT < WINDOW_MS) return game(team[0], last, null);
        return null;
    }

    /** Live games get the summary feed, which updates faster than the schedule. */
    private static JSONObject refresh(String[] team, JSONObject event) {
        try {
            String url = "https://site.api.espn.com/apis/site/v2/sports/" + team[1] + "/summary?event=" + event.getString("id");
            return new JSONObject(Net.get(url)).getJSONObject("header").getJSONArray("competitions").getJSONObject(0);
        } catch (Exception e) {
            return null;
        }
    }

    private static Game game(String heading, JSONObject event, JSONObject fresh) throws Exception {
        JSONObject comp = fresh != null ? fresh : event.getJSONArray("competitions").getJSONObject(0);
        JSONObject type = comp.getJSONObject("status").getJSONObject("type");
        Game g = new Game();
        g.heading = heading;
        g.start = time(event.optString("date"));
        String state = type.optString("state");
        g.live = "in".equals(state);
        g.upcoming = "pre".equals(state);
        JSONArray cs = comp.getJSONArray("competitors");
        for (int i = 0; i < cs.length(); i++) {
            JSONObject c = cs.getJSONObject(i);
            JSONObject team = c.getJSONObject("team");
            String name = team.optString("shortDisplayName", team.optString("location", team.optString("displayName")));
            String score = score(c);
            boolean won = c.optBoolean("winner", false);
            if ("home".equals(c.optString("homeAway"))) {
                g.homeName = name; g.homeScore = score; g.homeWon = won;
            } else {
                g.awayName = name; g.awayScore = score; g.awayWon = won;
            }
        }
        if (g.upcoming) {
            String when = new SimpleDateFormat("EEE h:mm a", Locale.US).format(new Date(g.start));
            String tv = broadcast(comp);
            g.status = tv.isEmpty() ? when : when + " · " + tv;
            g.homeScore = g.awayScore = "";
        } else {
            g.status = type.optString("shortDetail", g.live ? "Live" : "Final");
        }
        return g;
    }

    private static String score(JSONObject competitor) {
        Object s = competitor.opt("score");
        if (s instanceof JSONObject) return ((JSONObject) s).optString("displayValue", "");
        return s == null ? "" : String.valueOf(s);
    }

    private static String broadcast(JSONObject comp) {
        JSONArray b = comp.optJSONArray("broadcasts");
        if (b == null || b.length() == 0) return "";
        JSONObject first = b.optJSONObject(0);
        if (first == null) return "";
        JSONObject media = first.optJSONObject("media");
        if (media != null) return media.optString("shortName", "");
        JSONArray names = first.optJSONArray("names");
        return names != null && names.length() > 0 ? names.optString(0) : "";
    }

    /** ESPN dates look like 2026-10-25T23:00Z, sometimes with seconds. */
    static long time(String iso) {
        if (iso == null || iso.length() < 16) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso.substring(0, 16)).getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
