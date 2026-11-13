package cn.weeklymoments.guard;

import java.util.*;

/** Only records explicitly entered or checked inside this app. */
public final class RoutineData {
    public boolean weeklyEnabled, dailyEnabled, knowledgeEnabled, soundEnabled=true;
    public final List<Week> weeks=new ArrayList<>();
    public final List<Day> days=new ArrayList<>();
    public final List<LearningPeriod> learningPeriods=new ArrayList<>();
    public final Set<String> readCards=new HashSet<>();
    public static final class Item { public String name=""; public boolean done; }
    public static final class Habit {
        public String name="", rule="";
        // 0: weekly minimum count; 1: daily maximum minutes; 2: weekly maximum count.
        public int type, goal=1, allowedDays=127;
        public final Map<String,Integer> actual=new HashMap<>();
    }
    public static final class Week {
        public String key="", reflection="";
        public long createdAt, reviewedAt;
        public final List<Item> items=new ArrayList<>();
        public final List<Habit> habits=new ArrayList<>();
    }
    public static final class Day {
        public String key="";
        public long createdAt;
        public final List<Item> items=new ArrayList<>();
    }
    public static final class LearningPeriod { public String start="", end=""; }
    public Week week(String key) { for(Week w:weeks)if(w.key.equals(key))return w;return null; }
    public Day day(String key) { for(Day d:days)if(d.key.equals(key))return d;return null; }
}
