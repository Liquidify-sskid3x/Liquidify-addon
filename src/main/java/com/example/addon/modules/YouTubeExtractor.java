package com.example.addon.modules;

import com.example.addon.AddonTemplate;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.*;
import java.util.zip.*;

public class YouTubeExtractor {
    private volatile long ytDlpBytes=0;
    private volatile long ffmpegBytes=0;
    private volatile double ffmpegProgress=-1;
    public record VideoInfo(String videoId, String title, Path audioFile) {}

    private static final String YTDLP_URL_WIN   = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
    private static final String YTDLP_URL_LINUX = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";
    private static final String YTDLP_URL_MAC   = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos";
    public long getYtDlpDownloadedBytes(){return ytDlpBytes;}
    public long getFfmpegDownloadedBytes(){return ffmpegBytes;}
    public double getFfmpegProgress(){return ffmpegProgress;}
    private static final String FFMPEG_URL_WIN  = "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";

    private final Path   workDir;
    private final Path   ytDlpExe;
    private final Path   ffmpegExe;
    private final Path   cacheDir;
    private final HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private volatile boolean downloadingYtDlp  = false;
    private volatile boolean downloadingFfmpeg = false;

    public YouTubeExtractor(Path gameDir) {
        workDir   = gameDir.resolve("yt-music");
        ytDlpExe  = workDir.resolve(isWindows() ? "yt-dlp.exe" : "yt-dlp");
        ffmpegExe = workDir.resolve(isWindows() ? "ffmpeg.exe" : "ffmpeg");
        cacheDir  = workDir.resolve("cache");
        try {
            Files.createDirectories(workDir);
            Files.createDirectories(cacheDir);
        } catch (Exception e) {
            AddonTemplate.LOG.error("Failed to create yt-music dirs", e);
        }
    }

    public boolean isYtDlpPresent()  { return exists(ytDlpExe); }
    public boolean isFfmpegPresent()  { return exists(ffmpegExe); }
    public boolean isReady()          { return isYtDlpPresent() && isFfmpegPresent(); }
    public boolean isDownloadingYtDlp()  { return downloadingYtDlp; }
    public boolean isDownloadingFfmpeg() { return downloadingFfmpeg; }

