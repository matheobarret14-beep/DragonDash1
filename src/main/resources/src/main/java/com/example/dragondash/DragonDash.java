package com.example.dragondash;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class DragonDash extends JavaPlugin implements Listener {

    private static final long COOLDOWN_MS = 10000L;
    private static final double DASH_MULTIPLIER = 1.6;
    private static final int SPEED_TICKS = 40;
    private static final int NO_FALL_TICKS = 40;

    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Set<UUID> noFall = new HashSet<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        createRecipe();
        getLogger().info("DragonDash enabled");
    }

    private void createRecipe() {
        ItemStack stick = createDragonDash();
        NamespacedKey key = new NamespacedKey(this, "dragon_dash_recipe");

        ShapedRecipe recipe = new ShapedRecipe(key, stick);
        recipe.shape("BBB","FFF","FFF");
        recipe.setIngredient('B', Material.BLAZE_ROD);
        recipe.setIngredient('F', Material.FIREWORK_ROCKET);

        Bukkit.addRecipe(recipe);
    }

    private ItemStack createDragonDash() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cDragon Dash");
        meta.setCustomModelData(1001);
        meta.setLore(Arrays.asList("§7Bâton Dragon", "§eDash de puissance"));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player)) return true;
        if (!sender.hasPermission("dragondash.give")) {
            sender.sendMessage("§cTu n'as pas la permission.");
            return true;
        }
        Player p = (Player) sender;
        p.getInventory().addItem(createDragonDash());
        p.sendMessage("§aDragon Dash donné !");
        return true;
    }

    @EventHandler
    public void onDash(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();

        if (!meta.hasDisplayName()) return;
        if (!meta.getDisplayName().equals("§cDragon Dash")) return;

        switch (event.getAction()) {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
                tryDash(player);
                break;
        }
    }

    private void tryDash(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastUse.getOrDefault(id, 0L);

        if (now - last < COOLDOWN_MS) {
            long left = (COOLDOWN_MS - (now - last)) / 1000;
            player.sendMessage("§cCooldown: " + left + "s");
            return;
        }

        doDash(player);
        lastUse.put(id, now);
    }

    private void doDash(Player player) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Vector vel = dir.multiply(DASH_MULTIPLIER).setY(0.25);

        player.setVelocity(vel);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);

        for (int i = 0; i < 20; i++) {
            player.getWorld().spawnParticle(
                Particle.DRAGON_BREATH,
                player.getLocation().add(dir.clone().multiply(i * 0.2)),
                5, 0.1, 0.1, 0.1, 0.01
            );
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SPEED_TICKS, 0));
        noFall.add(player.getUniqueId());

        new BukkitRunnable() {
            @Override
            public void run() {
                noFall.remove(player.getUniqueId());
            }
        }.runTaskLater(this, NO_FALL_TICKS);

        player.sendMessage("§c§lDragon Dash !");
    }

    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player p = (Player) event.getEntity();
        if (noFall.contains(p.getUniqueId())) {
            event.setCancelled(true);
            noFall.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastUse.remove(id);
        noFall.remove(id);
    }
}
