package cn.weeklymoments.guard;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Scheduling independent of Android: no alarms, UI or private phone data. */
public final class LifeEngine {
    public enum Kind { NONE, WAKE, BED, PLAN, REVIEW, DAY_REVIEW, WEEK_PLAN, DAY_PLAN, WEEK_REVIEW, LEARN }
    public static final class Due {
        public final Kind kind;
        public final String key, day, planId;
        public Due(Kind kind, String key, String day, String planId) {
            this.kind = kind; this.key = key; this.day = day; this.planId = planId;
        }
        public String title() {
            switch (kind) {
                case WEEK_PLAN: return "新的一周，先写下想完成的事";
                case DAY_PLAN: return "参考周计划，安排今天的重点";
                case WEEK_REVIEW: return "为这一周写一份完成清单";
                case LEARN: return "先补完之前的 AI 知识卡片";
                case WAKE: return "记录今天的起床时间";
                case BED: return "到准备睡觉的时间了";
                case PLAN: return "给接下来的一段时间，定个小目标";
                case REVIEW: return "这段计划到点了，回顾一下吧";
                case DAY_REVIEW: return "把今天的努力，认真看一看";
                default: return "当前没有待办提醒";
            }
        }
    }
    public static final Due NONE = new Due(Kind.NONE, "", "", "");
    public static final class Span {
        public final long start, end;
        public Span(long start, long end) { this.start = start; this.end = end; }
        public boolean contains(long now) { return start <= now && now < end; }
    }

    public static long at(LocalDate date, int minute, ZoneId zone) {
        return date.atStartOfDay().plusMinutes(minute).atZone(zone).toInstant().toEpochMilli();
    }
    public static LocalDate date(long now, ZoneId zone) { return Instant.ofEpochMilli(now).atZone(zone).toLocalDate(); }
    public static LocalDate day(LifeData data, long now, ZoneId zone) {
        LocalDate today = date(now, zone);
        return now < at(today, data.wakeMinute, zone) ? today.minusDays(1) : today;
    }
    public static long bedAt(LifeData data, LocalDate day, ZoneId zone) {
        return at(data.bedMinute <= data.wakeMinute ? day.plusDays(1) : day, data.bedMinute, zone);
    }

    public static boolean courseOn(LifeData data, LifeData.Course course, LocalDate date) {
        if (course.skipDates.contains(date.toString())) return false;
        if (!course.date.isEmpty()) return course.date.equals(date.toString());
        if (course.day != date.getDayOfWeek().getValue()) return false;
        long week = 1;
        if (!data.termStart.isEmpty()) {
            LocalDate start = LocalDate.parse(data.termStart).with(DayOfWeek.MONDAY);
            long days = ChronoUnit.DAYS.between(start, date);
            if (days < 0) return false;
            week = days / 7 + 1;
        }
        return week >= course.firstWeek && week <= course.lastWeek
                && (course.parity == 0 || (week % 2 == (course.parity == 1 ? 1 : 0)));
    }

    public static List<Span> classes(LifeData data, LocalDate date, ZoneId zone) {
        List<Span> result = new ArrayList<>();
        for (LifeData.Course course : data.courses) {
            if (!courseOn(data, course, date)) continue;
            if (course.start < 0 || course.start >= 1440 || course.end < 0 || course.end >= 1440 || course.start == course.end) continue;
            result.add(new Span(at(date, course.start, zone),
                    at(course.end <= course.start ? date.plusDays(1) : date, course.end, zone)));
        }
        return merge(result);
    }

    public static boolean inClass(LifeData data, long now, ZoneId zone) {
        if (!data.coursesConfirmed) return false;
        LocalDate date = date(now, zone);
        for (int d = -1; d <= 0; d++) for (Span span : classes(data, date.plusDays(d), zone)) if (span.contains(now)) return true;
        return false;
    }

    public static List<Span> free(LifeData data, LocalDate day, ZoneId zone) {
        List<Span> windows = new ArrayList<>();
        long awake = at(day, data.wakeMinute, zone), bed = bedAt(data, day, zone);
        for (int offset = -1; offset <= 1; offset++) {
            LocalDate date = day.plusDays(offset);
            for (LifeData.Window window : data.windows) {
                if ((window.days & (1 << (date.getDayOfWeek().getValue() - 1))) == 0 || window.start == window.end) continue;
                long start = Math.max(awake, at(date, window.start, zone));
                long end = Math.min(bed, at(window.end <= window.start ? date.plusDays(1) : date, window.end, zone));
                if (start < end) windows.add(new Span(start, end));
            }
        }
        List<Span> free = merge(windows);
        for (int offset = -1; offset <= 1; offset++) for (Span course : classes(data, day.plusDays(offset), zone)) free = subtract(free, course);
        return free;
    }

