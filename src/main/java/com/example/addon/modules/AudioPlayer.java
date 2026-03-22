package com.example.addon.modules;
import com.example.addon.AddonTemplate;
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.Consumer;
public class AudioPlayer {
    public enum State{IDLE,LOADING,PLAYING,PAUSED,STOPPED}
    private static AudioPlayer I;
    public static AudioPlayer get(){if(I==null)I=new AudioPlayer();return I;}
    private final ExecutorService ex=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"YTMusic-Player");t.setDaemon(true);return t;});
    private volatile Clip clip;
    private volatile State state=State.IDLE;
    private volatile String title="";
    private volatile float vol=0.8f;
    private volatile Future<?> pending;
    private volatile String lastErr="";
    private volatile boolean suppress=false;
    private Consumer<State> onChange=s->{};
    public void setOnStateChange(Consumer<State> cb){this.onChange=cb;}
    public String getLastError(){return lastErr;}
    public void play(Path file,String t){
        suppress=true;stop();suppress=false;
        title=t;lastErr="";setState(State.LOADING);
        pending=ex.submit(()->{
            try{
                BufferedInputStream bis=new BufferedInputStream(new FileInputStream(file.toFile()));
                MpegAudioFileReader reader=new MpegAudioFileReader();
                AudioInputStream raw;
                try{raw=reader.getAudioInputStream(bis);}
                catch(Exception e){lastErr="Cannot open MP3: "+e.getMessage();AddonTemplate.LOG.error("[YTMusic] {}",lastErr);setState(State.IDLE);return;}
                AudioFormat src=raw.getFormat();
                AddonTemplate.LOG.info("[YTMusic] src: {}",src);
                AudioFormat pcm=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,src.getSampleRate()>0?src.getSampleRate():44100f,16,src.getChannels()>0?src.getChannels():2,src.getChannels()>0?src.getChannels()*2:4,src.getSampleRate()>0?src.getSampleRate():44100f,false);
                MpegFormatConversionProvider converter=new MpegFormatConversionProvider();
                AudioInputStream decoded;
                try{decoded=converter.getAudioInputStream(pcm,raw);}
                catch(Exception e){lastErr="PCM convert failed: "+e.getMessage();AddonTemplate.LOG.error("[YTMusic] {}",lastErr);setState(State.IDLE);return;}
                DataLine.Info info=new DataLine.Info(Clip.class,pcm);
                if(!AudioSystem.isLineSupported(info)){lastErr="Audio line not supported.";AddonTemplate.LOG.error("[YTMusic] {}",lastErr);setState(State.IDLE);return;}
                clip=(Clip)AudioSystem.getLine(info);
                clip.open(decoded);
                applyVol();
                clip.addLineListener(e->{if(e.getType()==LineEvent.Type.STOP&&state==State.PLAYING)setState(State.STOPPED);});
                clip.start();
                setState(State.PLAYING);
                AddonTemplate.LOG.info("[YTMusic] playing: {}",t);
            }catch(Exception e){lastErr=e.getMessage()!=null?e.getMessage():e.getClass().getSimpleName();AddonTemplate.LOG.error("[YTMusic] failed '{}': {}",t,lastErr);setState(State.IDLE);}
        });}
    public void pause(){if(clip!=null&&clip.isRunning()){clip.stop();setState(State.PAUSED);}}
    public void resume(){if(clip!=null&&state==State.PAUSED){clip.start();setState(State.PLAYING);}}
    public void togglePause(){if(state==State.PLAYING)pause();else if(state==State.PAUSED)resume();}
    public void stop(){if(pending!=null)pending.cancel(true);if(clip!=null){clip.stop();clip.close();clip=null;}setState(State.STOPPED);}
    public void seekSeconds(int d){
        if(clip==null)return;
        if(d==Integer.MIN_VALUE){clip.setMicrosecondPosition(0);return;}
        long p=clip.getMicrosecondPosition()+(d*1_000_000L);
        p=Math.max(0,Math.min(clip.getMicrosecondLength(),p));
        clip.setMicrosecondPosition(p);}
    public void setVolume(float v){vol=Math.max(0f,Math.min(1f,v));applyVol();}
    public State getState(){return state;}
    public String getTitle(){return title;}
    public boolean isPlaying(){return state==State.PLAYING;}
    public float getVolume(){return vol;}
    public float getProgress(){if(clip==null||clip.getFrameLength()==0)return 0f;return(float)clip.getFramePosition()/clip.getFrameLength();}
    public String getElapsed(){return fmt(clip==null?0:clip.getMicrosecondPosition()/1_000_000L);}
    public String getDuration(){return fmt(clip==null?0:clip.getMicrosecondLength()/1_000_000L);}
    private String fmt(long s){return"%d:%02d".formatted(s/60,s%60);}
    private void applyVol(){if(clip==null||!clip.isControlSupported(FloatControl.Type.MASTER_GAIN))return;FloatControl g=(FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);float dB=vol==0f?g.getMinimum():(float)(20.0*Math.log10(vol));g.setValue(Math.max(g.getMinimum(),Math.min(g.getMaximum(),dB)));}
    private void setState(State s){state=s;if(suppress)return;try{onChange.accept(s);}catch(Exception ignored){}}
    public void shutdown(){stop();ex.shutdownNow();}
}
