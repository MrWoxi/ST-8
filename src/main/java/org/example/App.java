package com.mycompany.app;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App {

    static final String SITE = "http://www.papercdcase.com";
    static final int TRACK_LIMIT = 16;

    public static void main(String[] args) throws Exception {
        String dir = System.getProperty("user.dir");
        Path driverPath = Paths.get(dir, "chromedriver.exe");
        if (!Files.exists(driverPath)) {
            driverPath = Paths.get(dir, "chromedriver-win64", "chromedriver.exe");
        }
        if (!Files.exists(driverPath)) {
            System.err.println("chromedriver.exe не найден");
            return;
        }
        System.setProperty("webdriver.chrome.driver", driverPath.toString());
        System.out.println("Драйвер: " + driverPath);

        AlbumInfo album = loadAlbum(Paths.get(dir, "data", "data.txt"));
        System.out.println("Исполнитель: " + album.artist);
        System.out.println("Альбом: " + album.title);
        System.out.println("Треков: " + album.tracks.size());

        Path resultDir = Paths.get(dir, "result");
        Files.createDirectories(resultDir);
        Path targetPdf = resultDir.resolve("cd.pdf");
        Files.deleteIfExists(targetPdf);

        ChromeOptions opts = buildOptions(resultDir.toString());
        WebDriver driver = new ChromeDriver(opts);
        try {
            fillAndSubmit(driver, album);
            waitDownload(resultDir, Duration.ofSeconds(60));
            renameDownloadedPdf(resultDir, targetPdf);
            System.out.println("Готово: " + targetPdf + " (" + Files.size(targetPdf) + " байт)");
        } finally {
            driver.quit();
        }
    }

    static ChromeOptions buildOptions(String downloadDir) {
        ChromeOptions opts = new ChromeOptions();
        opts.setAcceptInsecureCerts(true);
        opts.addArguments("--start-maximized");
        opts.addArguments("--ignore-certificate-errors");

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadDir);
        prefs.put("download.prompt_for_download", false);
        prefs.put("plugins.always_open_pdf_externally", true);
        opts.setExperimentalOption("prefs", prefs);
        return opts;
    }

    static void fillAndSubmit(WebDriver driver, AlbumInfo album) throws InterruptedException {
        driver.get(SITE);
        Thread.sleep(2000);

        driver.findElement(By.name("artist")).sendKeys(album.artist);
        driver.findElement(By.name("title")).sendKeys(album.title);

        int n = Math.min(album.tracks.size(), TRACK_LIMIT);
        for (int i = 0; i < n; i++) {
            driver.findElement(By.name("track" + (i + 1))).sendKeys(album.tracks.get(i));
        }

        driver.findElement(By.xpath("//input[@name='size' and @value='a4']")).click();
        driver.findElement(By.xpath("//input[@name='template' and @value='jewel']")).click();
        driver.findElement(By.name("submit")).click();
    }

    static void waitDownload(Path dir, Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            boolean partial = false;
            try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "*.crdownload")) {
                partial = s.iterator().hasNext();
            }
            boolean hasPdf = false;
            try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "*.pdf")) {
                hasPdf = s.iterator().hasNext();
            }
            if (hasPdf && !partial) return;
            Thread.sleep(500);
        }
        throw new IOException("PDF не скачался за " + timeout.getSeconds() + " сек");
    }

    static void renameDownloadedPdf(Path dir, Path target) throws IOException {
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "*.pdf")) {
            for (Path p : s) {
                if (!p.getFileName().toString().equals(target.getFileName().toString())) {
                    Files.move(p, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException("Не найден скачаный PDF файл");
    }

    static AlbumInfo loadAlbum(Path file) throws IOException {
        AlbumInfo a = new AlbumInfo();
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) lines.add(line);
            }
        }
        if (lines.size() < 3) {
            throw new IOException("Файл data.txt должен содержать исполнителя, название и хотя бы один трек");
        }

        String artistRaw = lines.get(0);
        if (artistRaw.toLowerCase().startsWith("artist:")) {
            artistRaw = artistRaw.substring(artistRaw.indexOf(':') + 1).trim();
        }
        String titleRaw = lines.get(1);
        if (titleRaw.toLowerCase().startsWith("title:")) {
            titleRaw = titleRaw.substring(titleRaw.indexOf(':') + 1).trim();
        }
        a.artist = artistRaw;
        a.title = titleRaw;
        a.tracks = lines.subList(2, Math.min(lines.size(), TRACK_LIMIT + 2));
        return a;
    }

    static class AlbumInfo {
        String artist = "";
        String title  = "";
        List<String> tracks = new ArrayList<>();
    }
}