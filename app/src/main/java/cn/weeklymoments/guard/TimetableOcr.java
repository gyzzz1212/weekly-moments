package cn.weeklymoments.guard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.googlecode.tesseract.android.ResultIterator;
import java.io.*;
import java.util.*;

final class TimetableOcr {
    static final class Result { String text=""; final List<TimetableParser.Word> words=new ArrayList<>(); }
    static Bitmap bitmap(Context c,Uri uri) throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("图片无法读取，请使用清晰的 JPG 或 PNG 课表");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>2800)options.inSampleSize*=2;
        Bitmap bitmap;try(InputStream in=c.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)throw new IOException("图片解码失败");
        try(InputStream in=c.getContentResolver().openInputStream(uri)){
            int orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix=new Matrix();
            if(orientation==ExifInterface.ORIENTATION_ROTATE_90)matrix.postRotate(90);
            else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)matrix.postRotate(180);
            else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)matrix.postRotate(270);
            else if(orientation==ExifInterface.ORIENTATION_FLIP_HORIZONTAL)matrix.postScale(-1,1);
            else if(orientation==ExifInterface.ORIENTATION_FLIP_VERTICAL)matrix.postScale(1,-1);
            if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
        }catch(IOException ignored){}
        return bitmap;
    }
    static Result recognize(Context c,Uri uri) throws Exception {
        File base=new File(c.getFilesDir(),"ocr"),tessdata=new File(base,"tessdata");if(!tessdata.isDirectory()&&!tessdata.mkdirs())throw new IOException("无法准备识别模型");
        for(String language:new String[]{"chi_sim","eng"}){
            File dest=new File(tessdata,language+".traineddata");if(dest.exists()&&dest.length()>100_000)continue;
            File tmp=new File(tessdata,language+".tmp");
            try(InputStream in=c.getAssets().open("tessdata/"+language+".traineddata");FileOutputStream out=new FileOutputStream(tmp)){
                byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            if(!tmp.renameTo(dest))throw new IOException("无法保存识别模型");
        }
        Bitmap image=bitmap(c,uri);TessBaseAPI api=new TessBaseAPI();
        try{
            if(!api.init(base.getAbsolutePath(),"chi_sim+eng",TessBaseAPI.OEM_LSTM_ONLY))throw new IOException("本机文字识别初始化失败");
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);api.setImage(image);
            Result result=new Result();String text=api.getUTF8Text();result.text=text==null?"":text;
            ResultIterator iterator=api.getResultIterator();if(iterator!=null)try{iterator.begin();do{
                String word=iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD);int[] box=iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD);
                if(word!=null&&!word.trim().isEmpty()&&box!=null&&box.length==4)result.words.add(new TimetableParser.Word(word.trim(),box[0],box[1],box[2],box[3]));
            }while(iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD));}finally{iterator.delete();}
            return result;
        }finally{api.recycle();image.recycle();}
    }
    private TimetableOcr(){}
}
