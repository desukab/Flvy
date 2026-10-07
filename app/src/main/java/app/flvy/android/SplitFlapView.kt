package app.flvy.android

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class SplitFlapView(context: Context): View(context) {
    private val amber=Color.rgb(196,145,62); private val light=Color.rgb(231,195,119)
    private val dark=Color.rgb(139,94,37); private val ink=Color.rgb(15,13,10)
    private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)}
    private var time="--:--"; private var date="FLVY"; private var notice:String?=null; private var until=0L; private var downX=0f
    init { setBackgroundColor(Color.BLACK); isClickable=true; isLongClickable=true }
    fun setClock(t:String,d:String){time=t;date=d;invalidate()}
    fun showNotification(title:String,body:String){
        val s=(if(title.isBlank()) body else "$title  ·  $body").replace("\n"," ").replace(Regex("\\s+")," ").trim()
        if(s.isNotBlank()){notice=s.take(42).uppercase();until=System.currentTimeMillis()+7000;invalidate()}
    }
    override fun onDraw(c:Canvas){
        val w=width.toFloat(); val h=height.toFloat(); val cx=w/2f; val active=notice!=null && System.currentTimeMillis()<until
        if(!active) notice=null
        p.color=Color.rgb(10,10,10); c.drawRect(0f,0f,w,h,p)
        p.color=light;p.textSize=h*.038f;p.textAlign=Paint.Align.CENTER;c.drawText("FLVY",cx,h*.13f,p)
        board(c,if(active)notice!! else time,cx,h*.43f,h*.27f)
        board(c,if(active)"NEW MESSAGE" else date,cx,h*.72f,h*.07f)
        p.color=dark;p.textSize=h*.022f;c.drawText("LONG PRESS  •  NOTIFICATIONS",cx,h*.93f,p)
        if(notice!=null)postInvalidateDelayed(250)
    }
    private fun board(c:Canvas,s:String,cx:Float,cy:Float,size:Float){
        val width=width*.94f; val l=cx-width/2; val top=cy-size*.64f; val bot=cy+size*.64f
        p.color=Color.rgb(23,23,23);c.drawRoundRect(l-9,top-9,l+width+9,bot+9,16f,16f,p)
        p.color=amber;c.drawRoundRect(l,top,l+width,bot,12f,12f,p)
        p.color=Color.rgb(20,17,13);c.drawRect(l,cy-2,l+width,cy+2,p)
        p.color=ink;p.textSize=size;p.textAlign=Paint.Align.CENTER
        var out=s;while(p.measureText(out)>width-28 && out.length>3)out=out.dropLast(1)
        if(out!=s && out.length>3)out="$out…"
        c.drawText(out,cx,cy+size*.35f,p)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(e.actionMasked==MotionEvent.ACTION_DOWN){downX=e.x;return true}
        if(e.actionMasked==MotionEvent.ACTION_UP){if(abs(e.x-downX)>100){notice=null;invalidate()};performClick();return true}
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
