package tr.gov.ibb.envision;

import java.util.regex.Pattern;

/**
 * Tek bir environment variable'ı temsil eder.
 */
public record EnvVariable(String key, String value, String category, boolean sensitive) {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        "(?i).*(password|passwd|secret|token|key|api_key|auth|credential|private|cert|pkcs|ssl|jwt|bearer).*"
    );

    public static EnvVariable of(String key, String value) {
        boolean sensitive = SENSITIVE_PATTERN.matcher(key).matches();
        return new EnvVariable(key, value != null ? value : "", detectCategory(key), sensitive);
    }

    /** Hassas değerleri maskeler: ilk 2 + *** + son 2 karakter */
    public String maskedValue() {
        if (!sensitive || value.length() <= 4) return value.isEmpty() ? "(boş)" : value;
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    private static String detectCategory(String key) {
        if (key == null) return "OTHER";
        String u = key.toUpperCase();
        if (u.startsWith("JAVA_") || u.startsWith("JDK") || u.startsWith("JRE"))          return "JAVA";
        if (u.startsWith("MAVEN_") || u.equals("M2_HOME") || u.equals("M2"))              return "MAVEN";
        if (u.equals("PATH") || u.equals("PATHEXT") || u.startsWith("PATH="))             return "PATH";
        if (u.startsWith("PYTHON") || u.startsWith("PY"))                                  return "PYTHON";
        if (u.startsWith("NODE") || u.startsWith("NPM") || u.startsWith("NVM"))           return "NODE";
        if (u.startsWith("DOCKER") || u.startsWith("COMPOSE"))                            return "DOCKER";
        if (u.startsWith("AWS_") || u.startsWith("AZURE_") || u.startsWith("GCP_")
                || u.startsWith("GOOGLE_"))                                                 return "CLOUD";
        if (u.startsWith("DB_") || u.startsWith("DATABASE") || u.startsWith("POSTGRES")
                || u.startsWith("MYSQL") || u.startsWith("MONGO") || u.startsWith("REDIS")) return "DATABASE";
        if (u.startsWith("OS") || u.equals("USERNAME") || u.equals("USER")
                || u.equals("HOME") || u.equals("HOMEPATH") || u.equals("USERPROFILE")
                || u.startsWith("TEMP") || u.startsWith("TMP") || u.startsWith("PROCESSOR")
                || u.startsWith("COMPUTER") || u.startsWith("NUMBER_OF")
                || u.equals("WINDIR") || u.equals("SYSTEMROOT")
                || u.startsWith("PROGRAMFILES") || u.startsWith("SYSTEM"))                 return "SYSTEM";
        return "OTHER";
    }
}
