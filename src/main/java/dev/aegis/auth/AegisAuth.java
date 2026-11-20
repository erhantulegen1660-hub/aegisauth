package dev.aegis.auth;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class AegisAuth extends JavaPlugin {

    private PasswordStore passwordStore;
    private AuthManager authManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.passwordStore = new PasswordStore(this);
        this.passwordStore.load();

        this.authManager = new AuthManager(this, passwordStore);
        Bukkit.getScheduler().runTask(this, () -> authManager.ensureWorldsLoaded());

        AuthCommands commands = new AuthCommands(authManager);
        if (getCommand("register") != null) getCommand("register").setExecutor(commands);
        if (getCommand("login") != null) getCommand("login").setExecutor(commands);
        if (getCommand("captcha") != null) getCommand("captcha").setExecutor(commands);
        if (getCommand("aegisauth") != null) getCommand("aegisauth").setExecutor(commands);

        Bukkit.getPluginManager().registerEvents(new AuthListener(this, authManager), this);
        getLogger().info("AegisAuth успешно запущен.");
    }

    @Override
    public void onDisable() {
        if (authManager != null) {
            authManager.cleanupAll();
        }
        if (passwordStore != null) {
            passwordStore.save();
        }
    }
}
