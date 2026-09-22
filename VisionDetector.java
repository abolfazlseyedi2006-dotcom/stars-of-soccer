package com.example.soccertrajectory;

import android.graphics.Bitmap;
import android.graphics.PointF;
import java.util.*;

public final class VisionDetector {
    public static class Scene {
        public RectF field;
        public PointF ball;
        public final ArrayList<Circle> obstacles=new ArrayList<>();
        public float confidence;
    }
    public static class Circle {
        public float x,y,r;
        public Circle(float x,float y,float r){this.x=x;this.y=y;this.r=r;}
    }
    public static class RectF {
        public float left,top,right,bottom;
        public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
    }

    // Detector tuned to the supplied 1536x689 Soccer Stars screenshot:
    // green field, white/black soccer ball, yellow player discs.
    // Coordinates are automatically scaled to the actual capture size.
    public static Scene detect(Bitmap src) {
        int w=src.getWidth(), h=src.getHeight();
        Scene s=new Scene();

        // Largest green horizontal run on the central gameplay band.
        int y0=(int)(h*.18), y1=(int)(h*.97);
        int bestL=0,bestR=w-1,best=0;
        for(int y=y0;y<y1;y+=Math.max(1,h/120)) {
            int run=0,start=0;
            for(int x=0;x<w;x+=2) {
                int c=src.getPixel(x,y);
                int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
                boolean green=g>65 && g>r*1.18 && g>b*1.02;
                if(green){ if(run==0) start=x; run+=2; }
                else {
                    if(run>best){best=run;bestL=start;bestR=x;}
                    run=0;
                }
            }
        }
        if(best< w*.35) {
            bestL=(int)(w*.195); bestR=(int)(w*.805);
        }
        s.field=new RectF(bestL,Math.max(0,(int)(h*.15)),bestR,Math.min(h,(int)(h*.985)));

        // Ball: compact white+dark circular signature.
        float bestScore=0,bx=-1,by=-1;
        int rad=Math.max(7,(int)(Math.min(w,h)*.0145));
        int step=Math.max(2,(int)(Math.min(w,h)/190f));
        int margin=Math.max(24,(int)(Math.min(w,h)*.035));
        for(int y=(int)s.field.top+rad+margin;y<s.field.bottom-rad-margin;y+=step) {
            for(int x=(int)s.field.left+rad+margin;x<s.field.right-rad-margin;x+=step) {
                float white=0,dark=0,neutral=0,total=0;
                for(int yy=-rad;yy<=rad;yy+=2) for(int xx=-rad;xx<=rad;xx+=2) {
                    int c=src.getPixel(Math.min(w-1,Math.max(0,x+xx)),Math.min(h-1,Math.max(0,y+yy)));
                    int rr=(c>>16)&255,gg=(c>>8)&255,bb=c&255;
                    int mx=Math.max(rr,Math.max(gg,bb)), mn=Math.min(rr,Math.min(gg,bb));
                    int sat=mx-mn; total++;
                    if(mn>145 && sat<60) white++;
                    if(mx<90) dark++;
                    if(sat<80) neutral++;
                }
                float wr=white/total, dr=dark/total, nr=neutral/total;
                float score=(wr*wr)*(dr+0.02f)*nr*10000f;
                if(wr>.32f && dr>.07f && nr>.78f && score>bestScore){
                    bestScore=score; bx=x; by=y;
                }
            }
        }
        if(bx>=0) s.ball=new PointF(bx,by);

        // Player discs: yellow-center + dark perimeter signature.
        int pr=Math.max(8,(int)(Math.min(w,h)*.025));
        for(int y=(int)s.field.top+pr;y<s.field.bottom-pr;y+=Math.max(5,pr/2)) {
            for(int x=(int)s.field.left+pr;x<s.field.right-pr;x+=Math.max(5,pr/2)) {
                int yellow=0,dark=0;
                for(int yy=-pr;yy<=pr;yy+=3) for(int xx=-pr;xx<=pr;xx+=3) {
                    int c=src.getPixel(x+xx,y+yy);
                    int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
                    if(r>145 && g>120 && b<95 && r>g*.9) yellow++;
                    if(r<75 && g<75 && b<75) dark++;
                }
                if(yellow>18 && dark>20) {
                    boolean near=false;
                    for(Circle q:s.obstacles)
                        if(Math.hypot(q.x-x,q.y-y)<pr*1.5){near=true;break;}
                    if(!near) s.obstacles.add(new Circle(x,y,pr*.72f));
                }
            }
        }
        s.confidence=(s.ball!=null?0.65f:0f)+(best> w*.45?0.25f:0f);
        return s;
    }
}
