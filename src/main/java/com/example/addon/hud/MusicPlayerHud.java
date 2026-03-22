package com.example.addon.hud;
import com.example.addon.AddonTemplate;
import com.example.addon.modules.AudioPlayer;
import com.example.addon.modules.MusicPlayerModule;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
public class MusicPlayerHud extends HudElement {
    public static final HudElementInfo<MusicPlayerHud> INFO=new HudElementInfo<>(AddonTemplate.HUD_GROUP,"yt-music-hud","Shows the currently playing YouTube track.",MusicPlayerHud::new);
    private final SettingGroup sg=settings.getDefaultGroup();
    private final Setting<SettingColor> bgCol=sg.add(new ColorSetting.Builder().name("background-color").defaultValue(new SettingColor(0,0,0,160)).build());
    private final Setting<SettingColor> titCol=sg.add(new ColorSetting.Builder().name("title-color").defaultValue(new SettingColor(255,255,255,255)).build());
    private final Setting<SettingColor> chanCol=sg.add(new ColorSetting.Builder().name("channel-color").defaultValue(new SettingColor(180,180,180,255)).build());
    private final Setting<SettingColor> timeCol=sg.add(new ColorSetting.Builder().name("time-color").defaultValue(new SettingColor(100,200,255,255)).build());
    private final Setting<Double> scale=sg.add(new DoubleSetting.Builder().name("scale").defaultValue(1.0).min(0.5).sliderMax(3.0).build());
    private final Setting<Boolean> showThumb=sg.add(new BoolSetting.Builder().name("show-thumbnail").defaultValue(true).build());
    private Identifier thumbId=null;
    private String loadedVid=null;
    private boolean loadingThumb=false;
    private String loadedChanVid=null;
    private String chanName="";
    private boolean loadingChan=false;
    private static final int TW=80,TH=45,PAD=6,GAP=3;
    private final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    public MusicPlayerHud(){super(INFO);}
    @Override public void render(HudRenderer r){
        AudioPlayer p=AudioPlayer.get();
        MusicPlayerModule mod=Modules.get().get(MusicPlayerModule.class);
        if(mod!=null)mod.tickScroll();
        if(p.getState()==AudioPlayer.State.IDLE||p.getState()==AudioPlayer.State.STOPPED||p.getTitle().isBlank()){setSize(180,20*scale.get());r.text("No music playing",x,y,titCol.get(),true,scale.get());return;}
        double s=scale.get(),tH=r.textHeight(true);
        boolean th=showThumb.get();
        double tdW=th?TW*s:0,tdH=th?TH*s:0,tRight=th?tdW+PAD:0;
        String dt=mod!=null?mod.getScrollingTitle():p.getTitle();
        String ic=switch(p.getState()){case PLAYING->"> ";case PAUSED->"|| ";case LOADING->".. ";default->"";};
        String ts=p.getElapsed()+" / "+p.getDuration();
        String le=p.getLastError();boolean hasErr=!le.isBlank();
        double tAW=Math.max(r.textWidth(ic+dt,true)*s,r.textWidth(ts,true)*s);
        double totW=tRight+tAW+PAD*2;
        int lines=2+(chanName.isBlank()?0:1)+(hasErr?1:0);
        double totH=Math.max(tdH,(tH*lines+GAP*(lines-1))*s)+PAD*2;
        setSize(totW,totH);
        r.quad(x,y,totW,totH,bgCol.get());
        if(th){
            String vid=mod!=null?mod.getCurrentVideoId():null;
            if(vid!=null&&!vid.equals(loadedVid)&&!loadingThumb)loadThumb(vid);
            if(thumbId!=null){r.texture(thumbId,x+PAD,y+PAD,(int)(TW*s),(int)(TH*s),new SettingColor(255,255,255,255));}
            else{r.quad(x+PAD,y+PAD,tdW,tdH,new SettingColor(40,40,40,200));r.text("loading...",x+PAD+4,y+PAD+TH*s/2-tH/2,new SettingColor(150,150,150,200),false,s*0.7);}
            if(vid!=null&&!vid.equals(loadedChanVid)&&!loadingChan)loadChan(vid);}
        double tx=x+tRight+PAD,ty=y+PAD;
        r.text(ic+dt,tx,ty,titCol.get(),true,s);ty+=(tH+GAP)*s;
        if(!chanName.isBlank()){r.text(chanName,tx,ty,chanCol.get(),true,s);ty+=(tH+GAP)*s;}
        r.text(ts,tx,ty,timeCol.get(),true,s);ty+=(tH+GAP)*s;
        if(hasErr)r.text("! "+trunc(le,40),tx,ty,new SettingColor(255,80,80,255),true,s*0.85);}
    private void loadThumb(String vid){
        loadingThumb=true;
        String url="https://img.youtube.com/vi/"+vid+"/mqdefault.jpg";
        CompletableFuture.supplyAsync(()->{try{HttpResponse<byte[]>res=http.send(HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent","Mozilla/5.0").GET().build(),HttpResponse.BodyHandlers.ofByteArray());return res.statusCode()==200?res.body():null;}catch(Exception e){AddonTemplate.LOG.error("Thumb fetch failed",e);return null;}})
            .thenAccept(b->{loadingThumb=false;if(b==null)return;MinecraftClient.getInstance().execute(()->{try{
                BufferedImage img=ImageIO.read(new ByteArrayInputStream(b));if(img==null)return;
                BufferedImage sc=new BufferedImage(TW,TH,BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2=sc.createGraphics();g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);g2.drawImage(img,0,0,TW,TH,null);g2.dispose();
                ByteArrayOutputStream bos=new ByteArrayOutputStream();ImageIO.write(sc,"png",bos);
                NativeImage ni=NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
                NativeImageBackedTexture tex=new NativeImageBackedTexture(()->"yt_thumb_"+vid,ni);
                if(thumbId!=null)MinecraftClient.getInstance().getTextureManager().destroyTexture(thumbId);
                Identifier id=Identifier.of("addon","yt_thumb_"+vid.toLowerCase());
                MinecraftClient.getInstance().getTextureManager().registerTexture(id,tex);
                thumbId=id;loadedVid=vid;}catch(Exception e){AddonTemplate.LOG.error("Thumb decode failed",e);}});});}
    private void loadChan(String vid){
        loadingChan=true;
        CompletableFuture.supplyAsync(()->{try{HttpResponse<String>res=http.send(HttpRequest.newBuilder().uri(URI.create("https://www.youtube.com/watch?v="+vid)).header("User-Agent","Mozilla/5.0").GET().build(),HttpResponse.BodyHandlers.ofString());if(res.statusCode()!=200)return"";String body=res.body();String mk="\"ownerChannelName\":\"";int i=body.indexOf(mk);if(i==-1){mk="\"author\":\"";i=body.indexOf(mk);}if(i==-1)return"";int st=i+mk.length();int en=body.indexOf("\"",st);return en==-1?"":body.substring(st,en);}catch(Exception e){AddonTemplate.LOG.error("Chan fetch failed",e);return"";}})
            .thenAccept(n->{loadingChan=false;chanName=n;loadedChanVid=vid;});}
    private String trunc(String s,int max){if(s==null)return"";return s.length()<=max?s:s.substring(0,max-1)+"...";}
}
