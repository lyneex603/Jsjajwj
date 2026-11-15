package dev.randomportal;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Exit from the Nether puts the player at a random SAFE spot in the Overworld (like /rtp). */
public class RandomPortal extends JavaPlugin implements Listener {

    /** Blocks we never want to stand on. */
    private static final Set<Material> BAD_GROUND = EnumSet.of(
            Material.CACTUS, Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.FIRE, Material.SOUL_FIRE, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
            Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.COBWEB);

    /** Blocks we do not want anywhere close to the landing spot. */
    private static final Set<Material> BAD_NEARBY = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);

    private int range;
    private int minRange;
    private boolean sendMessages;
    private List<String> msgTeleported;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        range = Math.max(50, getConfig().getInt("range", 1300));
        minRange = Math.max(0, Math.min(getConfig().getInt("min-range", 0), range - 1));
        sendMessages = getConfig().getBoolean("send-messages", true);
        msgTeleported = getConfig().getStringList("messages.teleported");
        if (msgTeleported.isEmpty()) msgTeleported = List.of("&6Портал выбросил вас в случайное место!", "&7Вы вышли из портала. Удачи!");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RandomPortal enabled, range=" + minRange + ".." + range);
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (e.getCause() != TeleportCause.NETHER_PORTAL) return;
        World from = e.getFrom().getWorld();
        if (from == null || from.getEnvironment() != World.Environment.NETHER) return;

        World target = null;
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) { target = w; break; }
        }
        if (target == null) return;

        Player p = e.getPlayer();
        Location loc = findSafe(target);
        if (loc == null) return; // no safe spot found: vanilla portal behaviour, no message
        e.setTo(loc);
        e.setCanCreatePortal(true);
        say(p, msgTeleported, loc);
    }

    private void say(Player p, List<String> lines, Location loc) {
        if (!sendMessages || lines == null || lines.isEmpty()) return;
        final java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            String text = line;
            if (loc != null) {
                text = text.replace("{x}", String.valueOf(loc.getBlockX()))
                           .replace("{y}", String.valueOf(loc.getBlockY()))
                           .replace("{z}", String.valueOf(loc.getBlockZ()));
            }
            out.add(text.replace('&', '\u00a7'));
        }
        // send one tick later so it arrives after the teleport
        getServer().getScheduler().runTask(this, () -> {
            if (p.isOnline()) for (String s : out) p.sendMessage(s);
        });
    }

    private Location findSafe(World w) {
        Location c = w.getWorldBorder().getCenter();
        double half = Math.min(range, w.getWorldBorder().getSize() / 2.0 - 16);
        if (half < 10) return null;
        int h = (int) half;
        var r = ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            int x = (int) c.getX() + r.nextInt(-h, h + 1);
            int z = (int) c.getZ() + r.nextInt(-h, h + 1);
            if (Math.max(Math.abs(x - (int) c.getX()), Math.abs(z - (int) c.getZ())) < minRange) continue;
            int y = w.getHighestBlockYAt(x, z);
            if (isSafe(w, x, y, z)) return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    /** Ground block at (x,y,z); the player stands at y+1. */
    private boolean isSafe(World w, int x, int y, int z) {
        Block ground = w.getBlockAt(x, y, z);
        Material gm = ground.getType();
        if (!gm.isSolid() || ground.isLiquid()) return false;      // no air / water / lava
        if (Tag.LEAVES.isTagged(gm)) return false;                 // not on tree tops
        if (BAD_GROUND.contains(gm)) return false;                 // cactus, magma, fire...

        // space for the player: feet and head must be free
        Block feet = w.getBlockAt(x, y + 1, z);
        Block head = w.getBlockAt(x, y + 2, z);
        if (!feet.isPassable() || feet.isLiquid()) return false;
        if (!head.isPassable() || head.isLiquid()) return false;

        // no lava / fire / cactus etc. within 2 blocks
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    if (BAD_NEARBY.contains(w.getBlockAt(x + dx, y + dy, z + dz).getType())) return false;
                }
            }
        }
        return true;
    }
}
