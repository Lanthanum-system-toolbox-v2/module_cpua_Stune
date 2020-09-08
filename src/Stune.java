import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import utils.NumAndBoolean;
import utils.StringToList;
import xzr.La.systemtoolbox.modules.java.LModule;
import xzr.La.systemtoolbox.ui.StandardCard;
import xzr.La.systemtoolbox.utils.process.ShellUtil;

import java.util.ArrayList;
import java.util.HashMap;

public class Stune implements LModule {
    public static final String TAG="[Stune]";

    ArrayList<String> group;

    boolean colocate_available, no_override_available;

    HashMap<String,String> namemap=new HashMap(){{
        put("audio-app","音频服务");
        put("background","后台应用");
        put("foreground","前台任务");
        put("rt","低延时要求任务");
        put("top-app","当前界面任务");

    }};

    @Override
    public String classname() {
        return "cpua";
    }

    @Override
    public View init(Context context) {
        if(!ShellUtil.run("if [ -e /dev/stune/schedtune.boost ]\nthen\necho true\nfi\n",true).equals("true"))
            return null;
        no_override_available=ShellUtil.run("if [ -e /dev/stune/schedtune.sched_boost_no_override ]\nthen\necho true\nfi\n",true).equals("true");
        //boolean colocate_available=ShellUtil.run("if [ -e /dev/stune/schedtune.colocate ]\nthen\necho true\nfi\n",true).equals("true");
        colocate_available=false;

        LinearLayout linearLayout=new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView title= StandardCard.title(context);
        title.setText("EAS调度组");
        linearLayout.addView(title);
        TextView subtitle=StandardCard.subtitle(context);
        subtitle.setText("您可以在此配置不同EAS调度组的调度情况");
        linearLayout.addView(subtitle);

        String ret= ShellUtil.run("cd /dev/stune\n" +
                "for i in *\n" +
                "do\n" +
                "if [ -d $i ]\n" +
                "then\n" +
                "echo $i\n" +
                "fi\n" +
                "done\n",true);
        group= StringToList.to(ret);

        for(int i=0;i<group.size();i++){
            Button button=new Button(context);
            button.setBackgroundColor(android.R.attr.buttonBarButtonStyle);
            button.setText(node2name(group.get(i)));
            linearLayout.addView(button);
            int finalI = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showdialog(context,group.get(finalI));
                }
            });
        }
        return linearLayout;
    }

    void showdialog(Context context,String groupname){
        ScrollView scrollView=new ScrollView(context);
        LinearLayout linearLayout=new LinearLayout(context);
        scrollView.addView(linearLayout);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        {
            TextView textView = new TextView(context);
            textView.setText("调度激进程度：");
            linearLayout.addView(textView);
        }
        EditText boost=new EditText(context);
        boost.setText(ShellUtil.run("cat /dev/stune/"+groupname+"/schedtune.boost",true));
        linearLayout.addView(boost);
        {
            TextView textView = new TextView(context);
            textView.setText("* 在多数内核上，调度激进程度的取值范围为0到100，越大代表越激进，0代表完全使用“能量感知”来进行负载平衡。但是在部分第三方内核上，它的取值范围为-100到100。");
            linearLayout.addView(textView);
        }
        Switch prefer_idle=new Switch(context);
        linearLayout.addView(prefer_idle);
        prefer_idle.setText("优先使用空闲的核心");
        prefer_idle.setChecked(NumAndBoolean.Num2Boolean(ShellUtil.run("cat /dev/stune/"+groupname+"/schedtune.prefer_idle",true)));
        {
            TextView textView = new TextView(context);
            textView.setText("* 开启这个选项意味着调度器将会优先将负载安排到负载最低的核心上。开启这个选项有利于提升性能，但是会影响调度器对于CPU核心进入C-State的安排，从而加大电量消耗。");
            linearLayout.addView(textView);
        }
        Switch no_override=new Switch(context);
        linearLayout.addView(no_override);
        no_override.setText("优先向大核进行负载迁移");
        if(!no_override_available)
            no_override.setVisibility(View.GONE);
        no_override.setChecked(NumAndBoolean.Num2Boolean(ShellUtil.run("cat /dev/stune/"+groupname+"/schedtune.sched_boost_no_override",true)));
        if(no_override_available){
            TextView textView = new TextView(context);
            textView.setText("* 开启这个选项后，此组的负载将会被优先迁移至大核心。这将会增加功耗，但是有助于性能提升。\n* 注意，只有在负载模式被设置为对应选项时，它才会生效。");
            linearLayout.addView(textView);
        }

        Switch colocate=new Switch(context);
        linearLayout.addView(colocate);
        colocate.setText("可并置");
        if(!colocate_available)
            colocate.setVisibility(View.GONE);
        colocate.setChecked(NumAndBoolean.Num2Boolean(ShellUtil.run("cat /dev/stune/"+groupname+"/schedtune.colocate",true)));
        if(colocate_available){
            TextView textView = new TextView(context);
            textView.setText("* 开启这个选项后，这一组的任务将和其它同样开启这个选项的组的任务并置。至于这有什么用，我也不知道。");
            linearLayout.addView(textView);
        }


        new AlertDialog.Builder(context)
                .setTitle("编辑“"+node2name(groupname)+"”的CPU组配置")
                .setNegativeButton("取消",null)
                .setView(scrollView)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ShellUtil.run("echo \""+boost.getText().toString()+"\" > /dev/stune/"+groupname+"/schedtune.boost",true);
                        ShellUtil.run("echo "+NumAndBoolean.Boolean2Num(prefer_idle.isChecked())+" > /dev/stune/"+groupname+"/schedtune.prefer_idle",true);
                        if(no_override_available)
                            ShellUtil.run("echo "+NumAndBoolean.Boolean2Num(no_override.isChecked())+" > /dev/stune/"+groupname+"/schedtune.sched_boost_no_override",true);
                        if(colocate_available)
                            ShellUtil.run("echo "+NumAndBoolean.Boolean2Num(colocate.isChecked())+" > /dev/stune/"+groupname+"/schedtune.colocate",true);
                    }
                }).create().show();
    }

    String node2name(String nodename){
        String ret=namemap.get(nodename);
        if(ret!=null){
            return ret;
        }
        return nodename;
    }

    @Override
    public String onBootApply() {
        if(!ShellUtil.run("if [ -d /dev/stune ]\nthen\necho true\nfi\n",true).equals("true"))
            return null;
        String cmd="";
        for (int i=0;i<group.size();i++) {
            cmd += "echo \"" + ShellUtil.run("cat /dev/stune/" + group.get(i) + "/schedtune.boost", true)+"\" > /dev/stune/" + group.get(i) + "/schedtune.boost\n";
            cmd += "echo "+ShellUtil.run("cat /dev/stune/"+group.get(i)+"/schedtune.prefer_idle",true)+" > /dev/stune/"+group.get(i)+"/schedtune.prefer_idle\n";
            if(no_override_available)
                cmd += "echo "+ShellUtil.run("cat /dev/stune/"+group.get(i)+"/schedtune.sched_boost_no_override",true)+" > /dev/stune/"+group.get(i)+"/schedtune.sched_boost_no_override\n";
            if(colocate_available)
                cmd += "echo "+ShellUtil.run("cat /dev/stune/"+group.get(i)+"/schedtune.colocate",true)+" > /dev/stune/"+group.get(i)+"/schedtune.colocate\n";
        }
        return cmd;
    }

    @Override
    public void onExit() {

    }
}
