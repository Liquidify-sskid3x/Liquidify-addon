package com.example.addon.modules;
import com.example.addon.AddonTemplate;
import com.sun.jna.platform.win32.User32;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.List;
public class MusicPlayerModule extends Module {
    private static final int VK_MEDIA_NEXT_TRACK=0xB0;
    private static final int VK_MEDIA_PREV_TRACK=0xB1;
    private static final int VK_MEDIA_STOP=0xB2;
    private static final int VK_MEDIA_PLAY_PAUSE=0xB3;
    private final SettingGroup sg=settings.getDefaultGroup();
    public final Setting<Double> volume=sg.add(new DoubleSetting.Builder().name("volume").defaultValue(80).min(0).max(100).sliderMax(100).onChanged(v->AudioPlayer.get().setVolume(v.floatValue()/100f)).build());
    public final Setting<Boolean> stopOnDisable=sg.add(new BoolSetting.Builder().name("stop-on-disable").defaultValue(true).build());
    public final Setting<Boolean> stopOnLeave=sg.add(new BoolSetting.Builder().name("stop-on-leave").defaultValue(true).build());
    public final Setting<Integer> scrollSpeed=sg.add(new IntSetting.Builder().name("title-scroll-speed").description("Ticks between scroll steps. Lower = faster.").defaultValue(2).min(1).max(10).sliderMin(1).sliderMax(10).build());
    public record QueueEntry(String url,String videoId,String title,java.nio.file.Path file){}
    private final List<QueueEntry> queue=new ArrayList<>();
    private int qi=0;
    private boolean loading=false;
    private String status="";
    private volatile double dlProgress=-1;
    private volatile long dlBytes=0,dlTotal=0;
    private int scrollOff=0,scrollTick=0;
    private static final int MAX_CHARS=28,PAUSE_TICKS=30;
    private enum RepeatMode{OFF,ALL,ONE}
    private RepeatMode repeat=RepeatMode.OFF;
    private YouTubeExtractor ext;
    private Runnable reloader;
    private boolean gWas=false,jWas=false,qWas=false,pWas=false;
    public MusicPlayerModule(){super(AddonTemplate.CATEGORY,"yt-music","Plays YouTube videos as audio in-game.");}
    @Override public void onActivate(){AudioPlayer.get().setVolume(volume.get().floatValue()/100f);if(ext==null)ext=new YouTubeExtractor(MinecraftClient.getInstance().runDirectory.toPath());AudioPlayer.get().setOnStateChange(s->{if(s==AudioPlayer.State.STOPPED)MinecraftClient.getInstance().execute(this::tryNext);reload();});}
    @Override public void onDeactivate(){if(stopOnDisable.get())AudioPlayer.get().stop();}
    @EventHandler private void onLeave(GameLeftEvent e){if(stopOnLeave.get())AudioPlayer.get().stop();}
    @EventHandler private void onTick(TickEvent.Pre e){
        if(mc.player==null)return;
        boolean gDown=isVK(VK_MEDIA_PLAY_PAUSE);
        boolean jDown=isVK(VK_MEDIA_STOP);
        boolean qDown=isVK(VK_MEDIA_PREV_TRACK);
        boolean pDown=isVK(VK_MEDIA_NEXT_TRACK);
        if(gDown&&!gWas)AudioPlayer.get().togglePause();
        if(jDown&&!jWas)AudioPlayer.get().pause();
        if(qDown&&!qWas)AudioPlayer.get().seekSeconds(Integer.MIN_VALUE);
        if(pDown&&!pWas)tryNext();
        gWas=gDown;jWas=jDown;qWas=qDown;pWas=pDown;}
    private boolean isVK(int vk){try{return(User32.INSTANCE.GetAsyncKeyState(vk)&0x8000)!=0;}catch(Exception e){return false;}}
    public String getCurrentVideoId(){if(qi<0||qi>=queue.size())return null;return queue.get(qi).videoId();}
    public String getScrollingTitle(){String t=AudioPlayer.get().getTitle();if(t==null||t.length()<=MAX_CHARS)return t==null?"":t;String p=t+"     ";int len=p.length();int s=scrollOff%len;return(p+p).substring(s,s+MAX_CHARS);}
    public void tickScroll(){String t=AudioPlayer.get().getTitle();if(t==null||t.length()<=MAX_CHARS){scrollOff=0;scrollTick=0;return;}scrollTick++;if(scrollOff==0&&scrollTick<PAUSE_TICKS)return;String p=t+"     ";if(scrollOff>=p.length()&&scrollTick%PAUSE_TICKS!=0)return;if(scrollTick%scrollSpeed.get()==0){scrollOff++;if(scrollOff>=p.length()){scrollOff=0;scrollTick=0;}}}
    private YouTubeExtractor getExt(){if(ext==null)ext=new YouTubeExtractor(MinecraftClient.getInstance().runDirectory.toPath());return ext;}
    private void addUrl(String url){
        if(url.isBlank())return;YouTubeExtractor e=getExt();
        if(!e.isReady()){status="yt-dlp or ffmpeg not ready";reload();return;}
        loading=true;dlProgress=-1;dlBytes=0;dlTotal=0;status="Starting download...";reload();
        e.extractWithProgress(url,(bytes,total)->{
            dlBytes=bytes;dlTotal=total;
            dlProgress=total>0?(bytes*100.0/total):-1;
            status="Downloading: "+(dlProgress>=0?String.format("%.1f%%",dlProgress):"...")+(total>0?" ("+formatBytes(bytes)+"/"+formatBytes(total)+")":"");
            reload();
        }).thenAccept(info->{
            loading=false;dlProgress=-1;
            status="Added: "+trunc(info.title(),40);
            QueueEntry en=new QueueEntry(url,info.videoId(),info.title(),info.audioFile());
            queue.add(en);
            AudioPlayer p=AudioPlayer.get();
            if(p.getState()==AudioPlayer.State.IDLE||p.getState()==AudioPlayer.State.STOPPED)playEntry(queue.size()-1);
            reload();
        }).exceptionally(ex->{loading=false;dlProgress=-1;String m=ex.getCause()!=null?ex.getCause().getMessage():ex.getMessage();status="Error: "+trunc(m,50);reload();return null;});}
    private String formatBytes(long b){if(b<1024)return b+"B";if(b<1024*1024)return String.format("%.1fKB",b/1024.0);return String.format("%.1fMB",b/(1024.0*1024));}
    private void playEntry(int idx){if(idx<0||idx>=queue.size())return;qi=idx;scrollOff=0;scrollTick=0;QueueEntry e=queue.get(idx);AudioPlayer.get().play(e.file(),e.title());}
    private void tryNext(){if(repeat==RepeatMode.ONE){playEntry(qi);return;}if(qi+1<queue.size()){playEntry(qi+1);return;}if(repeat==RepeatMode.ALL&&!queue.isEmpty())playEntry(0);}
    private void reload(){if(reloader==null)return;MinecraftClient mc=MinecraftClient.getInstance();if(mc!=null)mc.execute(reloader);else reloader.run();}
    @Override public WWidget getWidget(GuiTheme theme){
        if(ext==null)ext=new YouTubeExtractor(MinecraftClient.getInstance().runDirectory.toPath());
        WVerticalList root=theme.verticalList();
        reloader=()->{root.clear();build(theme,root);root.invalidate();};
        build(theme,root);return root;}
    private void build(GuiTheme theme,WVerticalList root){
        YouTubeExtractor e=getExt();
        if(!e.isReady()){
            root.add(theme.label("Required tools not found.")).widget();
            root.add(theme.label("Saved to: .minecraft/yt-music/")).widget();
            root.add(theme.horizontalSeparator()).expandX();
            if(e.isDownloadingYtDlp()){root.add(theme.label("Downloading yt-dlp... "+formatBytes(e.getYtDlpDownloadedBytes()))).widget();}
            else if(!e.isYtDlpPresent()){WButton b=root.add(theme.button("Download yt-dlp")).expandX().widget();b.action=()->{status="Downloading yt-dlp...";reload();e.downloadYtDlp().thenAccept(ok->{status=ok?"yt-dlp ready!":"yt-dlp download failed";reload();});};}
            else{root.add(theme.label("yt-dlp: ready")).widget();}
            if(e.isDownloadingFfmpeg()){double pct=e.getFfmpegProgress();root.add(theme.label("Downloading ffmpeg..."+(pct>=0?String.format(" %.1f%%",pct):"")+" "+formatBytes(e.getFfmpegDownloadedBytes()))).widget();}
            else if(!e.isFfmpegPresent()){WButton b=root.add(theme.button("Download ffmpeg")).expandX().widget();b.action=()->{status="Downloading ffmpeg (~50MB)...";reload();e.downloadFfmpeg().thenAccept(ok->{status=ok?"ffmpeg ready!":"ffmpeg download failed";reload();});};}
            else{root.add(theme.label("ffmpeg: ready")).widget();}
            if(!status.isBlank())root.add(theme.label(status)).widget();
            return;}
        WHorizontalList cr=root.add(theme.horizontalList()).expandX().widget();
        cr.add(theme.label("Cache: "+e.getCacheSizeMB()+" MB")).expandX().widget();
        WButton clr=cr.add(theme.button("Clear Cache")).widget();
        clr.action=()->{queue.clear();AudioPlayer.get().stop();e.clearCache();status="Cache cleared.";reload();};
        root.add(theme.horizontalSeparator()).expandX();
        WHorizontalList cookieRow=root.add(theme.horizontalList()).expandX().widget();
        cookieRow.add(theme.label("Cookies: "+(e.hasCookies()?"ready":"none"))).expandX().widget();
        if(e.isExportingCookies()){cookieRow.add(theme.label("Exporting...")).widget();}
        else{
            WButton expBtn=cookieRow.add(theme.button("Export from Chrome")).widget();
            expBtn.action=()->{reload();e.exportCookiesFromChrome().thenAccept(ok->reload());};
            if(e.hasCookies()){WButton delCookies=cookieRow.add(theme.button("Clear Cookies")).widget();delCookies.action=()->{try{java.nio.file.Files.deleteIfExists(e.getCookiesPath());}catch(Exception ex){}reload();};}}
        if(!e.getCookieExportStatus().isBlank())root.add(theme.label(e.getCookieExportStatus())).widget();
        root.add(theme.label("Close Chrome before exporting cookies.")).widget();
        root.add(theme.horizontalSeparator()).expandX();
        WHorizontalList ir=root.add(theme.horizontalList()).expandX().widget();
        WTextBox ub=ir.add(theme.textBox("","Paste YouTube URL...")).expandX().widget();
        WButton ab=ir.add(theme.button("Add")).widget();
        Runnable sub=()->{String u=ub.get().strip();if(!u.isBlank()){addUrl(u);ub.set("");}};
        ab.action=sub;ub.action=sub;
        if(loading){root.add(theme.label(status)).widget();if(dlProgress>=0){root.add(theme.label(buildProgressBar(dlProgress)+" "+String.format("%.1f%%",dlProgress))).widget();if(dlTotal>0)root.add(theme.label(formatBytes(dlBytes)+" / "+formatBytes(dlTotal))).widget();}}
        else if(!status.isBlank()){root.add(theme.label(status)).widget();}
        String le=AudioPlayer.get().getLastError();
        if(!le.isBlank())root.add(theme.label("Error: "+trunc(le,55))).widget();
        root.add(theme.horizontalSeparator()).expandX();
        AudioPlayer player=AudioPlayer.get();
        if(!player.getTitle().isBlank()){
            String st=switch(player.getState()){case PLAYING->"[>]";case PAUSED->"||";case LOADING->"...";default->"[S]";};
            String dt=player.getTitle().length()>MAX_CHARS?getScrollingTitle():player.getTitle();
            root.add(theme.label(st+"  "+dt)).widget();
            root.add(theme.label(player.getElapsed()+" / "+player.getDuration())).widget();
            root.add(theme.label("Media keys: Play/Pause  Stop  Prev  Next")).widget();
            WHorizontalList ctl=root.add(theme.horizontalList()).widget();
            WButton prev=ctl.add(theme.button("Prev")).widget();
            WButton b5=ctl.add(theme.button("-5s")).widget();
            WButton pp=ctl.add(theme.button(player.isPlaying()?"Pause":"Play")).widget();
            WButton f5=ctl.add(theme.button("+5s")).widget();
            WButton nxt=ctl.add(theme.button("Next")).widget();
            WButton stp=ctl.add(theme.button("Stop")).widget();
            WButton rep=ctl.add(theme.button(repeat==RepeatMode.OFF?"Rep: Off":repeat==RepeatMode.ALL?"Rep: All":"Rep: One")).widget();
            prev.action=()->{if(qi>0)playEntry(qi-1);};
            b5.action=()->player.seekSeconds(-5);
            pp.action=player::togglePause;
            f5.action=()->player.seekSeconds(5);
            nxt.action=this::tryNext;
            stp.action=player::stop;
            rep.action=()->{repeat=switch(repeat){case OFF->RepeatMode.ALL;case ALL->RepeatMode.ONE;case ONE->RepeatMode.OFF;};reload();};
            WHorizontalList vr=root.add(theme.horizontalList()).expandX().widget();
            vr.add(theme.label("Vol:")).widget();
            WSlider vs=vr.add(theme.slider(volume.get(),0,100)).expandX().widget();
            vs.action=()->{double v=vs.get();volume.set(v);player.setVolume((float)(v/100.0));};
            root.add(theme.horizontalSeparator()).expandX();}
        if(!queue.isEmpty()){
            root.add(theme.label("Queue ("+queue.size()+")")).widget();
            for(int i=0;i<queue.size();i++){final int idx=i;QueueEntry en=queue.get(i);WHorizontalList row=root.add(theme.horizontalList()).expandX().widget();String pfx=(i==qi&&!AudioPlayer.get().getTitle().isBlank())?"[>] ":(i+1)+". ";row.add(theme.label(pfx+trunc(en.title(),32))).expandX().widget();WButton pb=row.add(theme.button("Play")).widget();WButton dl=row.add(theme.button("Del")).widget();pb.action=()->playEntry(idx);dl.action=()->{queue.remove(idx);reload();};}
            root.add(theme.horizontalSeparator()).expandX();
            WButton clrq=root.add(theme.button("Clear Queue")).widget();
            clrq.action=()->{queue.clear();AudioPlayer.get().stop();reload();};}
        else if(!loading){root.add(theme.label("Paste a YouTube URL above to get started.")).widget();}}
    private String buildProgressBar(double pct){int total=20;int filled=(int)(pct/100.0*total);StringBuilder sb=new StringBuilder("[");for(int i=0;i<total;i++)sb.append(i<filled?"#":".");sb.append("]");return sb.toString();}
    private String trunc(String s,int max){if(s==null)return"";return s.length()<=max?s:s.substring(0,max-1)+"...";}
}
