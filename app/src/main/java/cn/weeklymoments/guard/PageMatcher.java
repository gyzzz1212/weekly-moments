package cn.weeklymoments.guard;

/** No text matching: a chat message saying "朋友圈" cannot trigger a block. */
public final class PageMatcher {
    public static final String WECHAT = "com.tencent.mm";
    private static final String SNS = "com.tencent.mm.plugin.sns.ui.";

    public static boolean isWechatPage(String name) {
        return name != null && name.startsWith("com.tencent.mm.") && !name.contains("$")
                && (name.endsWith("UI") || name.endsWith("Activity") || isMoments(name));
    }

    public static boolean isPersonal(String name) {
        return name != null && name.startsWith(SNS + "SnsUserUI");
    }

    public static boolean isMoments(String name) {
        return name != null && name.startsWith(SNS);
    }

    public static boolean shouldBlock(String pkg, String page, boolean all, boolean personalOrigin) {
        if (!WECHAT.equals(pkg) || !isMoments(page)) return false;
        return all || isPersonal(page) || personalOrigin;
    }

    private PageMatcher() {}
}
