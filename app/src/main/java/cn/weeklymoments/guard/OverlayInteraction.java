package cn.weeklymoments.guard;

import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Consumer;

/** Main-thread coordinator: release touch interception before launch or any persistence work. */
final class OverlayInteraction {
    static final long HANDOFF_MS=3000,FAILURE_RELEASE_MS=15000,GRACE_MS=15*60_000L;
    private final LongSupplier clock;private final Executor ui,disk;private final Runnable dismiss;private final Consumer<String> report;
    private long handoffUntil,graceUntil;private int generation;private boolean closed,awaitingNavigation;
    OverlayInteraction(LongSupplier clock,Executor ui,Executor disk,Runnable dismiss,Consumer<String> report){
        this.clock=clock;this.ui=ui;this.disk=disk;this.dismiss=dismiss;this.report=report;
    }
    boolean transitioning(){return !closed&&clock.getAsLong()<handoffUntil;}
    boolean lifePaused(){return !closed&&clock.getAsLong()<graceUntil;}
    boolean open(Runnable launch){
        if(closed||transitioning())return false;
        handoffUntil=clock.getAsLong()+HANDOFF_MS;awaitingNavigation=true;int token=++generation;dismiss.run();
        try{ui.execute(()->{if(closed||generation!=token)return;try{launch.run();}catch(RuntimeException e){launchFailed(token);}});}
        catch(RuntimeException e){launchFailed(token);}return true;
    }
    void confirmNavigation(){awaitingNavigation=false;}
    void checkLaunchTimeout(){if(!closed&&awaitingNavigation&&clock.getAsLong()>=handoffUntil){awaitingNavigation=false;launchFailed(generation);}}
    private void launchFailed(int token){if(closed||generation!=token)return;
        awaitingNavigation=false;handoffUntil=clock.getAsLong()+FAILURE_RELEASE_MS;
        report.accept("页面暂未打开，已先解除遮挡 15 秒；可以从桌面或通知进入自律空间。");
    }
    void emergency(Runnable persist){
        if(closed)return;graceUntil=clock.getAsLong()+GRACE_MS;handoffUntil=0;awaitingNavigation=false;++generation;dismiss.run();
        try{disk.execute(()->{try{persist.run();}catch(RuntimeException e){ui.execute(()->{if(!closed)report.accept("已立即暂停提醒；保存暂停记录失败，请检查手机剩余空间。");});}});}
        catch(RuntimeException e){report.accept("已立即暂停提醒；本次暂停记录尚未保存。");}
    }
    void close(){closed=true;++generation;handoffUntil=0;}
}
