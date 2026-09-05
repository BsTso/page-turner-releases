package local.pageturner;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

public class PracticeActivity extends Activity {
    static boolean visible;
    private int page = 1;
    @Override public void onResume() { super.onResume(); visible=true; }
    @Override public void onPause() { visible=false; super.onPause(); }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Ui.BG);
        ScrollView scroll=new ScrollView(this); LinearLayout content=Ui.column(this); content.setPadding(Ui.dp(this,28),Ui.dp(this,140),Ui.dp(this,28),Ui.dp(this,48)); scroll.addView(content); root.addView(scroll);
        for(int i=1;i<=6;i++) { TextView part=Ui.text(this,String.format("%02d",i)+"\n\n向下浏览",32,i%2==0?Ui.MUTED:Ui.INK); part.setGravity(Gravity.CENTER); part.setBackground(Ui.shape(this,i%2==0?0xffededf0:0xffffffff,24)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,320)); lp.bottomMargin=Ui.dp(this,18); content.addView(part,lp); }
        TextView text=Ui.text(this,"练习 · 第 1 页",24,Ui.INK); Ui.bold(text); text.setPadding(Ui.dp(this,28),Ui.dp(this,60),Ui.dp(this,28),Ui.dp(this,20)); text.setBackgroundColor(Ui.BG); root.addView(text,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        TextView next=Ui.action(this,"下一页",Color.WHITE,Ui.INK); FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(Ui.dp(this,116),Ui.dp(this,64),Gravity.RIGHT|Gravity.CENTER_VERTICAL); np.rightMargin=Ui.dp(this,12); root.addView(next,np);
        next.setOnClickListener(v -> { text.setText("练习 · 第 "+(++page)+" 页"); scroll.scrollTo(0,0); }); setContentView(root);
        if (TurnService.instance != null) TurnService.instance.showPanel();
    }
}
