package cn.weeklymoments.guard;

import java.util.*;
import java.util.concurrent.Executor;

/** Runs the production interaction coordinator against delayed UI/disk queues and launch failures. */
public final class ResponsivenessTests {
    private static int checks;
    private static void yes(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();public void execute(Runnable action){tasks.add(action);}
        void drain(){int count=0;while(!tasks.isEmpty()){if(++count>100000)throw new AssertionError("Unbounded worker loop");tasks.remove().run();}}
    }
    public static void main(String[] args){
        long[] time={1000};Queue ui=new Queue(),disk=new Queue();List<String> events=new ArrayList<>();
        OverlayInteraction tap=new OverlayInteraction(()->time[0],ui,disk,()->events.add("dismiss"),events::add);
        yes(tap.open(()->events.add("launch")),"first click accepted");
        yes(events.equals(Arrays.asList("dismiss")),"touch overlay released before activity launch");
        for(int i=0;i<100;i++)yes(!tap.open(()->events.add("duplicate")),"rapid duplicate click rejected");
        yes(ui.tasks.size()==1,"one launch is queued");ui.drain();
        yes(events.equals(Arrays.asList("dismiss","launch")),"launch runs after dismissal");
        yes(tap.transitioning(),"reminder cannot reappear over the launching page");
        time[0]+=OverlayInteraction.HANDOFF_MS;yes(!tap.transitioning(),"transition releases exactly at deadline");
        tap.open(()->{throw new IllegalStateException("OS refused activity");});ui.drain();
        yes(tap.transitioning(),"launch exception leaves the screen available briefly");
        yes(events.get(events.size()-1).contains("15 秒"),"failed launch gives actionable feedback");
        time[0]+=OverlayInteraction.FAILURE_RELEASE_MS;yes(!tap.transitioning(),"launch failure does not permanently disable rules");
        tap.open(()->events.add("cancelled-launch"));
        tap.emergency(()->{events.add("persist");throw new IllegalStateException("disk full");});
        yes(tap.lifePaused(),"emergency grace applies immediately");
        yes(!events.contains("persist")&&disk.tasks.size()==1,"disk work cannot delay release");
        ui.drain();yes(!events.contains("cancelled-launch"),"emergency cancels stale queued navigation");
        disk.drain();ui.drain();yes(tap.lifePaused(),"save failure cannot revoke the in-memory pause");
        yes(events.get(events.size()-1).contains("保存暂停记录失败"),"save failure is visible");
        time[0]+=OverlayInteraction.GRACE_MS;yes(!tap.lifePaused(),"emergency pause retains original fifteen-minute duration");
        tap.open(()->events.add("destroyed-launch"));tap.close();ui.drain();
        yes(!events.contains("destroyed-launch"),"destroyed service cannot launch a stale page");
        Queue silentUi=new Queue();OverlayInteraction silent=new OverlayInteraction(()->time[0],silentUi,disk,()->events.add("dismiss-silent"),events::add);
        silent.open(()->{});silentUi.drain();time[0]+=OverlayInteraction.HANDOFF_MS;silent.checkLaunchTimeout();
        yes(silent.transitioning(),"silently refused navigation releases the screen instead of trapping taps again");
        time[0]+=OverlayInteraction.FAILURE_RELEASE_MS;silent.open(()->{});silentUi.drain();silent.confirmNavigation();
        time[0]+=OverlayInteraction.HANDOFF_MS;int messages=events.size();silent.checkLaunchTimeout();
        yes(!silent.transitioning()&&events.size()==messages,"confirmed navigation has no false failure timeout");
        Executor reject=action->{throw new java.util.concurrent.RejectedExecutionException();};
        OverlayInteraction rejected=new OverlayInteraction(()->time[0],ui,reject,()->events.add("released"),events::add);
        rejected.emergency(()->events.add("never saved"));yes(rejected.lifePaused(),"worker rejection still allows emergency use");
        yes(events.get(events.size()-2).equals("released"),"worker rejection releases overlay first");

        Queue work=new Queue();int[] runs={0},completed={0},errors={0};
        CoalescingTask task=new CoalescingTask(work,()->runs[0]++,error->errors[0]++);
        for(int i=0;i<10000;i++)task.request(()->completed[0]++);
        yes(work.tasks.size()==1,"ten thousand refreshes share one queued job");work.drain();
        yes(runs[0]==1&&completed[0]==10000,"one latest-state refresh finishes every completion callback");
        final CoalescingTask[] latest={null};int[] latestRuns={0};
        latest[0]=new CoalescingTask(work,()->{latestRuns[0]++;if(latestRuns[0]==1)for(int i=0;i<10000;i++)latest[0].request();},error->errors[0]++);
        latest[0].request();work.drain();yes(latestRuns[0]==2,"changes arriving during work trigger one latest-state rerun");
        CoalescingTask failing=new CoalescingTask(work,()->{throw new IllegalStateException();},error->errors[0]++);
        failing.request(()->completed[0]++);work.drain();failing.request();work.drain();
        yes(errors[0]==2&&completed[0]==10001,"worker error finishes receiver callbacks and does not wedge the queue");

        int standingDraws=0,portraitDraws=0,blinks=0;
        for(long age=0;age<60000;){standingDraws++;if(CompanionFrames.standing(age,0)==1)blinks++;
            long delay=CompanionFrames.standingDelay(age,0);yes(delay>0,"standing redraw always advances time");age+=delay;}
        for(long age=0;age<60000;){portraitDraws++;long delay=CompanionFrames.portraitDelay(age);if(delay<=0)throw new AssertionError("Portrait redraw spin");age+=delay;}
        yes(standingDraws<30&&blinks>0,"standing character sleeps between blinks while retaining animation");
        yes(portraitDraws<300,"portrait does not continuously repaint twenty times per second");
        yes(CompanionFrames.standing(0,900)==2&&CompanionFrames.standing(240,660)==3,"tap hand frames still alternate");
        yes(CompanionFrames.standingDelay(0,100)==100,"tap response cannot outlive its remaining duration");
        yes(CompanionFrames.blink(5950)&&!CompanionFrames.blink(6140),"blink boundaries maintained");
        yes(CompanionFrames.smile(11000)>.99f&&CompanionFrames.smile(12000)==0,"expression settles back to neutral");
        System.out.println("PASS: "+checks+" responsiveness assertions; rapid taps, dismiss-before-launch/save, failure recovery, receiver completions; 60s idle draw simulation: standing="+standingDraws+", portrait="+portraitDraws);
    }
}
