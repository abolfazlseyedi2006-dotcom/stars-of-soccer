package com.example.soccertrajectory;

import android.graphics.PointF;
import java.util.*;

/**
 * Calibrated arcade-style approximation for Soccer Stars.
 *
 * Public descriptions of Soccer Stars document tabletop/flick gameplay,
 * wall rebounds and physics-based play. The game's private physics constants
 * are not public, so these constants are intentionally exposed as a profile
 * rather than being presented as Miniclip's internal values.
 */
public final class PhysicsEngine {
    // Tuned profile: normal arenas. Special arenas can require different values.
    public static final float DEFAULT_RESTITUTION = 0.955f;
    public static final float DEFAULT_FRICTION = 0.040f;
    public static final float DEFAULT_PIECE_MASS = 1.00f;
    public static final float DEFAULT_BALL_MASS = 0.72f;
    public static final float MIN_SPEED = 7f;

    public static class Body {
        public float x,y,vx,vy,r,mass;
        public final boolean ball;
        public Body(float x,float y,float r,float mass,boolean ball){this.x=x;this.y=y;this.r=r;this.mass=mass;this.ball=ball;}
    }
    public static class Result {
        public final ArrayList<PointF> ballPath=new ArrayList<>();
        public final ArrayList<PointF> strikerPath=new ArrayList<>();
        public final ArrayList<ArrayList<PointF>> bodyPaths=new ArrayList<>();
        public int collisions;
        public int wallHits;
    }

    /**
     * power is 0..1. angleDeg is the travel direction of the selected puck.
     * The launch-speed curve is intentionally non-linear: small flicks remain
     * controllable while the upper range approaches a high-speed shot.
     */
    public static float launchSpeed(float power){
        power=Math.max(0f,Math.min(1f,power));
        return 520f + 4680f*(power*0.72f + power*power*0.28f);
    }

    public static Result simulate(PointF ballStart, float ballRadius,
                                  List<VisionDetector.Circle> pieces,
                                  int selectedPiece, float angleDeg, float power,
                                  VisionDetector.RectF field, int maxWallHits,
                                  float restitution, float friction) {
        ArrayList<Body> bodies=new ArrayList<>();
        Body ball=new Body(ballStart.x,ballStart.y,ballRadius,DEFAULT_BALL_MASS,true);
        bodies.add(ball);

        int sel=-1;
        if(pieces!=null && !pieces.isEmpty()) {
            sel=Math.max(0,Math.min(selectedPiece,pieces.size()-1));
            for(VisionDetector.Circle q:pieces)
                bodies.add(new Body(q.x,q.y,q.r,DEFAULT_PIECE_MASS,false));
        }
        if(sel>=0){
            Body striker=bodies.get(1+sel);
            float speed=launchSpeed(power);
            double a=Math.toRadians(angleDeg);
            striker.vx=(float)Math.cos(a)*speed;
            striker.vy=(float)Math.sin(a)*speed;
        }

        Result out=new Result();
        for(int i=0;i<bodies.size();i++) out.bodyPaths.add(new ArrayList<>());
        record(out,bodies);

        // Small fixed timestep + several collision passes gives much more stable
        // results for fast pucks and dense clusters than a single large step.
        final float dt=.0025f;
        int wallHits=0;
        int quietFrames=0;

        for(int step=0;step<18000;step++){
            for(Body b:bodies){ b.x+=b.vx*dt; b.y+=b.vy*dt; }

            boolean activity=false;
            for(Body b:bodies){
                boolean wh=false;
                if(b.x-b.r<field.left){
                    b.x=field.left+b.r; if(b.vx<0) b.vx=-b.vx*restitution; wh=true;
                }
                if(b.x+b.r>field.right){
                    b.x=field.right-b.r; if(b.vx>0) b.vx=-b.vx*restitution; wh=true;
                }
                if(b.y-b.r<field.top){
                    b.y=field.top+b.r; if(b.vy<0) b.vy=-b.vy*restitution; wh=true;
                }
                if(b.y+b.r>field.bottom){
                    b.y=field.bottom-b.r; if(b.vy>0) b.vy=-b.vy*restitution; wh=true;
                }
                if(wh){wallHits++; out.wallHits++; activity=true;}
            }
            if(wallHits>maxWallHits) break;

            // Multiple sequential impulse passes approximate simultaneous chain
            // contacts such as puck -> ball -> puck -> wall.
            for(int pass=0;pass<5;pass++){
                for(int i=0;i<bodies.size();i++) for(int j=i+1;j<bodies.size();j++){
                    Body A=bodies.get(i), B=bodies.get(j);
                    float dx=B.x-A.x, dy=B.y-A.y;
                    float d2=dx*dx+dy*dy, min=A.r+B.r;
                    if(d2>min*min) continue;

                    float d=(float)Math.sqrt(Math.max(d2,.000001f));
                    float nx=dx/d, ny=dy/d;
                    float overlap=min-d;
                    float invA=1f/A.mass, invB=1f/B.mass, invSum=invA+invB;

                    // Positional correction avoids objects sticking together.
                    float correction=Math.max(0f,overlap-0.02f);
                    if(correction>0){
                        A.x-=nx*correction*(invA/invSum); A.y-=ny*correction*(invA/invSum);
                        B.x+=nx*correction*(invB/invSum); B.y+=ny*correction*(invB/invSum);
                    }

                    float rvx=B.vx-A.vx, rvy=B.vy-A.vy;
                    float vn=rvx*nx+rvy*ny;
                    if(vn<0){
                        float impulse=-(1f+restitution)*vn/invSum;
                        A.vx-=impulse*invA*nx; A.vy-=impulse*invA*ny;
                        B.vx+=impulse*invB*nx; B.vy+=impulse*invB*ny;
                        out.collisions++;
                        activity=true;
                    }
                }
            }

            // Tangential + linear energy loss. This makes long shots slow down
            // gradually while preserving the reflection direction.
            float damp=Math.max(0f,1f-friction*dt);
            for(Body b:bodies){
                b.vx*=damp; b.vy*=damp;
                if(Math.hypot(b.vx,b.vy)<MIN_SPEED){b.vx=0;b.vy=0;}
            }

            record(out,bodies);
            float total=0f;
            for(Body b:bodies) total+=(float)Math.hypot(b.vx,b.vy);
            if(total<MIN_SPEED*1.5f){
                quietFrames++;
                if(quietFrames>18) break;
            }else quietFrames=0;
        }
        return out;
    }

    private static void record(Result out, ArrayList<Body> bodies){
        out.ballPath.add(new PointF(bodies.get(0).x,bodies.get(0).y));
        for(int i=0;i<bodies.size();i++)
            out.bodyPaths.get(i).add(new PointF(bodies.get(i).x,bodies.get(i).y));
        if(bodies.size()>1) out.strikerPath.add(new PointF(bodies.get(1).x,bodies.get(1).y));
    }
}
