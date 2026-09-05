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
        if(!UpdatePolicy.validRepository(UpdateConfig.REPOSITORY)) { message(activity.getString(R.string.check_updates),activity.getString(R.string.update_unconfigured),null); return; }
        if(TurnService.instance!=null) TurnService.instance.pause(activity.getString(R.string.update_paused));
        progress=new ProgressDialog(activity); progress.setMessage(activity.getString(R.string.update_checking)); progress.setCancelable(true); progress.setOnCancelListener(d -> cancel()); progress.show();
        Thread worker=new Thread(() -> {
            try {
                JSONObject release=fetch();
                if(release.optBoolean("draft",true) || release.optBoolean("prerelease",true)) throw new IOException(activity.getString(R.string.release_not_ready));
                String tag=release.getString("tag_name"), latest=UpdatePolicy.version(tag);
                String current=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;
                if(UpdatePolicy.compare(latest,current)<=0) { deliver(activity.getString(R.string.up_to_date),activity.getString(R.string.current_version,current),null); return; }
                JSONArray assets=release.getJSONArray("assets"); String download=null;
                for(int i=0;i<assets.length();i++) {
                    JSONObject asset=assets.getJSONObject(i);
                    if(asset.optString("name").equals("PageTurner-"+latest+".apk") && "uploaded".equals(asset.optString("state")) && asset.optLong("size")>0
                        && UpdatePolicy.validAsset(UpdateConfig.REPOSITORY,tag,asset.optString("browser_download_url"))) {
                        if(download!=null) throw new IOException(activity.getString(R.string.asset_ambiguous)); download=asset.getString("browser_download_url");
                    }
                }
                if(download==null) throw new IOException(activity.getString(R.string.asset_missing));
                String notes=release.optString("body","").trim(); if(notes.length()>1800) notes=notes.substring(0,1800)+"…";
                deliver(activity.getString(R.string.new_version,latest),activity.getString(R.string.update_download_message,notes.isEmpty()?activity.getString(R.string.update_available):notes),download);
            } catch(SocketTimeoutException e) { deliver(activity.getString(R.string.update_unavailable),activity.getString(R.string.update_timeout),null); }
            catch(UnknownHostException e) { deliver(activity.getString(R.string.update_unavailable),activity.getString(R.string.update_dns),null); }
            catch(javax.net.ssl.SSLException e) { deliver(activity.getString(R.string.update_unavailable),activity.getString(R.string.update_tls),null); }
            catch(Exception e) { deliver(activity.getString(R.string.update_unavailable),e.getClass()==IOException.class?e.getMessage():activity.getString(R.string.update_invalid),null); }
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
        if(code==404) throw new IOException(activity.getString(R.string.release_missing));
        if(code==403 || code==429) throw new IOException(activity.getString(R.string.update_rate_limit));
        if(code!=200) throw new IOException(activity.getString(R.string.download_unreachable));
        try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[4096]; int n;
            while((n=in.read(bytes))!=-1) { if(cancelled) throw new IOException(activity.getString(R.string.cancelled)); if(out.size()+n>262144) throw new IOException(activity.getString(R.string.update_too_large)); out.write(bytes,0,n); }
            return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    private void deliver(String title,String body,String url) {
        activity.runOnUiThread(() -> { if(cancelled || activity.isFinishing() || activity.isDestroyed()) return; if(progress!=null) { progress.dismiss(); progress=null; } message(title,body,url); });
    }
    private void message(String title,String body,String url) {
        AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle(title).setMessage(body);
        if(url==null) dialog.setPositiveButton(activity.getString(R.string.ok),null);
        else dialog.setNegativeButton(activity.getString(R.string.later),null).setPositiveButton(activity.getString(R.string.download_update),(d,w) -> {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
            catch(ActivityNotFoundException e) { message(activity.getString(R.string.browser_missing),activity.getString(R.string.browser_enable),null); }
        });
        dialog.show();
    }
}
