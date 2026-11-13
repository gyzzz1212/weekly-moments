package cn.weeklymoments.guard;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Snapshots never overwrite the live file; retain seven saves and three upgrade checkpoints. */
final class LocalHistory {
    private final File directory;
    LocalHistory(File directory){this.directory=directory;}
    synchronized File save(byte[] bytes,String upgrade)throws IOException{
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建历史副本");
        String name=upgrade==null?"save-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".json":"upgrade-"+upgrade+".json";
        File out=new File(directory,name);if(upgrade!=null&&out.exists())return out;
        File temporary=new File(directory,name+".pending");
        try(FileOutputStream stream=new FileOutputStream(temporary)){stream.write(bytes);stream.getFD().sync();}
        try{Files.move(temporary.toPath(),out.toPath(),StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temporary.toPath(),out.toPath());}
        long newest=0;for(File f:list())if(!f.equals(out))newest=Math.max(newest,f.lastModified());
        out.setLastModified(Math.max(System.currentTimeMillis(),newest+1));
        trim("save-",7);trim("upgrade-",3);return out;
    }
    synchronized List<File> list(){
        File[] files=directory.listFiles(f->f.isFile()&&f.getName().endsWith(".json")&&(f.getName().startsWith("save-")||f.getName().startsWith("upgrade-")));
        List<File> result=new ArrayList<>();if(files!=null)Collections.addAll(result,files);
        result.sort((a,b)->{int order=Long.compare(b.lastModified(),a.lastModified());return order!=0?order:b.getName().compareTo(a.getName());});return result;
    }
    private void trim(String prefix,int count){int found=0;for(File f:list())if(f.getName().startsWith(prefix)&&++found>count)f.delete();}
}
