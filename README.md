# Envision — Kullanım Kılavuzu

## Ne yapar?

`System.getenv()` ile sistemdeki tüm environment variable'ları okur, modern bir HTML dosyasına gömer ve tarayıcıda açar. Sıfır harici bağımlılık.

---

## 1. Kendi projenin pom.xml'ine ekle

Önce local repo'ya yükle:
```bash
cd env-viewer
mvn install
```

Sonra bağımlılık olarak ekle:
```xml
<dependency>
    <groupId>com.envision</groupId>
    <artifactId>envision</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 2. Kodundan çağır

```java
import tr.gov.ibb.envision.EnvisionUtil;
import java.nio.file.Path;

// envision.html üretir (hassas değerler maskeli)
EnvisionUtil.generateHtml(Path.of("envision.html"), true);

// PIN korumalı — hassas değerleri kopyalamak için PIN gerekir
EnvisionUtil.generateHtml(Path.of("envision.html"), true, "1234");
```

---

## 3. IntelliJ'den çalıştır

1. `File → Open` → `env-viewer` klasörünü seç → **Trust Project**
2. Maven sync bekle (sağ üstteki bildirim)
3. `EnvisionUtil.java` → yeşil ▶ → **Run 'EnvisionUtil.main()'**

PIN ile çalıştırmak için: `Run → Edit Configurations → Program arguments`:
```
--pin=1234
```

---

## 4. JAR olarak çalıştır

```bash
mvn package

# Çalıştır → envision.html üretir + tarayıcı açar
java -jar target/envision-1.0.0.jar

# Özel çıktı yolu
java -jar target/envision-1.0.0.jar rapor/env.html

# PIN koruması
java -jar target/envision-1.0.0.jar --pin=1234

# Hassas değerleri açık göster
java -jar target/envision-1.0.0.jar --unmasked

# Tarayıcı otomatik açılmasın
java -jar target/envision-1.0.0.jar --no-open
```

---

## Arayüz özellikleri

- Anlık arama (key veya value üzerinde)
- Kategori filtreleme: JAVA, MAVEN, PATH, NODE, DOCKER, CLOUD, DATABASE, SYSTEM, OTHER
- Hassas maskeleme: PASSWORD, SECRET, TOKEN, KEY vb. `AB●●●●YZ` formatında gizlenir
- PIN koruması: hassas değerleri kopyalamak için SHA-256 doğrulamalı oturum kilidi
- Uzun değerleri tıkla genişlet / daralt
- JSON export butonu
- Dark / Light tema geçişi
