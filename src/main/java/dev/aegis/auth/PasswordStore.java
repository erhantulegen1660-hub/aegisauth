package dev.aegis.auth;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PasswordStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, String> hashes = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public PasswordStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "users.yml");
    }

    public void load() {
        hashes.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (cfg.isConfigurationSection("users")) {
            for (String key : cfg.getConfigurationSection("users").getKeys(false)) {
                String stored = cfg.getString("users." + key);
                if (stored != null && !stored.isBlank()) {
                    hashes.put(key.toLowerCase(Locale.ROOT), stored);
                }
            }
        }
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            cfg.set("users." + entry.getKey(), entry.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить users.yml: " + e.getMessage());
        }
    }

    public boolean isRegistered(String playerName) {
        return hashes.containsKey(playerName.toLowerCase(Locale.ROOT));
    }

    public void register(String playerName, String rawPassword) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String hash = pbkdf2(rawPassword, salt);
        String encoded = Base64.getEncoder().encodeToString(salt) + ":" + hash;
        hashes.put(playerName.toLowerCase(Locale.ROOT), encoded);
        save();
    }

    public boolean verify(String playerName, String rawPassword) {
        String stored = hashes.get(playerName.toLowerCase(Locale.ROOT));
        if (stored == null || !stored.contains(":")) {
            return false;
        }
        String[] parts = stored.split(":", 2);
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        String expectedHash = parts[1];
        String actualHash = pbkdf2(rawPassword, salt);
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String pbkdf2(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] bytes = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Ошибка хеширования пароля", e);
        }
    }
}
