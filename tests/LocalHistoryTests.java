package cn.weeklymoments.guard;

import java.nio.file.*;
import java.io.File;
import java.util.*;

public final class LocalHistoryTests {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("focus-history-");
        try{
            LocalHistory history=new LocalHistory(directory.toFile());
            check(history.list().isEmpty(),"empty history");
            File checkpoint=history.save("original".getBytes("UTF-8"),"6");
            history.save("must not overwrite".getBytes("UTF-8"),"6");
            check(new String(Files.readAllBytes(checkpoint.toPath()),"UTF-8").equals("original"),"upgrade checkpoint immutable");
            for(int i=0;i<12;i++)history.save(("saved-"+i).getBytes("UTF-8"),null);
            List<File> files=history.list();check(files.size()==8,"seven saves plus checkpoint");
            check(new String(Files.readAllBytes(files.get(0).toPath()),"UTF-8").equals("saved-11"),"newest save retained");
            check(checkpoint.exists(),"ordinary saves do not rotate upgrade checkpoint");
            history.save("next".getBytes("UTF-8"),"7");history.save("next".getBytes("UTF-8"),"8");history.save("next".getBytes("UTF-8"),"9");
            check(history.list().size()==10&&!checkpoint.exists(),"three upgrade checkpoints retained");
            Path blocked=directory.resolve("blocked");Files.write(blocked,new byte[]{1});boolean rejected=false;
            try{new LocalHistory(blocked.toFile()).save(new byte[]{1},null);}catch(java.io.IOException expected){rejected=true;}
            check(rejected,"snapshot failure is reported before live data changes");
            System.out.println("PASS: actual history file writes, immutable upgrade checkpoint, newest retention, separate rotation and failure reporting");
        }finally{try(java.util.stream.Stream<Path> walk=Files.walk(directory)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception e){throw new RuntimeException(e);}});}}
    }
}
