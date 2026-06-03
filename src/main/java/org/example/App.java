package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/**
 * ST-8: заполняет форму на www.papercdcase.com через Selenium
 * и сохраняет сгенерированный PDF в result/cd.pdf.
 */
public class App {

    private static final int    MAX_TRACKS      = 16;
    private static final String BASE_URL         = "http://www.papercdcase.com";
    private static final String DOWNLOADED_NAME  = "papercdcase.pdf";

    public static void main(String[] args) throws Exception {
        String projectDir = System.getProperty("user.dir");

        String chromeBin    = Paths.get(projectDir, "chrome-win64",      "chrome.exe").toString();
        String chromeDriver = Paths.get(projectDir, "chromedriver-win64", "chromedriver.exe").toString();
        System.setProperty("webdriver.chrome.driver", chromeDriver);

        CoverData data = readData(Paths.get(projectDir, "data", "data.txt"));
        System.out.println("Artist : " + data.artist);
        System.out.println("Title  : " + data.title);
        System.out.println("Tracks : " + data.tracks.size());

        Path resultDir  = Paths.get(projectDir, "result");
        Files.createDirectories(resultDir);
        Path downloaded = resultDir.resolve(DOWNLOADED_NAME);
        Path target     = resultDir.resolve("cd.pdf");
        Files.deleteIfExists(downloaded);

        ChromeOptions options = new ChromeOptions();
        options.setBinary(chromeBin);
        options.setAcceptInsecureCerts(true);
        options.setPageLoadStrategy(PageLoadStrategy.NONE);
        options.addArguments("--start-maximized");

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory",       resultDir.toString());
        prefs.put("download.prompt_for_download",     false);
        prefs.put("plugins.always_open_pdf_externally", true);
        options.setExperimentalOption("prefs", prefs);

        WebDriver driver = new ChromeDriver(options);
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            driver.get(BASE_URL);

            wait.until(ExpectedConditions.presenceOfElementLocated(By.name("artist")));
            driver.findElement(By.name("artist")).sendKeys(data.artist);
            driver.findElement(By.name("title")).sendKeys(data.title);

            int count = Math.min(data.tracks.size(), MAX_TRACKS);
            for (int i = 0; i < count; i++) {
                WebElement field = driver.findElement(By.name("track" + (i + 1)));
                field.sendKeys(data.tracks.get(i));
            }

            driver.findElement(By.xpath("//input[@name='size' and @value='a4']")).click();
            driver.findElement(By.xpath("//input[@name='template' and @value='jewel']")).click();

            WebElement form = driver.findElement(By.xpath("//form"));
            form.submit();

            waitForDownload(downloaded, resultDir, Duration.ofSeconds(60));

            Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Saved: " + target + " (" + Files.size(target) + " bytes)");
        } finally {
            driver.quit();
        }
    }

    // ===== Внутренние классы / методы =====

    private static class CoverData {
        String artist = "";
        String title  = "";
        List<String> tracks = new ArrayList<>();
    }

    /**
     * Формат data.txt:
     *   Artist: <имя>
     *   Title: <название>
     *   Остальные непустые строки — треки (по одному в строке).
     */
    private static CoverData readData(Path file) throws IOException {
        CoverData data = new CoverData();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String lower = line.toLowerCase();
            if (lower.startsWith("artist:")) {
                data.artist = line.substring(line.indexOf(':') + 1).trim();
            } else if (lower.startsWith("title:")) {
                data.title = line.substring(line.indexOf(':') + 1).trim();
            } else {
                data.tracks.add(line);
            }
        }
        return data;
    }

    /** Ждёт, пока файл полностью скачается (нет .crdownload). */
    private static void waitForDownload(Path file, Path dir, Duration timeout)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            boolean hasPartial = false;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.crdownload")) {
                hasPartial = stream.iterator().hasNext();
            }
            if (Files.exists(file) && Files.size(file) > 0 && !hasPartial) return;
            Thread.sleep(500);
        }
        throw new IOException("PDF не скачан за " + timeout.getSeconds() + " с");
    }
}
