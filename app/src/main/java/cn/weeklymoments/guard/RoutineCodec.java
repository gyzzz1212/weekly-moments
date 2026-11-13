package cn.weeklymoments.guard;

import org.json.*;
import java.time.LocalDate;
import java.util.Iterator;

final class RoutineCodec {
    private static JSONArray items(java.util.List<RoutineData.Item> values)throws Exception {
        JSONArray a=new JSONArray();for(RoutineData.Item i:values)a.put(new JSONObject().put("name",i.name).put("done",i.done));return a;
    }
    private static void items(JSONArray a,java.util.List<RoutineData.Item> values)throws Exception {
        if(a==null)return;for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);RoutineData.Item item=new RoutineData.Item();item.name=o.getString("name");item.done=o.optBoolean("done");values.add(item);}
    }
    static JSONObject encode(RoutineData d)throws Exception {
        JSONObject root=new JSONObject().put("weekly",d.weeklyEnabled).put("daily",d.dailyEnabled).put("knowledge",d.knowledgeEnabled).put("sound",d.soundEnabled);
        JSONArray weeks=new JSONArray(),days=new JSONArray(),periods=new JSONArray();
        for(RoutineData.Week w:d.weeks){JSONArray habits=new JSONArray();for(RoutineData.Habit h:w.habits){JSONObject actual=new JSONObject();for(String key:h.actual.keySet())actual.put(key,h.actual.get(key));habits.put(new JSONObject().put("name",h.name).put("rule",h.rule).put("type",h.type).put("goal",h.goal).put("allowedDays",h.allowedDays).put("actual",actual));}
            weeks.put(new JSONObject().put("key",w.key).put("created",w.createdAt).put("reviewed",w.reviewedAt).put("reflection",w.reflection).put("items",items(w.items)).put("habits",habits));}
        for(RoutineData.Day d1:d.days)days.put(new JSONObject().put("key",d1.key).put("created",d1.createdAt).put("items",items(d1.items)));
        for(RoutineData.LearningPeriod p:d.learningPeriods)periods.put(new JSONObject().put("start",p.start).put("end",p.end));
        return root.put("weeks",weeks).put("days",days).put("periods",periods).put("read",new JSONArray(d.readCards));
    }
    static RoutineData decode(JSONObject root)throws Exception {
        RoutineData d=new RoutineData();if(root==null)return d;
        d.weeklyEnabled=root.optBoolean("weekly");d.dailyEnabled=root.optBoolean("daily");d.knowledgeEnabled=root.optBoolean("knowledge");d.soundEnabled=root.optBoolean("sound",true);
        JSONArray a=root.optJSONArray("weeks");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);RoutineData.Week w=new RoutineData.Week();w.key=o.getString("key");LocalDate.parse(w.key);w.createdAt=o.optLong("created");w.reviewedAt=o.optLong("reviewed");w.reflection=o.optString("reflection");items(o.optJSONArray("items"),w.items);
            JSONArray hs=o.optJSONArray("habits");if(hs!=null)for(int j=0;j<hs.length();j++){JSONObject h1=hs.getJSONObject(j);RoutineData.Habit h=new RoutineData.Habit();h.name=h1.getString("name");h.rule=h1.optString("rule");h.type=h1.optInt("type");h.goal=h1.optInt("goal",1);h.allowedDays=h1.optInt("allowedDays",127);JSONObject ac=h1.optJSONObject("actual");if(ac!=null){Iterator<String> keys=ac.keys();while(keys.hasNext()){String key=keys.next();LocalDate.parse(key);int value=ac.getInt(key);if(value<0)throw new IllegalArgumentException("实际值不能为负");h.actual.put(key,value);}}w.habits.add(h);}d.weeks.add(w);}
        a=root.optJSONArray("days");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);RoutineData.Day day=new RoutineData.Day();day.key=o.getString("key");LocalDate.parse(day.key);day.createdAt=o.optLong("created");items(o.optJSONArray("items"),day.items);d.days.add(day);}
        a=root.optJSONArray("periods");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);RoutineData.LearningPeriod p=new RoutineData.LearningPeriod();p.start=o.getString("start");p.end=o.optString("end");LocalDate.parse(p.start);if(!p.end.isEmpty()&&LocalDate.parse(p.end).isBefore(LocalDate.parse(p.start)))throw new IllegalArgumentException("学习日期顺序无效");d.learningPeriods.add(p);}
        a=root.optJSONArray("read");if(a!=null)for(int i=0;i<a.length();i++)d.readCards.add(a.getString(i));return d;
    }
    private RoutineCodec(){}
}
