package com.example.addon.modules;
import com.example.addon.AddonTemplate;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.*;
import java.util.zip.*;
public class YouTubeExtractor {
    public record VideoInfo(String videoId,String title,Path audioFile){}
    private static final String YTDLP_URL_WIN="https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
    private static final String YTDLP_URL_LINUX="https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";
    private static final String YTDLP_URL_MAC="https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos";
    private static final String FFMPEG_URL_WIN="https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";
    private static final String USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private final Path workDir,ytDlpExe,ffmpegExe,cacheDir;
    private final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile boolean dlYtDlp=false,dlFfmpeg=false;
    private volatile long ytDlpBytes=0,ffmpegBytes=0;
    private volatile double ffmpegProgress=-1;
    private volatile boolean exportingCookies=false;
    private volatile String cookieExportStatus="";
    public long getYtDlpDownloadedBytes(){return ytDlpBytes;}
    public long getFfmpegDownloadedBytes(){return ffmpegBytes;}
    public double getFfmpegProgress(){return ffmpegProgress;}
    public boolean isExportingCookies(){return exportingCookies;}
    public String getCookieExportStatus(){return cookieExportStatus;}
    public boolean hasCookies(){return Files.exists(workDir.resolve("cookies.txt"));}
    public Path getCookiesPath(){return workDir.resolve("cookies.txt");}
    public YouTubeExtractor(Path gameDir){
        workDir=gameDir.resolve("yt-music");
        ytDlpExe=workDir.resolve(isWindows()?"yt-dlp.exe":"yt-dlp");
        ffmpegExe=workDir.resolve(isWindows()?"ffmpeg.exe":"ffmpeg");
        cacheDir=workDir.resolve("cache");
        try{Files.createDirectories(workDir);Files.createDirectories(cacheDir);}
        catch(Exception e){AddonTemplate.LOG.error("Failed to create yt-music dirs",e);}}
    public boolean isYtDlpPresent(){return exists(ytDlpExe);}
    public boolean isFfmpegPresent(){return exists(ffmpegExe);}
    public boolean isReady(){return isYtDlpPresent()&&isFfmpegPresent();}
    public boolean isDownloadingYtDlp(){return dlYtDlp;}
    public boolean isDownloadingFfmpeg(){return dlFfmpeg;}
    public CompletableFuture<Boolean> exportCookiesFromChrome(){
        exportingCookies=true;cookieExportStatus="Exporting cookies from Chrome...";
        return CompletableFuture.supplyAsync(()->{
            try{
                if(!isYtDlpPresent()){cookieExportStatus="yt-dlp not found.";exportingCookies=false;return false;}
                Path cookiesOut=workDir.resolve("cookies.txt");
                List<String> cmd=new ArrayList<>(List.of(
                    ytDlpExe.toAbsolutePath().toString(),
                    "--cookies-from-browser","chrome",
                    "--no-cache-dir",
                    "--cookies",cookiesOut.toAbsolutePath().toString(),
                    "--skip-download",
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
                ));
                AddonTemplate.LOG.info("Exporting Chrome cookies...");
                ProcessBuilder pb=new ProcessBuilder(cmd);pb.directory(workDir.toFile());pb.redirectErrorStream(true);
                Process proc=pb.start();StringBuilder out=new StringBuilder();
                try(BufferedReader br=new BufferedReader(new InputStreamReader(proc.getInputStream()))){String line;while((line=br.readLine())!=null){AddonTemplate.LOG.info("[cookie-export] {}",line);out.append(line).append("\n");}}
                int exit=proc.waitFor();exportingCookies=false;
                if(exit==0&&Files.exists(cookiesOut)&&cookiesOut.toFile().length()>0){cookieExportStatus="Cookies exported!";AddonTemplate.LOG.info("Cookies exported to {}",cookiesOut);return true;}
                cookieExportStatus="Export failed. Close Chrome first, then retry.";
                AddonTemplate.LOG.warn("Cookie export failed: {}",out);return false;
            }catch(Exception e){exportingCookies=false;cookieExportStatus="Error: "+e.getMessage();AddonTemplate.LOG.error("Cookie export error",e);return false;}});}
    public CompletableFuture<Boolean> downloadYtDlp(){
        if(isYtDlpPresent())return CompletableFuture.completedFuture(true);
        dlYtDlp=true;ytDlpBytes=0;
        String url=isWindows()?YTDLP_URL_WIN:isMac()?YTDLP_URL_MAC:YTDLP_URL_LINUX;
        AddonTemplate.LOG.info("Downloading yt-dlp from {}",url);
        return http.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent",USER_AGENT).GET().build(),HttpResponse.BodyHandlers.ofFile(ytDlpExe))
            .thenApply(resp->{dlYtDlp=false;if(resp.statusCode()==200){ytDlpExe.toFile().setExecutable(true);AddonTemplate.LOG.info("yt-dlp ready");return true;}AddonTemplate.LOG.error("yt-dlp download failed: HTTP {}",resp.statusCode());return false;})
            .exceptionally(e->{dlYtDlp=false;AddonTemplate.LOG.error("yt-dlp download error",e);return false;});}
    public CompletableFuture<Boolean> downloadFfmpeg(){
        if(isFfmpegPresent())return CompletableFuture.completedFuture(true);
        if(!isWindows()){AddonTemplate.LOG.warn("Auto ffmpeg download only supported on Windows.");return CompletableFuture.completedFuture(false);}
        dlFfmpeg=true;ffmpegBytes=0;ffmpegProgress=-1;
        Path zipPath=workDir.resolve("ffmpeg.zip");
        AddonTemplate.LOG.info("Downloading ffmpeg...");
        return http.sendAsync(HttpRequest.newBuilder().uri(URI.create(FFMPEG_URL_WIN)).header("User-Agent",USER_AGENT).GET().build(),HttpResponse.BodyHandlers.ofFile(zipPath))
            .thenApply(resp->{dlFfmpeg=false;if(resp.statusCode()!=200){AddonTemplate.LOG.error("ffmpeg download failed: HTTP {}",resp.statusCode());return false;}
                try{try(ZipInputStream zis=new ZipInputStream(new FileInputStream(zipPath.toFile()))){ZipEntry entry;while((entry=zis.getNextEntry())!=null){if(entry.getName().endsWith("/ffmpeg.exe")){Files.copy(zis,ffmpegExe,StandardCopyOption.REPLACE_EXISTING);ffmpegExe.toFile().setExecutable(true);AddonTemplate.LOG.info("ffmpeg extracted");break;}}}
                    Files.deleteIfExists(zipPath);return Files.exists(ffmpegExe);}catch(Exception e){AddonTemplate.LOG.error("ffmpeg extraction failed",e);return false;}})
            .exceptionally(e->{dlFfmpeg=false;AddonTemplate.LOG.error("ffmpeg download error",e);return false;});}
    public CompletableFuture<VideoInfo> extractWithProgress(String url,BiConsumer<Long,Long> progress){
        return CompletableFuture.supplyAsync(()->{
            try{
                if(!isYtDlpPresent())throw new RuntimeException("yt-dlp not found");
                if(!isFfmpegPresent())throw new RuntimeException("ffmpeg not found");
                String videoId=parseVideoId(url);
                if(videoId==null)throw new RuntimeException("Cannot parse video ID from: "+url);
                Path outFile=cacheDir.resolve(videoId+".mp3");
                if(exists(outFile)){String t=readTitle(videoId);AddonTemplate.LOG.info("Cache hit: {}",t);if(progress!=null)progress.accept(1L,1L);return new VideoInfo(videoId,t,outFile);}
                Path cookiesFile=workDir.resolve("cookies.txt");
                boolean useCookies=Files.exists(cookiesFile)&&cookiesFile.toFile().length()>0;
                List<String> cmd=new ArrayList<>();
                cmd.add(ytDlpExe.toAbsolutePath().toString());
                if(useCookies){cmd.add("--cookies");cmd.add(cookiesFile.toAbsolutePath().toString());AddonTemplate.LOG.info("Using cookies.txt");}
                cmd.addAll(List.of(
                    "--extractor-args","youtube:player_client=web",
                    "--js-runtimes","none",
                    "-x",
                    "--audio-format","mp3",
                    "--audio-quality","0",
                    "--no-playlist",
                    "--newline",
                    "--ffmpeg-location",ffmpegExe.toAbsolutePath().toString(),
                    "--print","before_dl:title",
                    "-o",cacheDir.resolve(videoId+".%(ext)s").toAbsolutePath().toString(),
                    url));
                AddonTemplate.LOG.info("Downloading audio for {}",videoId);
                ProcessBuilder pb=new ProcessBuilder(cmd);pb.directory(workDir.toFile());pb.redirectErrorStream(true);
                Process proc=pb.start();StringBuilder out=new StringBuilder();
                String title=videoId;boolean gotTitle=false;
                Pattern dlPat=Pattern.compile("\\[download\\]\\s+([\\d.]+)%\\s+of\\s+~?([\\d.]+)(\\w+)");
                Pattern dlPat2=Pattern.compile("\\[download\\]\\s+([\\d.]+)%");
                try(BufferedReader br=new BufferedReader(new InputStreamReader(proc.getInputStream()))){String line;while((line=br.readLine())!=null){AddonTemplate.LOG.info("[yt-dlp] {}",line);out.append(line).append("\n");if(!gotTitle&&!line.startsWith("[")&&!line.startsWith("WARNING")&&!line.isBlank()){title=line.trim();gotTitle=true;}if(progress!=null){Matcher m=dlPat.matcher(line);if(m.find()){double pct=Double.parseDouble(m.group(1));double sz=Double.parseDouble(m.group(2));String unit=m.group(3);long tb=toBytes(sz,unit);long db=(long)(pct/100.0*tb);progress.accept(db,tb);}else{Matcher m2=dlPat2.matcher(line);if(m2.find()){double pct=Double.parseDouble(m2.group(1));progress.accept((long)pct,100L);}}}}}
                int exit=proc.waitFor();
                if(exit!=0)throw new RuntimeException("yt-dlp failed. "+(useCookies?"":"No cookies found — export cookies.txt to .minecraft/yt-music/")+"\n"+out);
                if(!exists(outFile))throw new RuntimeException("Output mp3 not found at "+outFile);
                Files.writeString(cacheDir.resolve(videoId+".title"),title);
                if(progress!=null)progress.accept(1L,1L);
                AddonTemplate.LOG.info("Done: '{}' -> {}",title,outFile);
                return new VideoInfo(videoId,title,outFile);
            }catch(Exception e){AddonTemplate.LOG.error("Extraction failed: {}",e.getMessage());throw new RuntimeException(e);}});}
    public CompletableFuture<VideoInfo> extract(String url){return extractWithProgress(url,null);}
    private long toBytes(double val,String unit){return switch(unit.toLowerCase()){case "kib","kb"->(long)(val*1024);case "mib","mb"->(long)(val*1024*1024);case "gib","gb"->(long)(val*1024*1024*1024);default->(long)val;};}
    private String readTitle(String videoId){try{Path f=cacheDir.resolve(videoId+".title");if(Files.exists(f))return Files.readString(f).trim();}catch(Exception ignored){}return videoId;}
    private String parseVideoId(String url){
        if(url==null||url.isBlank())return null;url=url.trim();
        for(Pattern p:new Pattern[]{Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})"),Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})"),Pattern.compile("youtube\\.com/embed/([a-zA-Z0-9_-]{11})"),Pattern.compile("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})")}){Matcher m=p.matcher(url);if(m.find())return m.group(1);}
        if(url.matches("[a-zA-Z0-9_-]{11}"))return url;return null;}
    public long getCacheSizeMB(){try{return Files.walk(cacheDir).filter(Files::isRegularFile).mapToLong(p->p.toFile().length()).sum()/(1024*1024);}catch(Exception e){return 0;}}
    public void clearCache(){try{Files.walk(cacheDir).sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);Files.createDirectories(cacheDir);AddonTemplate.LOG.info("Cache cleared.");}catch(Exception e){AddonTemplate.LOG.error("Failed to clear cache",e);}}
    private boolean exists(Path p){return Files.exists(p)&&p.toFile().length()>0;}
    private boolean isWindows(){return System.getProperty("os.name").toLowerCase().contains("win");}
    private boolean isMac(){return System.getProperty("os.name").toLowerCase().contains("mac");}
}
