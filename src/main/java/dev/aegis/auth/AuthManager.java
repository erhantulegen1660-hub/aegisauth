package dev.aegis.auth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class AuthManager {

    public enum Stage {
        CAPTCHA,
        AUTH,
        DONE
    }

    private static class PlayerSession {
        Stage stage;
        String captchaCode;
        int failedAttempts;
        ItemStack[] savedContents;
        ItemStack[] savedArmor;
        ItemStack savedOffhand;
    }

    private final JavaPlugin plugin;
    private final PasswordStore passwordStore;
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    private final Set<UUID> teleportBypass = new HashSet<>();
    private final Random random = new Random();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public AuthManager(JavaPlugin plugin, PasswordStore passwordStore) {
        this.plugin = plugin;
        this.passwordStore = passwordStore;
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        ensureWorldsLoaded();
    }

    public void setCaptchaLocation(Location loc) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("captcha-zone.world", loc.getWorld().getName());
        cfg.set("captcha-zone.x", round2(loc.getX()));
        cfg.set("captcha-zone.y", round2(loc.getY()));
        cfg.set("captcha-zone.z", round2(loc.getZ()));
        cfg.set("captcha-zone.yaw", round2(loc.getYaw()));
        cfg.set("captcha-zone.pitch", round2(loc.getPitch()));
        plugin.saveConfig();
    }

    public void setSpawnLocation(Location loc) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("spawn.world", loc.getWorld().getName());
        cfg.set("spawn.x", round2(loc.getX()));
        cfg.set("spawn.y", round2(loc.getY()));
        cfg.set("spawn.z", round2(loc.getZ()));
        cfg.set("spawn.yaw", round2(loc.getYaw()));
        cfg.set("spawn.pitch", round2(loc.getPitch()));
        plugin.saveConfig();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public void ensureWorldsLoaded() {
        FileConfiguration cfg = plugin.getConfig();
        loadWorldIfExists(cfg.getString("captcha-zone.world", "auth"));
        loadWorldIfExists(cfg.getString("spawn.world", "world"));
    }

    private World loadWorldIfExists(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            return existing;
        }
        // Если установлен Multiverse-Core, даём ему управлять загрузкой миров
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
            return null;
        }
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists() && worldFolder.isDirectory() && new File(worldFolder, "level.dat").exists()) {
            return Bukkit.createWorld(new WorldCreator(worldName));
        }
        return null;
    }

    public Location getCaptchaLocation(Player targetForMessages) {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("captcha-zone.world", "auth");
        World world = loadWorldIfExists(worldName);
        if (world == null) {
            if (targetForMessages != null) {
                sendMsg(targetForMessages, msg("world-not-found").replace("%world%", worldName));
            }
            return null;
        }
        return new Location(
                world,
                cfg.getDouble("captcha-zone.x", 0.5),
                cfg.getDouble("captcha-zone.y", 65.0),
                cfg.getDouble("captcha-zone.z", 0.5),
                (float) cfg.getDouble("captcha-zone.yaw", 0.0),
                (float) cfg.getDouble("captcha-zone.pitch", 0.0)
        );
    }

    public Location getSpawnLocation(Player targetForMessages) {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("spawn.world", "world");
        World world = loadWorldIfExists(worldName);
        if (world == null) {
            if (targetForMessages != null) {
                sendMsg(targetForMessages, msg("world-not-found").replace("%world%", worldName));
            }
            return null;
        }
        return new Location(
                world,
                cfg.getDouble("spawn.x", 0.5),
                cfg.getDouble("spawn.y", 80.0),
                cfg.getDouble("spawn.z", 0.5),
                (float) cfg.getDouble("spawn.yaw", 0.0),
                (float) cfg.getDouble("spawn.pitch", 0.0)
        );
    }

    public double getMaxCaptchaDistance() {
        return Math.max(0.5, plugin.getConfig().getDouble("captcha-zone.max-distance-from-spawn", 2.5));
    }

    public void startAuthFlow(Player player) {
        PlayerSession session = new PlayerSession();
        session.stage = Stage.CAPTCHA;
        session.failedAttempts = 0;

        if (plugin.getConfig().getBoolean("settings.keep-inventory-backup", true)) {
            PlayerInventory inv = player.getInventory();
            session.savedContents = cloneArray(inv.getContents());
            session.savedArmor = cloneArray(inv.getArmorContents());
            session.savedOffhand = inv.getItemInOffHand() != null ? inv.getItemInOffHand().clone() : null;
        }

        sessions.put(player.getUniqueId(), session);

        Location capLoc = getCaptchaLocation(player);
        if (capLoc != null) {
            safeTeleport(player, capLoc);
        }

        giveCaptchaMap(player, session);
        sendMsg(player, msg("step-captcha"));
    }

    public void giveCaptchaMap(Player player) {
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null || session.stage != Stage.CAPTCHA) {
            return;
        }
        giveCaptchaMap(player, session);
    }

    private void giveCaptchaMap(Player player, PlayerSession session) {
        int len = Math.max(4, Math.min(6, plugin.getConfig().getInt("captcha.length", 5)));
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(random.nextInt(10));
        }
        session.captchaCode = sb.toString();

        World world = player.getWorld();
        MapView view = Bukkit.createMap(world);
        view.setScale(MapView.Scale.CLOSEST);
        view.setTrackingPosition(false);
        for (MapRenderer renderer : view.getRenderers()) {
            view.removeRenderer(renderer);
        }

        int noiseLines = plugin.getConfig().getInt("captcha.noise-lines", 8);
        int noiseDots = plugin.getConfig().getInt("captcha.noise-dots", 140);
        int maxTilt = plugin.getConfig().getInt("captcha.max-tilt-degrees", 12);
        view.addRenderer(new CaptchaRenderer(session.captchaCode, noiseLines, noiseDots, maxTilt));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            meta.displayName(legacy.deserialize("&5✦ &dКапча &7| &fВведи цифры в чат"));
            mapItem.setItemMeta(meta);
        }

        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);
        inv.setHeldItemSlot(0);
        inv.setItem(0, mapItem);
        player.updateInventory();
    }

    public boolean isAuthenticated(Player player) {
        PlayerSession s = sessions.get(player.getUniqueId());
        return s == null || s.stage == Stage.DONE;
    }

    public Stage getStage(Player player) {
        PlayerSession s = sessions.get(player.getUniqueId());
        return s == null ? Stage.DONE : s.stage;
    }

    public boolean isBypassingTeleport(UUID uuid) {
        return teleportBypass.contains(uuid);
    }

    public void handleCaptchaAttempt(Player player, String input) {
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null || session.stage != Stage.CAPTCHA) {
            return;
        }
        if (session.captchaCode.equals(input.trim())) {
            session.stage = Stage.AUTH;
            player.getInventory().clear();
            player.updateInventory();

            if (passwordStore.isRegistered(player.getName())) {
                sendMsg(player, msg("step-login"));
            } else {
                sendMsg(player, msg("step-register"));
            }
        } else {
            sendMsg(player, msg("captcha-wrong"));
        }
    }

    public void handleRegister(Player player, String pass1, String pass2) {
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null || session.stage == Stage.DONE) {
            sendMsg(player, msg("already-authed"));
            return;
        }
        if (session.stage == Stage.CAPTCHA) {
            sendMsg(player, msg("step-captcha"));
            return;
        }
        if (passwordStore.isRegistered(player.getName())) {
            sendMsg(player, msg("already-registered"));
            return;
        }
        if (!pass1.equals(pass2)) {
            sendMsg(player, msg("password-mismatch"));
            return;
        }
        int min = plugin.getConfig().getInt("settings.min-password-length", 5);
        int max = plugin.getConfig().getInt("settings.max-password-length", 32);
        if (pass1.length() < min || pass1.length() > max) {
            sendMsg(player, msg("password-length")
                    .replace("%min%", String.valueOf(min))
                    .replace("%max%", String.valueOf(max)));
            return;
        }

        passwordStore.register(player.getName(), pass1);
        completeAuthAndTeleport(player, session, msg("register-success"));
    }

    public void handleLogin(Player player, String password) {
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null || session.stage == Stage.DONE) {
            sendMsg(player, msg("already-authed"));
            return;
        }
        if (session.stage == Stage.CAPTCHA) {
            sendMsg(player, msg("step-captcha"));
            return;
        }
        if (!passwordStore.isRegistered(player.getName())) {
            sendMsg(player, msg("not-registered"));
            return;
        }

        if (passwordStore.verify(player.getName(), password)) {
            completeAuthAndTeleport(player, session, msg("login-success"));
        } else {
            session.failedAttempts++;
            int maxAttempts = plugin.getConfig().getInt("settings.max-login-attempts", 5);
            int left = Math.max(0, maxAttempts - session.failedAttempts);
            if (session.failedAttempts >= maxAttempts) {
                player.kick(legacy.deserialize(msg("kick-wrong-password")));
            } else {
                sendMsg(player, msg("wrong-password").replace("%left%", String.valueOf(left)));
            }
        }
    }

    private void completeAuthAndTeleport(Player player, PlayerSession session, String successText) {
        session.stage = Stage.DONE;
        restoreInventory(player, session);
        sessions.remove(player.getUniqueId());

        sendMsg(player, successText);

        Location spawnLoc = getSpawnLocation(player);
        if (spawnLoc != null) {
            safeTeleport(player, spawnLoc);
        }
    }

    private void restoreInventory(Player player, PlayerSession session) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        if (plugin.getConfig().getBoolean("settings.restore-inventory-on-success", true)
                && session.savedContents != null) {
            inv.setContents(session.savedContents);
            if (session.savedArmor != null) {
                inv.setArmorContents(session.savedArmor);
            }
            if (session.savedOffhand != null) {
                inv.setItemInOffHand(session.savedOffhand);
            }
        }
        player.updateInventory();
    }

    public void safeTeleport(Player player, Location loc) {
        teleportBypass.add(player.getUniqueId());
        try {
            player.teleport(loc);
        } finally {
            Bukkit.getScheduler().runTaskLater(plugin, () -> teleportBypass.remove(player.getUniqueId()), 2L);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        sessions.remove(uuid);
        teleportBypass.remove(uuid);
    }

    public void cleanupAll() {
        sessions.clear();
        teleportBypass.clear();
    }

    public String msg(String path) {
        return plugin.getConfig().getString("messages." + path, "");
    }

    public void sendMsg(Player player, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Component full = legacy.deserialize(msg("prefix") + text);
        player.sendMessage(full);
    }

    private ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null) {
            return null;
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? source[i].clone() : null;
        }
        return copy;
    }
}
