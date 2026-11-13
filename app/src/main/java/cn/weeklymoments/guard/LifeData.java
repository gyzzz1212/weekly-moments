package cn.weeklymoments.guard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/** Local records. Dates use ISO local dates, instants use epoch milliseconds. */
public final class LifeData {
    public RoutineData routine = new RoutineData();
    public boolean sleepEnabled, planningEnabled, coursesConfirmed;
    public int wakeMinute = 7 * 60, bedMinute = 23 * 60, forceMode = 2, defaultMinutes = 30;
    public long enabledAt, graceUntil;
    public String termStart = "", imageUri = "", periodText = "";
    public final List<Window> windows = new ArrayList<>();
    public final List<Course> courses = new ArrayList<>();
    public final List<Plan> plans = new ArrayList<>();
    public final List<Sleep> sleeps = new ArrayList<>();
    public final Set<String> acknowledgedDays = new HashSet<>();
    public final List<Long> emergencyExits = new ArrayList<>();
    public final Map<String, Long> closedAvailableMinutes = new HashMap<>(), closedCoveredMinutes = new HashMap<>();

    public static final class Window {
        public int days = 127, start = 8 * 60, end = 22 * 60;
    }
    public static final class Course {
        public String id = "", name = "", date = "";
        public int day = 1, start, end, firstWeek = 1, lastWeek = 52, parity;
        public final Set<String> skipDates = new HashSet<>();
    }
    public static final class Task {
        public String name = "";
        public int minutes, percent = -1;
    }
    public static final class Plan {
        public String id = "", day = "", reflection = "";
        public long start, end, reviewedAt;
        public boolean adjusted;
        public final List<Task> tasks = new ArrayList<>();
    }
    public static final class Sleep {
        public String day = "";
        public long wakeAt, bedAt;
        public long wakeTarget, bedTarget;
        public boolean corrected;
    }

    public Sleep sleep(String day, boolean create) {
        for (Sleep record : sleeps) if (record.day.equals(day)) return record;
        if (!create) return null;
        Sleep record = new Sleep(); record.day = day; sleeps.add(record); return record;
    }
    public Plan plan(String id) {
        for (Plan record : plans) if (record.id.equals(id)) return record;
        return null;
    }
}
