package local.pageturner;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class UpdateChecker {
    private final Activity activity;
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    private ProgressDialog progress;
    UpdateChecker(Activity activity) { this.activity=activity; }
    void cancel() { cancelled=true; HttpURLConnection c=connection; if(c!=null) c.disconnect(); if(progress!=null) { progress.dismiss(); progress=null; } }
    void start() {
        if(!UpdatePolicy.validRepository(UpdateConfig.REPOSITORY)) { message("检查更新","还没设置更新地址",null); return; }
        if(TurnService.instance!=null) TurnService.instance.pause("检查更新 · 已暂停");
        progress=new ProgressDialog(activity); progress.setMessage("正在检查更新…"); progress.setCancelable(true); progress.setOnCancelListener(d -> cancel()); progress.show();
        Thread worker=new Thread(() -> {
            try {
                JSONObject release=fetch();
                if(release.optBoolean("draft",true) || release.optBoolean("prerelease",true)) throw new IOException("发布信息尚未就绪");
                String tag=release.getString("tag_name"), latest=UpdatePolicy.version(tag);
                String current=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;
                if(UpdatePolicy.compare(latest,current)<=0) { deliver("已是最新版本","当前版本 "+current,null); return; }
                JSONArray assets=release.getJSONArray("assets"); String download=null;
                for(int i=0;i<assets.length();i++) {
                    JSONObject asset=assets.getJSONObject(i);
                    if(asset.optString("name").equals("PageTurner-"+latest+".apk") && "uploaded".equals(asset.optString("state")) && asset.optLong("size")>0
                        && UpdatePolicy.validAsset(UpdateConfig.REPOSITORY,tag,asset.optString("browser_download_url"))) {
                        if(download!=null) throw new IOException("安装包信息不明确"); download=asset.getString("browser_download_url");
                    }
                }
                if(download==null) throw new IOException("新版安装包尚未就绪");
                String notes=release.optString("body","").trim(); if(notes.length()>1800) notes=notes.substring(0,1800)+"…";
                deliver("发现新版 "+latest,(notes.isEmpty()?"可以更新到新版本":notes)+"\n\n用浏览器下载后，打开安装包更新",download);
            } catch(SocketTimeoutException e) { deliver("暂时无法检查","连接超时，请检查网络后重试",null); }
            catch(UnknownHostException e) { deliver("暂时无法检查","无法连接 GitHub，请检查网络后重试",null); }
            catch(javax.net.ssl.SSLException e) { deliver("暂时无法检查","无法建立安全连接，请稍后重试",null); }
            catch(Exception e) { deliver("暂时无法检查",e.getClass()==IOException.class?e.getMessage():"更新信息不可用，请稍后再试",null); }
            finally { HttpURLConnection c=connection; if(c!=null) c.disconnect(); connection=null; }
        },"page-turner-update"); worker.setDaemon(true); worker.start();
    }
    private JSONObject fetch() throws Exception {
        URL url=new URL("https://api.github.com/repos/"+UpdateConfig.REPOSITORY+"/releases/latest");
        HttpURLConnection c=(HttpURLConnection)url.openConnection(); connection=c;
        c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/vnd.github+json"); c.setRequestProperty("User-Agent","PageTurner-Android");
        c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
        int code=c.getResponseCode();
        if(code==404) throw new IOException("还没有可用的公开版本");
        if(code==403 || code==429) throw new IOException("GitHub 暂时限制了检查频率，请稍后重试");
        if(code!=200) throw new IOException("连不上下载地址，请检查网络后重试");
        try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[4096]; int n;
            while((n=in.read(bytes))!=-1) { if(cancelled) throw new IOException("已取消"); if(out.size()+n>262144) throw new IOException("更新信息过大"); out.write(bytes,0,n); }
            return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    private void deliver(String title,String body,String url) {
        activity.runOnUiThread(() -> { if(cancelled || activity.isFinishing() || activity.isDestroyed()) return; if(progress!=null) { progress.dismiss(); progress=null; } message(title,body,url); });
    }
    private void message(String title,String body,String url) {
        AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle(title).setMessage(body);
        if(url==null) dialog.setPositiveButton("知道了",null);
        else dialog.setNegativeButton("稍后",null).setPositiveButton("下载新版",(d,w) -> {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
            catch(ActivityNotFoundException e) { message("无法打开浏览器","请先安装或启用一个浏览器",null); }
        });
        dialog.show();
    }
}
