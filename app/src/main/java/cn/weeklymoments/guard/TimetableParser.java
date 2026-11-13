package cn.weeklymoments.guard;

import java.util.*;
import java.util.regex.*;

/** Conservative proposals only. A human must confirm all imported courses. */
public final class TimetableParser {
    public static final class Word {
        public final String text; public final int left,top,right,bottom;
        public Word(String text,int left,int top,int right,int bottom){this.text=text;this.left=left;this.top=top;this.right=right;this.bottom=bottom;}
        public int x(){return(left+right)/2;} public int y(){return(top+bottom)/2;}
    }
    public static final class Period {
        public final int number,start,end;
        public Period(int number,int start,int end){this.number=number;this.start=start;this.end=end;}
    }
    private static final Pattern TIME=Pattern.compile("(\\d{1,2})[:：](\\d{2})\\s*[-~—–至一]\\s*(\\d{1,2})[:：](\\d{2})");
    private static final Pattern DAY=Pattern.compile("(?:星期|周|礼拜)([一二三四五六日天1-7])");
    private static int day(String s){Matcher m=DAY.matcher(s.replace(" ",""));if(!m.find())return 0;String c=m.group(1);int i="一二三四五六日".indexOf(c);if(i>=0)return i+1;if(c.equals("天"))return 7;return Integer.parseInt(c);}
    public static List<Period> periods(String text){
        List<Period> result=new ArrayList<>();for(String line:text.split("\\n")){Matcher m=TIME.matcher(line);if(!m.find())continue;
            String before=line.substring(0,m.start()).replaceAll("[^0-9]","");if(before.isEmpty())continue;
            if(!validTime(m))continue;
            int n=Integer.parseInt(before),a=Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2)),b=Integer.parseInt(m.group(3))*60+Integer.parseInt(m.group(4));
            if(n>0&&n<40&&a<1440&&b<1440&&a!=b)result.add(new Period(n,a,b));}return result;
    }
    public static List<LifeData.Course> proposals(String text,List<Word> words,List<Period> supplied){
        List<LifeData.Course> result=new ArrayList<>();
        for(String line:text.split("\\n")){int currentDay=day(line);Matcher m=TIME.matcher(line);
            if(currentDay==0||!m.find()||!validTime(m))continue;int a=Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2)),b=Integer.parseInt(m.group(3))*60+Integer.parseInt(m.group(4));
            String name=line.substring(m.end()).trim();if(a>=1440||b>=1440||a==b||name.isEmpty())continue;
            result.add(course(currentDay,a,b,name));}
        if(!result.isEmpty())return result;
        List<List<Word>> lines=lines(words);Map<Integer,Word> headers=new TreeMap<>();int headerY=Integer.MAX_VALUE;
        for(List<Word> line:lines){Map<Integer,Word> found=new TreeMap<>();for(int i=0;i<line.size();i++){
            String combined="";for(int j=i;j<Math.min(line.size(),i+3);j++){combined+=line.get(j).text;int d=day(combined);
                if(d>0){Word first=line.get(i),last=line.get(j);found.put(d,new Word(combined,first.left,first.top,last.right,last.bottom));break;}}}
            if(found.size()>=3&&line.get(0).y()<headerY){headers=found;headerY=line.get(0).y();}}
        if(headers.size()<3)return result;
        final int firstX=headers.values().stream().mapToInt(Word::x).min().orElse(0);
        Map<Integer,Period> rowPeriods=new TreeMap<>();Map<Integer,Integer> rowY=new TreeMap<>();
        for(List<Word> line:lines){StringBuilder left=new StringBuilder();for(Word w:line)if(w.x()<firstX-20)left.append(w.text);
            Matcher tm=TIME.matcher(left);int y=line.get(0).y();if(y<=headerY)continue;
            if(tm.find()&&validTime(tm)){int a=Integer.parseInt(tm.group(1))*60+Integer.parseInt(tm.group(2)),b=Integer.parseInt(tm.group(3))*60+Integer.parseInt(tm.group(4));
                if(a<1440&&b<1440&&a!=b){int n=rowPeriods.size()+1;rowPeriods.put(n,new Period(n,a,b));rowY.put(n,y);}}
            else if(!supplied.isEmpty()){Matcher n=Pattern.compile("(?:第)?0?(\\d{1,2})(?:节)?").matcher(left.toString());if(n.matches()){
                int number=Integer.parseInt(n.group(1));for(Period p:supplied)if(p.number==number){rowPeriods.put(number,p);rowY.put(number,y);}}}}
        if(rowPeriods.isEmpty())return result;
        Map<String,List<Word>> cells=new LinkedHashMap<>();
        for(Word word:words){if(word.y()<=headerY+10||word.x()<firstX-20)continue;
            int closestDay=0,xd=Integer.MAX_VALUE;for(Map.Entry<Integer,Word> h:headers.entrySet()){int delta=Math.abs(word.x()-h.getValue().x());if(delta<xd){xd=delta;closestDay=h.getKey();}}
            int row=0,yd=Integer.MAX_VALUE;for(Map.Entry<Integer,Integer> r:rowY.entrySet()){int delta=Math.abs(word.y()-r.getValue());if(delta<yd){yd=delta;row=r.getKey();}}
            if(row==0||yd>120)continue;cells.computeIfAbsent(closestDay+":"+row,k->new ArrayList<>()).add(word);}
        for(Map.Entry<String,List<Word>> cell:cells.entrySet()){List<List<Word>> cellLines=lines(cell.getValue());String name="";
            for(List<Word> line:cellLines){StringBuilder s=new StringBuilder();for(Word w:line)s.append(w.text);String value=s.toString().trim();
                if(value.isEmpty()||TIME.matcher(value).find()||value.matches("[\\d\\s\\-~周节单双方括号()（）]+")||DAY.matcher(value).find())continue;name=value;break;}
            if(name.isEmpty())continue;String[] id=cell.getKey().split(":");Period p=rowPeriods.get(Integer.parseInt(id[1]));result.add(course(Integer.parseInt(id[0]),p.start,p.end,name));}
        return result;
    }
    private static LifeData.Course course(int day,int a,int b,String name){LifeData.Course c=new LifeData.Course();c.id=UUID.randomUUID().toString();c.day=day;c.start=a;c.end=b;c.name=name.length()>100?name.substring(0,100):name;return c;}
    private static boolean validTime(Matcher m){return Integer.parseInt(m.group(1))<24&&Integer.parseInt(m.group(2))<60&&Integer.parseInt(m.group(3))<24&&Integer.parseInt(m.group(4))<60;}
    private static List<List<Word>> lines(List<Word> words){List<Word> sorted=new ArrayList<>(words);sorted.sort(Comparator.comparingInt(Word::y));List<List<Word>> result=new ArrayList<>();
        for(Word w:sorted){List<Word> target=null;for(List<Word> line:result)if(Math.abs(line.get(0).y()-w.y())<=Math.max(8,(w.bottom-w.top)*.65)){target=line;break;}
            if(target==null){target=new ArrayList<>();result.add(target);}target.add(w);}
        for(List<Word> line:result)line.sort(Comparator.comparingInt(w->w.left));return result;}
    private TimetableParser(){}
}
