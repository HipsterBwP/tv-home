package io.github.hipsterbwp.tvhome;

/** Prints what the scores widget would show right now; run by the workflow's scores check. */
public class ScoresProbe {
    public static void main(String[] args) {
        java.util.List<Scores.Game> games = Scores.load();
        for (Scores.Game g : games) {
            System.out.println(g.heading + " | " + g.awayName + " " + g.awayScore + (g.awayWon ? " (W)" : "")
                    + " at " + g.homeName + " " + g.homeScore + (g.homeWon ? " (W)" : "")
                    + " | " + g.status + (g.live ? " | LIVE" : g.upcoming ? " | upcoming" : " | final"));
        }
        System.out.println(games.size() + " of " + Scores.TEAMS.length + " teams in season");
    }
}
