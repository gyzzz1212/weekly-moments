package cn.weeklymoments.guard;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class KnowledgeDeck {
    final JSONObject root;
    final JSONArray cards;
    KnowledgeDeck(Context c){try(InputStream in=c.getAssets().open("knowledge/cards.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        root=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));cards=root.getJSONArray("cards");
    }catch(Exception ex){throw new IllegalStateException("本机知识卡片读取失败",ex);}}
    JSONObject card(int index){return cards.optJSONObject(index);}
    JSONObject source(JSONObject card){return root.optJSONObject("sources").optJSONObject(card.optString("source"));}
}
