package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

/** ArasGram about page inspired by the Cherry layout, using the user's own icon and links. */
public class NekoAboutActivity extends BaseFragment {
    private final int white=0xfffaf2f5, muted=0xffdfc7d0;
    private int dp(float v){return AndroidUtilities.dp(v);}
    private String tr(int id){return LocaleController.getString(id);}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(Context c,String value,int size,int color,boolean bold){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void open(String username){MessagesController.getInstance(currentAccount).openByUserName(username,this,1);}
    private LinearLayout card(Context c){LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);box.setBackground(rounded(0x35ffffff,20));return box;}
    private void section(LinearLayout root,String title){
        TextView t=text(root.getContext(),title,13,muted,true);t.setPadding(dp(10),dp(22),dp(10),dp(9));root.addView(t);
    }
    private void entry(LinearLayout card,int icon,String title,String value,Runnable click,boolean divider){
        Context c=card.getContext();
        LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),0,dp(18),0);row.setMinimumHeight(dp(62));
        ImageView image=new ImageView(c);image.setImageResource(icon);image.setColorFilter(muted);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(28),dp(28));ip.rightMargin=dp(14);row.addView(image,ip);
        TextView name=text(c,title,17,white,false);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);name.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView username=text(c,value,14,muted,false);username.setSingleLine(true);username.setEllipsize(TextUtils.TruncateAt.END);username.setMaxWidth(dp(135));username.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-2,-2);vp.leftMargin=dp(12);row.addView(username,vp);
        TextView arrow=text(c,"›",26,muted,false);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,-2);ap.leftMargin=dp(8);row.addView(arrow,ap);
        row.setOnClickListener(v->click.run());card.addView(row,new LinearLayout.LayoutParams(-1,-2));
        if(divider){View line=new View(c);line.setBackgroundColor(0x26ffffff);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(dp(60),0,dp(18),0);card.addView(line,lp);}
    }
    @Override public View createView(Context context){
        actionBar.setTitle(tr(R.string.About));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackgroundColor(0xff7e3049);actionBar.setItemsColor(Color.WHITE,false);actionBar.setTitleColor(Color.WHITE);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff84314b,0xff64243d,0xff3b1928,0xff1c1119});scroll.setBackground(bg);
        LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(18),dp(22),dp(30));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        ImageView logo=new ImageView(context);try{logo.setImageDrawable(context.getPackageManager().getApplicationIcon(context.getApplicationInfo()));}catch(Exception ignored){}logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(86),dp(86));iconParams.gravity=Gravity.CENTER_HORIZONTAL;root.addView(logo,iconParams);
        TextView title=text(context,"ArasGram 13.2.2",24,white,true);title.setGravity(Gravity.CENTER);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,-2);titleParams.topMargin=dp(12);root.addView(title,titleParams);
        TextView update=text(context,"↻  "+tr(R.string.HmsaAboutUpdates),15,white,true);update.setGravity(Gravity.CENTER);update.setBackground(rounded(0x38ffffff,30));update.setPadding(dp(24),dp(12),dp(24),dp(12));
        LinearLayout.LayoutParams up=new LinearLayout.LayoutParams(-2,-2);up.gravity=Gravity.CENTER_HORIZONTAL;up.topMargin=dp(18);root.addView(update,up);update.setOnClickListener(v->open("InaRaS5"));
        section(root,tr(R.string.HmsaAboutDeveloper));LinearLayout developer=card(context);entry(developer,R.drawable.ic_ios_person_2,"Karar","@Lx5x5",()->open("Lx5x5"),false);root.addView(developer);
        section(root,tr(R.string.HmsaAboutChannels));LinearLayout channels=card(context);entry(channels,R.drawable.ic_ios_megaphone,tr(R.string.HmsaAboutMainChannel),"@InaRaS5",()->open("InaRaS5"),true);entry(channels,R.drawable.ic_ios_megaphone,tr(R.string.HmsaAboutFirstChannel),"@aRRaS_iD",()->open("aRRaS_iD"),false);root.addView(channels);
        TextView love=text(context,"♡  "+tr(R.string.HmsaAboutMadeWithLove),15,0xffeeb1c8,true);love.setGravity(Gravity.CENTER);love.setPadding(dp(20),dp(13),dp(20),dp(13));love.setBackground(rounded(0x30ffffff,30));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.gravity=Gravity.CENTER_HORIZONTAL;lp.topMargin=dp(30);root.addView(love,lp);love.setOnClickListener(v->open("Lx5x5"));
        fragmentView=scroll;return fragmentView;
    }
}
