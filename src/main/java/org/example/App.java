package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

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
    static final String PDF_NAME = "papercdcase.pdf";
    static final int TRACK_LIMIT = 16;

    public static void main(String[] args) throws Exception {
        String dir = System.getProperty("user.dir");

        System.setProperty("webdriver.chrome.driver",
            Paths.get(dir, "chromedriver-win64", "chromedriver.exe").toString());

        AlbumInfo album = loadAlbum(Paths.get(dir, "data", "data.txt"));
        System.out.println("Исполнитель: " + album.artist);
        System.out.println("Альбом: " + album.title);
        System.out.println("Треков: " + album.tracks.size());

        Path resultDir = Paths.get(dir, "result");
        Files.createDirectories(resultDir);

        Path pending = resultDir.resolve(PDF_NAME);
        Path output  = resultDir.resolve("cd.pdf");
        Files.deleteIfExists(pending);

        ChromeOptions opts = buildOptions(
            Paths.get(dir, "chrome-win64", "chrome.exe").toString(),
            resultDir.toString()
        );

        WebDriver driver = new ChromeDriver(opts);
        try {
            fillAndSubmit(driver, album);
            waitDownload(pending, resultDir, Duration.ofSeconds(60));
            Files.move(pending, output, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Готово: " + output + " (" + Files.size(output) + " байт)");
        } finally {
            driver.quit();
        }
    }

    static ChromeOptions buildOptions(String chromeBin, String downloadDir) {
        ChromeOptions opts = new ChromeOptions();
        opts.setBinary(chromeBin);
        opts.setAcceptInsecureCerts(true);
        opts.setPageLoadStrategy(PageLoadStrategy.NONE);
        opts.addArguments("--start-maximized");

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadDir);
        prefs.put("download.prompt_for_download", false);
        prefs.put("plugins.always_open_pdf_externally", true);
        opts.setExperimentalOption("prefs", prefs);
        return opts;
    }

    static void fillAndSubmit(WebDriver driver, AlbumInfo album) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        driver.get(SITE);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("artist")));

        driver.findElement(By.name("artist")).sendKeys(album.artist);
        driver.findElement(By.name("title")).sendKeys(album.title);

        int n = Math.min(album.tracks.size(), TRACK_LIMIT);
        for (int i = 0; i < n; i++) {
            driver.findElement(By.name("track" + (i + 1))).sendKeys(album.tracks.get(i));
        }

        driver.findElement(
            By.xpath("//input[@name='size' and @value='a4']")).click();
        driver.findElement(
            By.xpath("//input[@name='template' and @value='jewel']")).click();

        WebElement form = driver.findElement(By.xpath("//form"));
        form.submit();
    }

    static void waitDownload(Path file, Path dir, Duration timeout)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            boolean partial = false;
            try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "*.crdownload")) {
                partial = s.iterator().hasNext();
            }
            if (Files.exists(file) && Files.size(file) > 0 && !partial) return;
            Thread.sleep(500);
        }
        throw new IOException("Файл не скачан за " + timeout.getSeconds() + " с");
    }

    static AlbumInfo loadAlbum(Path file) throws IOException {
        AlbumInfo a = new AlbumInfo();
        try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String low = line.toLowerCase();
                if (low.startsWith("artist:")) {
                    a.artist = line.substring(line.indexOf(':') + 1).trim();
                } else if (low.startsWith("title:")) {
                    a.title = line.substring(line.indexOf(':') + 1).trim();
                } else {
                    a.tracks.add(line);
                }
            }
        }
        return a;
    }

    static class AlbumInfo {
        String artist = "";
        String title  = "";
        List<String> tracks = new ArrayList<>();
    }
}
