package cn.weeklymoments.guard;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LifeStats {
    public static final class Day {
        public int plans, pending, effortMinutes;
        public double completedMinutes;
        public long coveredMinutes, availableMinutes;
        public int percent() { return effortMinutes == 0 ? -1 : (int)Math.round(completedMinutes * 100 / effortMinutes); }
        public int coverage() { return availableMinutes == 0 ? -1 : (int)Math.min(100, Math.round(coveredMinutes * 100.0 / availableMinutes)); }
    }
    public static Day day(LifeData data, LocalDate date, ZoneId zone) {
        Day result = new Day();
        List<LifeEngine.Span> planned = new ArrayList<>();
        for (LifeData.Plan plan : data.plans) if (plan.day.equals(date.toString())) {
            result.plans++; if (plan.reviewedAt == 0) result.pending++;
            planned.add(new LifeEngine.Span(plan.start, plan.end));
            for (LifeData.Task task : plan.tasks) {
                result.effortMinutes += task.minutes;
                if (plan.reviewedAt > 0) result.completedMinutes += task.minutes * Math.max(0, task.percent) / 100.0;
            }
        }
        for (LifeEngine.Span free : LifeEngine.free(data, date, zone)) {
            result.availableMinutes += (free.end - free.start) / 60_000;
            for (LifeEngine.Span plan : LifeEngine.merge(planned))
                result.coveredMinutes += Math.max(0, Math.min(free.end, plan.end) - Math.max(free.start, plan.start)) / 60_000;
        }
        if (data.closedAvailableMinutes.containsKey(date.toString())) {
            result.availableMinutes = data.closedAvailableMinutes.get(date.toString());
            result.coveredMinutes = data.closedCoveredMinutes.get(date.toString());
        }
        return result;
    }
    public static List<String> advice(LifeData data, LocalDate date, ZoneId zone) {
        Day day = day(data, date, zone);
        List<String> result = new ArrayList<>();
        if (day.plans == 0) result.add("今天还没有计划记录。下一次先安排 30 分钟，只放进一件能清楚判断是否完成的小事。");
        if (day.pending > 0) result.add("还有 " + day.pending + " 段没有反馈，先补记再解读完成率，避免把漏填当成任务失败。");
        if (day.percent() >= 0 && day.percent() < 60 && day.pending == 0)
            result.add("今天任务完成率偏低。明天可以缩短到 30 分钟，把任务拆到“读完 3 页／完成 2 题”这样的大小，并留出休息时间。");
        if (day.percent() >= 85 && day.pending == 0)
            result.add("今天的任务安排比较适合你。先保持相近的任务量，给休息和意外留一点余地，不必急着加量。");
        if (day.coverage() >= 0 && day.coverage() < 50)
            result.add("已安排时段占可用时段的 " + day.coverage() + "% 。可以检查循环时段是否过宽，或在课后先安排一段较短计划；未覆盖时间不代表你没有做事。");
        LifeData.Task lowest = null;
        for (LifeData.Plan plan : data.plans) if (plan.day.equals(date.toString()) && plan.reviewedAt > 0)
            for (LifeData.Task task : plan.tasks) if (task.percent < 60 && (lowest == null || task.minutes > lowest.minutes)) lowest = task;
        if (lowest != null) result.add("“" + lowest.name + "”完成了 " + lowest.percent + "% 。下次先写一个更小的交付结果，或把它放到精力更好的时段。");
        LifeData.Sleep sleep = data.sleep(date.toString(), false);
        if (sleep != null && sleep.wakeAt > 0) {
            long late = (sleep.wakeAt - (sleep.wakeTarget > 0 ? sleep.wakeTarget : LifeEngine.at(date, data.wakeMinute, zone))) / 60_000;
            if (late >= 20) result.add("今天起床确认比目标晚 " + late + " 分钟。检查目标是否适合目前作息；可以先把睡前准备提前一点，再观察几天。");
        }
        if (result.isEmpty()) result.add("记录还不多。继续用相近的计划长度积累几天，再根据具体哪类任务容易卡住调整安排。");
        return result;
    }
    public static String restInterval(LifeData data, LocalDate wakeDay) {
        LifeData.Sleep previous = data.sleep(wakeDay.minusDays(1).toString(), false), current = data.sleep(wakeDay.toString(), false);
        if (previous == null || current == null || previous.bedAt <= 0 || current.wakeAt <= previous.bedAt) return "未配对";
        long minutes = (current.wakeAt - previous.bedAt) / 60_000;
        if (minutes > 24 * 60) return "请核对记录";
        return String.format(Locale.CHINA, "%d小时%02d分", minutes / 60, minutes % 60);
    }
    private LifeStats() {}
}
