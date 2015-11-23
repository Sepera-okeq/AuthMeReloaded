package fr.xephi.authme.process.register;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.cache.auth.PlayerAuth;
import fr.xephi.authme.cache.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.security.PasswordSecurity;
import fr.xephi.authme.settings.Messages;
import fr.xephi.authme.settings.Settings;
import org.bukkit.entity.Player;

import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 */
public class AsyncRegister {

    protected Player player;
    protected String name;
    protected String password;
    protected String email = "";
    private AuthMe plugin;
    private DataSource database;
    private Messages m = Messages.getInstance();

    /**
     * Constructor for AsyncRegister.
     *
     * @param player   Player
     * @param password String
     * @param email    String
     * @param plugin   AuthMe
     * @param data     DataSource
     */
    public AsyncRegister(Player player, String password, String email,
                         AuthMe plugin, DataSource data) {
        this.player = player;
        this.password = password;
        name = player.getName().toLowerCase();
        this.email = email;
        this.plugin = plugin;
        this.database = data;
    }

    /**
     * Method getIp.
     *
     * @return String
     */
    protected String getIp() {
        return plugin.getIP(player);
    }

    /**
     * Method preRegisterCheck.
     *
     * @return boolean * @throws Exception
     */
    protected boolean preRegisterCheck() throws Exception {
        String passLow = password.toLowerCase();
        if (PlayerCache.getInstance().isAuthenticated(name)) {
            m.send(player, "logged_in");
            return false;
        } else if (!Settings.isRegistrationEnabled) {
            m.send(player, "reg_disabled");
            return false;
        } else if (passLow.contains("delete") || passLow.contains("where") || passLow.contains("insert") || passLow.contains("modify") || passLow.contains("from") || passLow.contains("select") || passLow.contains(";") || passLow.contains("null") || !passLow.matches(Settings.getPassRegex)) {
            m.send(player, "password_error");
            return false;
        } else if (passLow.equalsIgnoreCase(player.getName())) {
            m.send(player, "password_error_nick");
            return false;
        } else if (password.length() < Settings.getPasswordMinLen || password.length() > Settings.passwordMaxLength) {
            m.send(player, "pass_len");
            return false;
        } else if (!Settings.unsafePasswords.isEmpty() && Settings.unsafePasswords.contains(password.toLowerCase())) {
            m.send(player, "password_error_unsafe");
            return false;
        } else if (database.isAuthAvailable(name)) {
            m.send(player, "user_regged");
            return false;
        } else if (Settings.getmaxRegPerIp > 0) {
            if (!plugin.getPermissionsManager().hasPermission(player, "authme.allow2accounts") && database.getAllAuthsByIp(getIp()).size() >= Settings.getmaxRegPerIp && !getIp().equalsIgnoreCase("127.0.0.1") && !getIp().equalsIgnoreCase("localhost")) {
                m.send(player, "max_reg");
                return false;
            }
        }
        return true;
    }

    public void process() {
        try {
            if (!preRegisterCheck())
                return;
            if (!email.isEmpty() && !email.equals("")) {
                if (Settings.getmaxRegPerEmail > 0) {
                    if (!plugin.getPermissionsManager().hasPermission(player, "authme.allow2accounts") && database.getAllAuthsByEmail(email).size() >= Settings.getmaxRegPerEmail) {
                        m.send(player, "max_reg");
                        return;
                    }
                }
                emailRegister();
                return;
            }
            passwordRegister();
        } catch (Exception e) {
            ConsoleLogger.showError(e.getMessage());
            ConsoleLogger.writeStackTrace(e);
            m.send(player, "error");
        }
    }

    /**
     * Method emailRegister.
     *
     * @throws Exception
     */
    protected void emailRegister() throws Exception {
        if (Settings.getmaxRegPerEmail > 0) {
            if (!plugin.getPermissionsManager().hasPermission(player, "authme.allow2accounts") && database.getAllAuthsByEmail(email).size() >= Settings.getmaxRegPerEmail) {
                m.send(player, "max_reg");
                return;
            }
        }
        PlayerAuth auth;
        final String hashNew = PasswordSecurity.getHash(Settings.getPasswordHash, password, name);
        auth = new PlayerAuth(name, hashNew, getIp(), 0, (int) player.getLocation().getX(), (int) player.getLocation().getY(), (int) player.getLocation().getZ(), player.getLocation().getWorld().getName(), email, player.getName());
        if (PasswordSecurity.userSalt.containsKey(name)) {
            auth.setSalt(PasswordSecurity.userSalt.get(name));
        }
        database.saveAuth(auth);
        database.updateEmail(auth);
        database.updateSession(auth);
        plugin.mail.main(auth, password);
        ProcessSyncEmailRegister sync = new ProcessSyncEmailRegister(player, plugin);
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, sync);

    }

    protected void passwordRegister() {
        PlayerAuth auth;
        String hash;
        try {
            hash = PasswordSecurity.getHash(Settings.getPasswordHash, password, name);
        } catch (NoSuchAlgorithmException e) {
            ConsoleLogger.showError(e.getMessage());
            m.send(player, "error");
            return;
        }
        if (Settings.getMySQLColumnSalt.isEmpty() && !PasswordSecurity.userSalt.containsKey(name)) {
            auth = new PlayerAuth(name, hash, getIp(), new Date().getTime(), "your@email.com", player.getName());
        } else {
            auth = new PlayerAuth(name, hash, PasswordSecurity.userSalt.get(name), getIp(), new Date().getTime(), player.getName());
        }
        if (!database.saveAuth(auth)) {
            m.send(player, "error");
            return;
        }
        if (!Settings.forceRegLogin) {
            PlayerCache.getInstance().addPlayer(auth);
            database.setLogged(name);
        }
        plugin.otherAccounts.addPlayer(player.getUniqueId());
        ProcessSyncronousPasswordRegister sync = new ProcessSyncronousPasswordRegister(player, plugin);
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, sync);
    }
}