    public CompletableFuture<Boolean> downloadYtDlp() {
        if (isYtDlpPresent()) return CompletableFuture.completedFuture(true);
        downloadingYtDlp = true;
        String url = isWindows() ? YTDLP_URL_WIN : isMac() ? YTDLP_URL_MAC : YTDLP_URL_LINUX;
        AddonTemplate.LOG.info("Downloading yt-dlp from {}", url);
        return http.sendAsync(
            HttpRequest.newBuilder().uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0").GET().build(),
            HttpResponse.BodyHandlers.ofFile(ytDlpExe)
        ).thenApply(resp -> {
            downloadingYtDlp = false;
            if (resp.statusCode() == 200) {
                ytDlpExe.toFile().setExecutable(true);
                AddonTemplate.LOG.info("yt-dlp ready at {}", ytDlpExe);
                return true;
            }
            AddonTemplate.LOG.error("yt-dlp download failed: HTTP {}", resp.statusCode());
            return false;
        }).exceptionally(e -> { downloadingYtDlp = false; AddonTemplate.LOG.error("yt-dlp download error", e); return false; });
    }
    public CompletableFuture<VideoInfo> extractWithProgress(String url, java.util.function.BiConsumer<Long,Long> progress){
        return extract(url).thenApply(v->{progress.accept(1L,1L);return v;});
    }
    public CompletableFuture<Boolean> downloadFfmpeg() {
        if (isFfmpegPresent()) return CompletableFuture.completedFuture(true);
        if (!isWindows()) {
            AddonTemplate.LOG.warn("Auto ffmpeg download only supported on Windows. Install ffmpeg manually.");
            return CompletableFuture.completedFuture(false);
        }
        downloadingFfmpeg = true;
        Path zipPath = workDir.resolve("ffmpeg.zip");
        AddonTemplate.LOG.info("Downloading ffmpeg...");
        return http.sendAsync(
            HttpRequest.newBuilder().uri(URI.create(FFMPEG_URL_WIN))
                .header("User-Agent", "Mozilla/5.0").GET().build(),
            HttpResponse.BodyHandlers.ofFile(zipPath)
        ).thenApply(resp -> {
            downloadingFfmpeg = false;
            if (resp.statusCode() != 200) {
                AddonTemplate.LOG.error("ffmpeg download failed: HTTP {}", resp.statusCode());
                return false;
            }
            try {
                // Extract ffmpeg.exe from the zip
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.getName().endsWith("/ffmpeg.exe")) {
                            Files.copy(zis, ffmpegExe, StandardCopyOption.REPLACE_EXISTING);
                            ffmpegExe.toFile().setExecutable(true);
                            AddonTemplate.LOG.info("ffmpeg extracted to {}", ffmpegExe);
                            break;
                        }
                    }
                }
                Files.deleteIfExists(zipPath);
                return Files.exists(ffmpegExe);
            } catch (Exception e) {
                AddonTemplate.LOG.error("ffmpeg extraction failed", e);
                return false;
            }
        }).exceptionally(e -> { downloadingFfmpeg = false; AddonTemplate.LOG.error("ffmpeg download error", e); return false; });
    }

    public CompletableFuture<VideoInfo> extract(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isYtDlpPresent())
                    throw new RuntimeException("yt-dlp not found");
                if (!isFfmpegPresent())
                    throw new RuntimeException("ffmpeg not found — click Download ffmpeg");

                String videoId = parseVideoId(url);
                if (videoId == null) throw new RuntimeException("Cannot parse video ID from: " + url);

                Path outFile = cacheDir.resolve(videoId + ".mp3");

                if (exists(outFile)) {
                    String title = readTitle(videoId);
                    AddonTemplate.LOG.info("Cache hit: {}", title);
                    return new VideoInfo(videoId, title, outFile);
                }

                AddonTemplate.LOG.info("Downloading audio for {}", videoId);
                ProcessBuilder pb = new ProcessBuilder(
                    ytDlpExe.toAbsolutePath().toString(),
                    "-x",
                    "--audio-format", "mp3",
                    "--audio-quality", "0",
                    "--no-playlist",
                    "--no-progress",
                    "--ffmpeg-location", ffmpegExe.toAbsolutePath().toString(),
                    "--print", "before_dl:title",
                    "-o", cacheDir.resolve(videoId + ".%(ext)s").toAbsolutePath().toString(),
                    url
                );
                pb.directory(workDir.toFile());
                pb.redirectErrorStream(true);

                Process proc = pb.start();
                StringBuilder out = new StringBuilder();
                String title = videoId;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    boolean gotTitle = false;
                    while ((line = br.readLine()) != null) {
                        AddonTemplate.LOG.info("[yt-dlp] {}", line);
                        out.append(line).append("\n");
                        if (!gotTitle && !line.startsWith("[") && !line.startsWith("WARNING") && !line.isBlank()) {
                            title = line.trim();
                            gotTitle = true;
                        }
                    }
                }

                int exit = proc.waitFor();
                if (exit != 0)
                    throw new RuntimeException("yt-dlp exited " + exit + "\n" + out);

                if (!exists(outFile))
                    throw new RuntimeException("Output mp3 not found at " + outFile);

                Files.writeString(cacheDir.resolve(videoId + ".title"), title);
                AddonTemplate.LOG.info("Done: '{}' -> {}", title, outFile);
                return new VideoInfo(videoId, title, outFile);

            } catch (Exception e) {
                AddonTemplate.LOG.error("Extraction failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private String readTitle(String videoId) {
        try {
            Path f = cacheDir.resolve(videoId + ".title");
            if (Files.exists(f)) return Files.readString(f).trim();
        } catch (Exception ignored) {}
        return videoId;
    }

    private String parseVideoId(String url) {
        if (url == null || url.isBlank()) return null;
        url = url.trim();
        for (Pattern p : new Pattern[]{
            Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})"),
            Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})"),
            Pattern.compile("youtube\\.com/embed/([a-zA-Z0-9_-]{11})"),
            Pattern.compile("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})")
        }) {
            Matcher m = p.matcher(url);
            if (m.find()) return m.group(1);
        }
        if (url.matches("[a-zA-Z0-9_-]{11}")) return url;
        return null;
    }


    public long getCacheSizeMB() {
        try {
            return java.nio.file.Files.walk(cacheDir)
                .filter(java.nio.file.Files::isRegularFile)
                .mapToLong(p -> p.toFile().length())
                .sum() / (1024 * 1024);
        } catch (Exception e) { return 0; }
    }

    public void clearCache() {
        try {
            java.nio.file.Files.walk(cacheDir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(java.io.File::delete);
            java.nio.file.Files.createDirectories(cacheDir);
            AddonTemplate.LOG.info("Cache cleared.");
        } catch (Exception e) {
            AddonTemplate.LOG.error("Failed to clear cache", e);
        }
    }
    private boolean exists(Path p) { return Files.exists(p) && p.toFile().length() > 0; }
    private boolean isWindows()    { return System.getProperty("os.name").toLowerCase().contains("win"); }
    private boolean isMac()        { return System.getProperty("os.name").toLowerCase().contains("mac"); }
}
