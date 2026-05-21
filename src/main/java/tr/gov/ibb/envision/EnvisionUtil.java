package tr.gov.ibb.envision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * Ana giriş noktası.
 *
 * Kullanım (JAR):
 *   java -jar envision.jar                     → envision.html üretir ve tarayıcıda açar
 *   java -jar envision.jar output/env.html     → belirtilen yola yazar
 *   java -jar envision.jar --no-open           → dosya üretir ama tarayıcı açmaz
 *   java -jar envision.jar --unmasked          → hassas değerleri maskesiz göster
 *   java -jar envision.jar --pin=1234          → hassas kopyalama için PIN koruması ekler
 *
 * Kendi projenizden programatik kullanım:
 *   EnvisionUtil.generateHtml(Path.of("envision.html"), true);
 *   EnvisionUtil.generateHtml(Path.of("envision.html"), true, "1234");
 */
public class EnvisionUtil {

    public static void main(String[] args) throws Exception {
        Path outputPath = Path.of("envision.html");
        boolean openBrowser = true;
        boolean masked = true;
        String pin = "";

        for (String arg : args) {
            if (arg.equals("--no-open"))          openBrowser = false;
            else if (arg.equals("--unmasked"))    masked = false;
            else if (arg.startsWith("--pin="))    pin = arg.substring(6);
            else if (!arg.startsWith("--"))       outputPath = Path.of(arg);
        }

        generateHtml(outputPath, masked, pin);
        System.out.println("✔ envision.html oluşturuldu: " + outputPath.toAbsolutePath());
        if (!pin.isBlank()) System.out.println("🔒 PIN koruması aktif");

        if (openBrowser) {
            openInBrowser(outputPath);
            System.out.println("✔ Tarayıcı açılıyor…");
        }
    }

    /** PIN koruması olmadan üretir. */
    public static void generateHtml(Path outputPath, boolean maskSensitive) throws IOException {
        generateHtml(outputPath, maskSensitive, "");
    }

    /**
     * Programatik API: env variable'ları okuyup HTML dosyası üretir.
     *
     * @param outputPath    Çıktı dosyasının yolu (örn. Path.of("envision.html"))
     * @param maskSensitive true ise PASSWORD/SECRET gibi değerleri başlangıçta maskeler
     * @param pin           PIN koruması için parola; boş bırakılırsa koruma yok
     */
    public static void generateHtml(Path outputPath, boolean maskSensitive, String pin) throws IOException {
        List<EnvVariable> vars = new EnvReader().readAll();
        String html = new HtmlGenerator().generate(vars, maskSensitive, pin);
        Files.createDirectories(outputPath.getParent() == null ? Path.of(".") : outputPath.getParent());
        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
    }

    private static void openInBrowser(Path file) {
        try {
            String uri = file.toAbsolutePath().toUri().toString();
            String os = System.getProperty("os.name").toLowerCase();
            Runtime rt = Runtime.getRuntime();
            if (os.contains("win")) {
                rt.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", uri});
            } else if (os.contains("mac")) {
                rt.exec(new String[]{"open", uri});
            } else {
                rt.exec(new String[]{"xdg-open", uri});
            }
        } catch (Exception ignored) {}
    }
}
