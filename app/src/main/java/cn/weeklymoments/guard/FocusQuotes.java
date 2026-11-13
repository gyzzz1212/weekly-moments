package cn.weeklymoments.guard;

import java.util.Random;

/** Original reminders, stored locally; no attribution to outside authors. */
public final class FocusQuotes {
    public static final class Entry {
        public final String words, action;
        private Entry(String words, String action) { this.words = words; this.action = action; }
    }

    private static final Entry[] ENTRIES = {
        new Entry("把注意力留给自己，\n让今天向前一点。", "选一件你在意的小事，先给它五分钟。"),
        new Entry("你想成为的样子，\n藏在今天的小行动里。", "不必一步到位，先迈出眼前这一步。"),
        new Entry("别人的生活很精彩，\n你的生活也值得用心。", "想一想：此刻，有什么是你想为自己做的？"),
        new Entry("目标不必很大，\n但可以从现在开始。", "把“以后再做”，换成一个今天能完成的小步骤。"),
        new Entry("把一小段时间，\n还给真正想做的事。", "读一页书、整理桌面，或写下一个念头。"),
        new Entry("不用赶上谁，\n慢慢走向自己的目标。", "按照自己的节奏，完成今天的一点点。"),
        new Entry("先照顾好此刻，\n再慢慢靠近远方。", "喝口水，松一松肩膀，再决定下一件事。"),
        new Entry("你可以重新选择，\n把这一刻交给自己。", "一次停顿，就是一次找回方向的机会。"),
        new Entry("让想做的事，\n在今天有一个开始。", "打开那本书、那份文档，或穿上运动鞋。"),
        new Entry("小小的坚持，\n也在悄悄积累力量。", "做完一个小步骤，就给自己一点肯定。"),
        new Entry("先走一步，\n方向会渐渐清楚。", "不用等准备完美，先完成最小的一件事。"),
        new Entry("你投入的目光，\n会慢慢长成你的生活。", "把接下来的几分钟，送给一件真正珍惜的事。"),
        new Entry("今天也可以，\n多为自己留一点空白。", "不必填满每一分钟，安静一会儿也很好。"),
        new Entry("把期待写下来，\n把行动变得小一点。", "写一句：今天，我想完成什么？"),
        new Entry("不是每个空隙，\n都需要被屏幕填满。", "抬头看看窗外，让目光和思绪都休息一下。"),
        new Entry("你认真过的今天，\n会成为明天的底气。", "选择一件做完后，会让你轻松一点的小事。"),
        new Entry("有自己的方向，\n就不必总看别人的进度。", "回到你的清单，挑一个现在能做的步骤。"),
        new Entry("把“我想”，\n轻轻变成“我开始”。", "只做五分钟也可以，开始本身就有意义。"),
        new Entry("把时间花在热爱里，\n也把耐心留给自己。", "给自己的兴趣留一个小位置，不必追求成果。"),
        new Entry("走得慢一点，\n也可以离目标近一点。", "不比较速度，只确认今天想走的方向。"),
        new Entry("此刻的选择，\n可以更靠近你的心意。", "问问自己：做什么，会让我今晚更安心？"),
        new Entry("你的注意力，\n值得被温柔地安放。", "回到呼吸，回到身体，再回到眼前的小事。"),
        new Entry("把生活的主角，\n慢慢换回自己。", "今天，给自己的计划一次优先的机会。"),
        new Entry("休息不必有理由，\n重新开始也不必着急。", "如果累了，就先好好休息，而不是继续刷下去。")
    };

    public static int size() { return ENTRIES.length; }
    public static Entry at(int index) { return ENTRIES[index]; }
    public static int nextIndex(int previous, Random random) {
        if (previous < 0 || previous >= ENTRIES.length) return random.nextInt(ENTRIES.length);
        int next = random.nextInt(ENTRIES.length - 1);
        return next >= previous ? next + 1 : next;
    }
    private FocusQuotes() {}
}
