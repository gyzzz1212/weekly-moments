package cn.weeklymoments.guard;

public final class PolicyTests {
    private static int assertions;
    private static final long WALL = 1_800_000_000_000L, ELAPSED = 5_000_000L;
    private static final int BOOT = 42;
    private static void equal(long actual, long expected, String message) {
        assertions++;
        if (actual != expected) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static void yes(boolean value, String message) { equal(value ? 1 : 0, 1, message); }
    private static void no(boolean value, String message) { equal(value ? 1 : 0, 0, message); }

    public static void main(String[] args) {
        AccessPolicy fresh = new AccessPolicy(0, 0, -1, false);
        yes(fresh.canStart(WALL, ELAPSED, BOOT), "first allowance available");
        equal(fresh.remainingOpen(ELAPSED, BOOT), 0, "no automatic first allowance");
        AccessPolicy p = new AccessPolicy(WALL, ELAPSED, BOOT, false);
        equal(p.remainingOpen(ELAPSED, BOOT), 900000, "exactly fifteen minutes");
        equal(p.remainingOpen(ELAPSED + 899999, BOOT), 1, "last millisecond");
        equal(p.remainingOpen(ELAPSED + 900000, BOOT), 0, "expiry boundary");
        equal(p.remainingOpen(ELAPSED + 900001, BOOT), 0, "past expiry");
        no(p.canStart(WALL, ELAPSED + AccessPolicy.COOLDOWN_MS - 1, BOOT), "week not yet passed");
        yes(p.canStart(WALL, ELAPSED + AccessPolicy.COOLDOWN_MS, BOOT), "exactly seven days");
        equal(p.remainingCooldown(WALL + 365L * 86400000, ELAPSED + 1000, BOOT),
                AccessPolicy.COOLDOWN_MS - 1000, "forward wall clock does not unlock in same boot");
        equal(p.remainingCooldown(WALL - 100000, ELAPSED + 1000, BOOT),
                AccessPolicy.COOLDOWN_MS - 1000, "backward wall clock does not alter same-boot countdown");
        equal(p.remainingOpen(ELAPSED - 1, BOOT), 0, "invalid elapsed clock ends window");
        equal(p.remainingOpen(ELAPSED + 1000, BOOT + 1), 0, "reboot ends active window");
        equal(p.remainingCooldown(WALL + 1000, 100, BOOT + 1),
                AccessPolicy.COOLDOWN_MS - 1000, "reboot preserves cooldown via wall clock");
        equal(p.remainingCooldown(WALL - 1, 100, BOOT + 1), AccessPolicy.COOLDOWN_MS,
                "backward reboot clock cannot unlock");
        yes(p.canStart(WALL + AccessPolicy.COOLDOWN_MS, 100, BOOT + 1), "week can expire after reboot");
        AccessPolicy ended = new AccessPolicy(WALL, ELAPSED, BOOT, true);
        equal(ended.remainingOpen(ELAPSED + 1000, BOOT), 0, "early ending cannot resume");
        no(ended.canStart(WALL + 1000, ELAPSED + 1000, BOOT), "early ending does not refund quota");
        equal(new AccessPolicy(WALL, ELAPSED, -1, false).remainingOpen(ELAPSED, -1), 0,
                "unknown boot fails closed");

        String sns = "com.tencent.mm.plugin.sns.ui.";
        yes(PageMatcher.shouldBlock(PageMatcher.WECHAT, sns + "SnsUserUI", false, false), "personal page blocked");
        yes(PageMatcher.shouldBlock(PageMatcher.WECHAT, sns + "SnsUserUIWithPost", false, false), "personal variant blocked");
        no(PageMatcher.shouldBlock(PageMatcher.WECHAT, sns + "SnsTimeLineUI", false, false), "feed allowed in personal mode");
        yes(PageMatcher.shouldBlock(PageMatcher.WECHAT, sns + "SnsTimeLineUI", true, false), "feed blocked in all mode");
        yes(PageMatcher.shouldBlock(PageMatcher.WECHAT, sns + "SnsGalleryUI", false, true), "personal nested photo blocked");
        no(PageMatcher.shouldBlock(PageMatcher.WECHAT, sns + "SnsGalleryUI", false, false), "unrelated photo allowed in personal mode");
        no(PageMatcher.shouldBlock(PageMatcher.WECHAT, "com.tencent.mm.ui.LauncherUI", true, true), "launcher never blocked");
        no(PageMatcher.shouldBlock(PageMatcher.WECHAT, "com.tencent.mm.ui.chatting.ChattingUI", true, true), "chat never blocked");
        no(PageMatcher.shouldBlock(PageMatcher.WECHAT, "com.tencent.mm.plugin.profile.ui.ContactInfoUI", true, true), "contact info allowed");
        no(PageMatcher.shouldBlock("cn.weeklymoments.guard", sns + "SnsUserUI", true, true), "other app never blocked");
        no(PageMatcher.shouldBlock(PageMatcher.WECHAT, "朋友圈", true, true), "chat text cannot trigger match");
        no(PageMatcher.shouldBlock(PageMatcher.WECHAT, null, true, true), "null safe");
        no(PageMatcher.isWechatPage("android.app.Dialog"), "system dialog does not replace activity");
        no(PageMatcher.isWechatPage("com.tencent.mm.ui.widget.MMNeat7extView"), "widget does not replace activity");
        yes(PageMatcher.isWechatPage("com.tencent.mm.ui.LauncherUI"), "launcher state tracked");
        java.util.Random random = new java.util.Random(20261026);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int previous = -1;
        for (int i = 0; i < 10000; i++) {
            int next = FocusQuotes.nextIndex(previous, random);
            if (next < 0 || next >= FocusQuotes.size() || next == previous)
                throw new AssertionError("quote invalid or immediately repeated");
            seen.add(next); previous = next;
        }
        equal(seen.size(), FocusQuotes.size(), "all reminders reachable across 10000 choices");
        java.util.Set<String> words = new java.util.HashSet<>();
        for (int i = 0; i < FocusQuotes.size(); i++) {
            FocusQuotes.Entry entry = FocusQuotes.at(i);
            if (entry.words.trim().isEmpty() || entry.action.trim().isEmpty() || !words.add(entry.words))
                throw new AssertionError("empty or duplicate reminder");
        }
        yes(FocusQuotes.nextIndex(Integer.MAX_VALUE, random) < FocusQuotes.size(), "invalid old quote index recovers");
        System.out.println("PASS: " + assertions + " timing/page/quote assertions; 10000 quote transitions without adjacent repeats");
    }
}
