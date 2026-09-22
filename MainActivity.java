package com.example.soccertrajectory;

import android.app.*;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import android.graphics.Color;

public class MainActivity extends Activity {
    static final int OVERLAY=20, CAPTURE=21;
    MediaProjectionManager mp;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(28,28,28,28);

        TextView title=new TextView(this);
        title.setText("Soccer Stars Trajectory PRO v3");
        title.setTextSize(23); title.setTextColor(Color.BLACK); l.addView(title);

        TextView info=new TextView(this);
        info.setText("\\nاسکرین‌شات ارسالی شما برای طراحی detector مبنا قرار گرفت. "
          +"نسخه 3 تصویر صفحه را با MediaProjection می‌گیرد، زمین و توپ را تخمین می‌زند و مسیر را با برخورد به دیواره/موانع روی Overlay رسم می‌کند.\\n\\n"
          +"برای دقت بیشتر، بعد از اجرای بازی می‌توانی نقطه توپ را با دست روی Overlay اصلاح کنی.");
        info.setTextSize(15); l.addView(info);

        Button start=new Button(this); start.setText("شروع تشخیص و Overlay");
        start.setOnClickListener(v -> {
            if(!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:"+getPackageName())));
                Toast.makeText(this,"مجوز نمایش روی برنامه‌ها را فعال کن و دوباره بزن.",Toast.LENGTH_LONG).show();
                return;
            }
            mp=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mp.createScreenCaptureIntent(), CAPTURE);
        }); l.addView(start);

        Button stop=new Button(this); stop.setText("خاموش");
        stop.setOnClickListener(v -> stopService(new Intent(this,CaptureService.class)));
        l.addView(stop);

        setContentView(l);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==CAPTURE && resultCode==RESULT_OK && data!=null) {
            Intent i=new Intent(this,CaptureService.class);
            i.putExtra("resultCode",resultCode);
            i.putExtra("data",data);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        }
    }
}