    public static Span availableNow(LifeData data, long now, ZoneId zone) {
        if (!data.coursesConfirmed) return null;
        LocalDate day = day(data, now, zone);
        LifeData.Sleep sleep = data.sleep(day.toString(), false);
        if (sleep != null && sleep.bedAt > 0 && now >= sleep.bedAt) return null;
        for (Span span : free(data, day, zone)) if (span.contains(now)) return new Span(now, span.end);
        return null;
    }

    public static Due due(LifeData data, long now, ZoneId zone) {
        if ((!data.sleepEnabled && !data.planningEnabled && !RoutineEngine.enabled(data.routine)) || data.enabledAt <= 0 || now < data.enabledAt) return NONE;
        Due priority = RoutineEngine.priority(data,now,zone);
        if(priority.kind != Kind.NONE)return priority;
        if (inClass(data, now, zone)) return NONE;
        LocalDate date = day(data, now, zone);
        String key = date.toString();
        LifeData.Sleep sleep = data.sleep(key, false);
        if (sleep != null && sleep.bedAt > 0 && now >= sleep.bedAt) return NONE;
        boolean bedtime = now >= bedAt(data, date, zone);
        if (!bedtime && data.sleepEnabled && (sleep == null || sleep.wakeAt == 0))
            return new Due(Kind.WAKE, "wake:" + key, key, "");
        if (data.planningEnabled) {
            for (LifeData.Plan plan : data.plans) if (plan.reviewedAt == 0 && plan.end <= now)
                return new Due(Kind.REVIEW, "review:" + plan.id, plan.day, plan.id);
        }
        if (bedtime) {
            if ((data.planningEnabled || data.routine.dailyEnabled) && !data.acknowledgedDays.contains(key)
                    && bedAt(data, date, zone) >= data.enabledAt)
                return new Due(Kind.DAY_REVIEW, "summary:" + key, key, "");
            if (data.sleepEnabled && (sleep == null || sleep.bedAt == 0)
                    && bedAt(data, date, zone) >= data.enabledAt)
                return new Due(Kind.BED, "bed:" + key, key, "");
            return NONE;
        }
        Due daily=RoutineEngine.daily(data,now,zone);
        if(daily.kind!=Kind.NONE)return daily;
        if (!data.planningEnabled || !data.coursesConfirmed) return NONE;
        // A missed evening report remains available the next morning, without
        // manufacturing zero-work reports for every day the phone was off.
        String yesterday = date.minusDays(1).toString();
        if (!data.acknowledgedDays.contains(yesterday)) {
            for (LifeData.Plan plan : data.plans) if (plan.day.equals(yesterday))
                return new Due(Kind.DAY_REVIEW, "summary:" + yesterday, yesterday, "");
        }
        for (LifeData.Plan plan : data.plans) if (plan.reviewedAt == 0 && plan.end > now) return NONE;
        Span span = availableNow(data, now, zone);
        if (span != null && span.end - now >= 15 * 60_000L)
            return new Due(Kind.PLAN, "plan:" + key + ":" + span.end, key, "");
        return NONE;
    }

    public static long nextBoundary(LifeData data, long now, ZoneId zone) {
        long next = now + (due(data, now, zone).kind == Kind.NONE ? 24 * 60 : 15) * 60_000L;
        LocalDate day = date(now, zone);
        if(RoutineEngine.enabled(data.routine))next=Math.min(next,at(day.plusDays(1),0,zone));
        for (int i = -1; i <= 2; i++) {
            LocalDate date = day.plusDays(i);
            long wake = at(date, data.wakeMinute, zone), bed = bedAt(data, date, zone);
            if (wake > now) next = Math.min(next, wake);
            if (bed > now) next = Math.min(next, bed);
            for (Span span : free(data, date, zone)) {
                if (span.start > now) next = Math.min(next, span.start);
                if (span.end > now) next = Math.min(next, span.end);
            }
        }
        for (LifeData.Plan plan : data.plans) if (plan.reviewedAt == 0 && plan.end > now) next = Math.min(next, plan.end);
        if (data.graceUntil > now) next = Math.min(next, data.graceUntil);
        return Math.max(now + 1000, next);
    }

    public static List<Span> merge(List<Span> spans) {
        List<Span> sorted = new ArrayList<>(spans), result = new ArrayList<>();
        sorted.sort(Comparator.comparingLong(s -> s.start));
        for (Span span : sorted) {
            if (result.isEmpty() || result.get(result.size() - 1).end < span.start) result.add(span);
            else {
                Span previous = result.remove(result.size() - 1);
                result.add(new Span(previous.start, Math.max(previous.end, span.end)));
            }
        }
        return result;
    }
    private static List<Span> subtract(List<Span> spans, Span cut) {
        List<Span> result = new ArrayList<>();
        for (Span span : spans) {
            if (cut.end <= span.start || cut.start >= span.end) result.add(span);
            else {
                if (span.start < cut.start) result.add(new Span(span.start, cut.start));
                if (cut.end < span.end) result.add(new Span(cut.end, span.end));
            }
        }
        return result;
    }
    private LifeEngine() {}
}
