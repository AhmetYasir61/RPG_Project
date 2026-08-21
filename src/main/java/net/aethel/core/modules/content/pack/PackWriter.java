package net.aethel.core.modules.content.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Uretilen pack agacini diske yazar, zip'ler ve SHA-1'ini hesaplar. Hash zip
 * yazilirken akis uzerinden hesaplanir; dosya ikinci kez okunmaz.
 */
public final class PackWriter {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** JSON dosyasini pack agacina yazar; ara klasorleri kendisi olusturur. */
    public void json(File target, JsonObject content) throws IOException {
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        Files.writeString(target.toPath(), gson.toJson(content), StandardCharsets.UTF_8);
    }

    public void bytes(File target, byte[] content) throws IOException {
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        Files.write(target.toPath(), content);
    }

    public void copy(Path source, File target) throws IOException {
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        Files.copy(source, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** pack.mcmeta; pack_format sunucu surumune gore secilir. */
    public void mcmeta(File packRoot, int packFormat, String description) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", packFormat);
        pack.addProperty("description", description);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        json(new File(packRoot, "pack.mcmeta"), root);
    }

    /**
     * Klasoru zip'ler ve SHA-1'i akis sirasinda hesaplar. Girdiler sirali yazilir;
     * ayni icerik her uretimde ayni hash'i verir, boylece degismeyen pack istemcide
     * yeniden indirilmez.
     */
    public String zip(File sourceDirectory, File target) throws IOException {
        MessageDigest digest = sha1();
        Path root = sourceDirectory.toPath();

        try (OutputStream fileStream = Files.newOutputStream(target.toPath());
             DigestOutputStream digestStream = new DigestOutputStream(fileStream, digest);
             ZipOutputStream zip = new ZipOutputStream(digestStream)) {

            try (var walk = Files.walk(root)) {
                var files = walk.filter(Files::isRegularFile).sorted().toList();
                for (Path path : files) {
                    ZipEntry entry = new ZipEntry(root.relativize(path).toString().replace('\\', '/'));
                    // Sabit zaman damgasi: ayni icerik daima ayni hash'i uretsin.
                    entry.setTime(0L);
                    zip.putNextEntry(entry);
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Kaynak dosyanin degisip degismedigini anlamak icin kullanilan hash. */
    public String hashOf(Path path) throws IOException {
        MessageDigest digest = sha1();
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }

    private MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 kullanilamiyor", e);
        }
    }

    /** Uretim oncesi eski agaci temizler; artik dosyalar pack'te kalmasin. */
    public void clean(File directory) throws IOException {
        if (!directory.exists()) return;
        try (var walk = Files.walk(directory.toPath())) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        directory.mkdirs();
    }
}
