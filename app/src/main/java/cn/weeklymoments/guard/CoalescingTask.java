package cn.weeklymoments.guard;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** At most one worker drain; a burst requests one latest-state rerun, not a queue per event. */
final class CoalescingTask {
    private final Executor executor;private final Runnable action;private final Consumer<RuntimeException> onError;
    private boolean scheduled,dirty;private final List<Runnable> completions=new ArrayList<>();
    CoalescingTask(Executor executor,Runnable action,Consumer<RuntimeException> onError){this.executor=executor;this.action=action;this.onError=onError;}
    void request(){request(null);}
    void request(Runnable completion){
        synchronized(this){dirty=true;if(completion!=null)completions.add(completion);if(scheduled)return;scheduled=true;}
        try{executor.execute(this::drain);}catch(RuntimeException e){List<Runnable> callbacks;
            synchronized(this){scheduled=false;dirty=false;callbacks=new ArrayList<>(completions);completions.clear();}
            try{onError.accept(e);}finally{finish(callbacks);}
        }
    }
    private void drain(){
        while(true){List<Runnable> callbacks;
            synchronized(this){dirty=false;callbacks=new ArrayList<>(completions);completions.clear();}
            try{action.run();}catch(RuntimeException e){onError.accept(e);}finally{finish(callbacks);}
            synchronized(this){if(!dirty){scheduled=false;return;}}
        }
    }
    private static void finish(List<Runnable> callbacks){for(Runnable callback:callbacks)try{callback.run();}catch(RuntimeException ignored){}}
}
