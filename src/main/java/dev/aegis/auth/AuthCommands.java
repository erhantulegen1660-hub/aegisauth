package dev.aegis.auth;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AuthCommands implements CommandExecutor {

    private final AuthManager authManager;

    public AuthCommands(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase();

        if (name.equals("aegisauth")) {
            if (!sender.hasPermission("aegisauth.admin")) {
                sender.sendMessage("§cНет прав.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                authManager.reloadConfig();
                sender.sendMessage("§8[§5✦ §dAEGIS§8] §aКонфиг AegisAuth перезагружен.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("setcaptcha")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Команда только для игрока.");
                    return true;
                }
                authManager.setCaptchaLocation(player.getLocation());
                sender.sendMessage("§8[§5✦ §dAEGIS§8] §dТочка капчи сохранена: §f"
                        + player.getWorld().getName() + " §7("
                        + (int) player.getLocation().getX() + ", "
                        + (int) player.getLocation().getY() + ", "
                        + (int) player.getLocation().getZ() + ")");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("setspawn")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Команда только для игрока.");
                    return true;
                }
                authManager.setSpawnLocation(player.getLocation());
                sender.sendMessage("§8[§5✦ §dAEGIS§8] §dТочка спавна сохранена: §f"
                        + player.getWorld().getName() + " §7("
                        + (int) player.getLocation().getX() + ", "
                        + (int) player.getLocation().getY() + ", "
                        + (int) player.getLocation().getZ() + ")");
                return true;
            }
            sender.sendMessage("§8[§5✦ §dAEGIS§8] §fИспользование: §d/aegisauth <setcaptcha|setspawn|reload>");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда только для игроков.");
            return true;
        }

        switch (name) {
            case "captcha" -> {
                if (authManager.getStage(player) != AuthManager.Stage.CAPTCHA) {
                    authManager.sendMsg(player, authManager.msg("already-authed"));
                    return true;
                }
                if (args.length == 0) {
                    authManager.giveCaptchaMap(player);
                    authManager.sendMsg(player, authManager.msg("captcha-refreshed"));
                } else {
                    authManager.handleCaptchaAttempt(player, args[0]);
                }
                return true;
            }
            case "register" -> {
                if (args.length < 2) {
                    authManager.sendMsg(player, "&fИспользуй: &d/register <пароль> <повтор>");
                    return true;
                }
                authManager.handleRegister(player, args[0], args[1]);
                return true;
            }
            case "login" -> {
                if (args.length < 1) {
                    authManager.sendMsg(player, "&fИспользуй: &d/login <пароль>");
                    return true;
                }
                authManager.handleLogin(player, args[0]);
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
