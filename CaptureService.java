package com.example.soccertrajectory;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.MediaProjection;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class CaptureService extends Service {
    WindowManager wm;
    OverlayView overlay;
    MediaProjection projection;
    ImageReader reader;
    Handler handler;
    int width,height;
    VisionDetector.Scene scene;
    float angle=-20f,power=.62f;
    boolean autoSelect=true;
    boolean manualBall=false;
    PointF manual=new PointF();

    @Override public void onCreate(){
        super.onCreate();
        handler=new Handler(Looper.getMainLooper());
        createChannel();
        startForeground(44, notification());
    }

    @Override public int onStartCommand(Intent in,int flags,int id){
        int result=in.getIntExtra("resultCode",-1);
        Intent data=in.getParcelableExtra("data");
        android.media.projection.MediaProjectionManager m=(android.media.projection.MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);
        if(result==-1 && data!=null){
            projection=m.getMediaProjection(result,data);
            startCapture();
        }
        return START_NOT_STICKY;
    }

    void startCapture(){
        width=getResources().getDisplayMetrics().widthPixels;
        height=getResources().getDisplayMetrics().heightPixels;
        int density=getResources().getDisplayMetrics().densityDpi;
        reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
        projection.createVirtualDisplay("SS-Trajectory",width,height,density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);

        overlay=new OverlayView(this);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        wm.addView(overlay,p);
        addControls();

        reader.setOnImageAvailableListener(r -> process(r),handler);
    }

    LinearLayout controlBox;
    void addControls(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setPadding(4,4,4,4);
        box.setBackgroundColor(0xCC111111);
        String[] labels={"◀","▶","P−","P+","خودکار","مهره−","مهره+","×"};
        for(String label:labels){
            Button b=new Button(this); b.setText(label); box.addView(b);
            if(label.equals("◀")) b.setOnClickListener(v->{angle-=2; if(overlay!=null)overlay.invalidate();});
            if(label.equals("▶")) b.setOnClickListener(v->{angle+=2; if(overlay!=null)overlay.invalidate();});
            if(label.equals("P−")) b.setOnClickListener(v->{power=Math.max(.10f,power-.05f); if(overlay!=null)overlay.invalidate();});
            if(label.equals("P+")) b.setOnClickListener(v->{power=Math.min(1f,power+.05f); if(overlay!=null)overlay.invalidate();});
            if(label.equals("خودکار")) b.setOnClickListener(v->{autoSelect=true; selectedPiece=nearestPieceToBall(); if(overlay!=null)overlay.invalidate();});
            if(label.equals("مهره−")) b.setOnClickListener(v->{selectedPiece=Math.max(0,selectedPiece-1); if(overlay!=null)overlay.invalidate();});
            if(label.equals("مهره+")) b.setOnClickListener(v->{selectedPiece=Math.min(Math.max(0,scene==null?0:scene.obstacles.size()-1),selectedPiece+1); if(overlay!=null)overlay.invalidate();});
            if(label.equals("×")) b.setOnClickListener(v->stopSelf());
        }
        WindowManager.LayoutParams cp=new WindowManager.LayoutParams(-2,-2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        cp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; cp.y=75;
        wm.addView(box,cp); controlBox=box;
    }

    int nearestPieceToBall(){
        if(scene==null || scene.ball==null || scene.obstacles.isEmpty()) return 0;
        int best=0; float bd=Float.MAX_VALUE;
        for(int i=0;i<scene.obstacles.size();i++){
            VisionDetector.Circle q=scene.obstacles.get(i);
            float d=(float)Math.hypot(q.x-scene.ball.x,q.y-scene.ball.y);
            if(d<bd){bd=d;best=i;}
        }
        return best;
    }

    void process(ImageReader r){
        Image im=null;
        try{
            im=r.acquireLatestImage();
            if(im==null)return;
            Image.Plane pl=im.getPlanes()[0];
            java.nio.ByteBuffer buf=pl.getBuffer();
            Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            b.copyPixelsFromBuffer(buf);
            VisionDetector.Scene sc=VisionDetector.detect(b);
            scene=sc;
            if(autoSelect) selectedPiece=nearestPieceToBall();
            if(!manualBall && sc.ball!=null) manual.set(sc.ball.x,sc.ball.y);
            handler.post(()->{if(overlay!=null)overlay.invalidate();});
            b.recycle();
        }catch(Exception ignored){} finally {if(im!=null)im.close();}
    }

    public class OverlayView extends View {
        Paint path=new Paint(1), dot=new Paint(1), obs=new Paint(1), txt=new Paint(1);
        boolean dragging=false;
        OverlayView(Context c){super(c);setFocusable(true);path.setColor(0xFF00FF66);path.setStrokeWidth(7);path.setStyle(Paint.Style.STROKE);
            dot.setColor(0xFFFFD740);obs.setColor(0x55FF5252);txt.setColor(Color.WHITE);txt.setTextSize(30);}
        @Override protected void onDraw(Canvas c){
            if(scene==null || scene.field==null)return;
            PointF ball=manualBall?manual:(scene.ball!=null?scene.ball:new PointF(width*.4f,height*.55f));
            c.drawCircle(ball.x,ball.y,15,dot);
            for(int i=0;i<scene.obstacles.size();i++){
                VisionDetector.Circle o=scene.obstacles.get(i);
                Paint pp=new Paint(obs);
                pp.setColor(i==selectedPiece?0x88FFFFFF:0x55FF5252);
                c.drawCircle(o.x,o.y,o.r,pp);
            }
            PhysicsEngine.Result rr=PhysicsEngine.simulate(ball,15,scene.obstacles,selectedPiece,angle,power,scene.field,7,PhysicsEngine.DEFAULT_RESTITUTION,PhysicsEngine.DEFAULT_FRICTION);
            Path bp=new Path(); boolean first=true;
            for(PointF q:rr.ballPath){if(first){bp.moveTo(q.x,q.y);first=false;}else bp.lineTo(q.x,q.y);}
            c.drawPath(bp,path);
            if(rr.strikerPath.size()>1){
                Paint sp=new Paint(path); sp.setColor(0x66FFFFFF); sp.setStrokeWidth(4);
                Path pp=new Path(); first=true;
                for(PointF q:rr.strikerPath){if(first){pp.moveTo(q.x,q.y);first=false;}else pp.lineTo(q.x,q.y);}
                c.drawPath(pp,sp);
            }
            // Draw predicted trajectories of other moving pieces faintly so a
            // chain collision remains visible instead of showing only the ball.
            Paint chain=new Paint(path); chain.setColor(0x44FFFFFF); chain.setStrokeWidth(3);
            for(int i=1;i<rr.bodyPaths.size();i++){
                if(i==1+selectedPiece) continue;
                ArrayList<PointF> pts=rr.bodyPaths.get(i);
                if(pts.size()<2) continue;
                Path cp=new Path(); boolean cf=true;
                for(PointF q:pts){if(cf){cp.moveTo(q.x,q.y);cf=false;}else cp.lineTo(q.x,q.y);}
                c.drawPath(cp,chain);
            }
            c.drawText("Ball "+Math.round(scene.confidence*100)+"% | "+Math.round(angle)+"° | "+Math.round(power*100)+"% | مهره "+(selectedPiece+1)+" | برخورد "+rr.collisions+" | دیوار "+rr.wallHits,20,40,txt);
        }
    
    }

    @Override public void onDestroy(){
        try{if(reader!=null)reader.close(); if(projection!=null)projection.stop(); if(wm!=null&&overlay!=null)wm.removeView(overlay);
        if(wm!=null&&controlBox!=null)wm.removeView(controlBox);}catch(Exception ignored){}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
    Notification notification(){return new Notification.Builder(this,"cap").setContentTitle("Soccer Stars Trajectory PRO").setContentText("Screen analysis is active").setSmallIcon(android.R.drawable.ic_menu_compass).build();}
    void createChannel(){if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("cap","Trajectory Capture",NotificationManager.IMPORTANCE_LOW));}
}
