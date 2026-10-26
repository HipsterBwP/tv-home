package io.github.hipsterbwp.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * League scoreboards from ESPN's public score feed (no account or key; ESPN does not document
 * it, so it may change). College leagues keep only games with a Top 25 team. Call
 * {@link #load} off the main thread.
 */
final class Scores {
    /** Heading, ESPN sport path, scoreboard query, and whether to keep only ranked matchups. */
    private static final Object[][] LEAGUES = {
            {"NCAA FOOTBALL", "football/college-football", "groups=80&limit=300", true},
            {"NFL", "football/nfl", "limit=100", false},
            {"NCAA BASKETBALL", "basketball/mens-college-basketball", "groups=50&limit=400", true},
    };

    /** Games further than this from now are ignored, so a league disappears between seasons. */
    private static final long WINDOW_MS = 14L * 24 * 60 * 60 * 1000;

    static final class Game {
        String homeName, awayName, homeScore, awayScore, status;
        int homeRank, awayRank;
        boolean live, upcoming, homeWon, awayWon;
        long start;
    }

    static final class League {
        final String heading;
        final List<Game> games = new ArrayList<>();

        League(String heading) {
            this.heading = heading;
        }
    }

    /** Leagues with at least one game near now; a league whose feed fails is left out. */
    static List<League> load() {
        List<League> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Object[] l : LEAGUES) {
            try {
                League league = league((String) l[0], (String) l[1], (String) l[2], (Boolean) l[3], now);
                if (!league.games.isEmpty()) out.add(league);
            } catch (Exception ignored) {
                // Feed unreachable or changed: leave this league out rather than show stale data.
            }
        }
        return out;
    }

    private static League league(String heading, String sport, String query, boolean rankedOnly, long now)
            throws Exception {
        String url = "https://site.api.espn.com/apis/site/v2/sports/" + sport + "/scoreboard?" + query;
        JSONArray events = new JSONObject(Net.get(url, 6 * 1024 * 1024)).optJSONArray("events");
        League league = new League(heading);
        for (int i = 0; events != null && i < events.length(); i++) {
            Game g = game(events.getJSONObject(i));
            if (g == null || Math.abs(g.start - now) > WINDOW_MS) continue;
            if (rankedOnly && g.homeRank == 0 && g.awayRank == 0) continue;
            league.games.add(g);
        }
        // Live games first, then the latest finals, then the soonest upcoming games.
        Collections.sort(league.games, new Comparator<Game>() {
            @Override
            public int compare(Game a, Game b) {
                int ga = group(a), gb = group(b);
                if (ga != gb) return ga - gb;
                return ga == 1 ? Long.compare(b.start, a.start) : Long.compare(a.start, b.start);
            }

            private int group(Game g) {
                return g.live ? 0 : g.upcoming ? 2 : 1;
            }
        });
        return league;
    }

    private static Game game(JSONObject event) throws Exception {
        JSONObject comp = event.getJSONArray("competitions").getJSONObject(0);
        JSONObject type = comp.getJSONObject("status").getJSONObject("type");
        Game g = new Game();
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
            int rank = rank(c);
            if ("home".equals(c.optString("homeAway"))) {
                g.homeName = name; g.homeScore = score; g.homeWon = won; g.homeRank = rank;
            } else {
                g.awayName = name; g.awayScore = score; g.awayWon = won; g.awayRank = rank;
            }
        }
        if (g.homeName == null || g.awayName == null) return null;
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

    /** Top 25 rank, or 0 when unranked (ESPN uses 99 for unranked). */
    private static int rank(JSONObject competitor) {
        JSONObject r = competitor.optJSONObject("curatedRank");
        int n = r == null ? 0 : r.optInt("current", 0);
        return n >= 1 && n <= 25 ? n : 0;
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
