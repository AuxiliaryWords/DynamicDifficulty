package me.skinnyjeans.gmd;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class WorldAffinity extends Affinity {

	public WorldAffinity(Main m) { super(m); }

	@EventHandler
	public void onDeath(PlayerRespawnEvent e) {
		if (onDeath != 0) {
			worldAffinity = calcAffinity(worldAffinity + onDeath);
		}
	}

	@EventHandler
	public void onKill(EntityDeathEvent e) {
		try{
			if ((onPVPKill != 0 || onPVEKill != 0) && e.getEntity().getKiller() instanceof Player) {
				if (e.getEntity() instanceof Player) {
					worldAffinity = calcAffinity(worldAffinity + onPVPKill);
				} else if (mobsPVE.contains(e.getEntityType().toString())) {
					worldAffinity = calcAffinity(worldAffinity + onPVEKill);
				}
			}

			if (!(e.getEntity() instanceof Player) && !(e.getEntity() instanceof EnderDragon) && !(e.getEntity() instanceof Wither) && e.getEntity().getKiller() instanceof Player) {
				e.setDroppedExp( (int) (e.getDroppedExp() * calcPercentage("experience-multiplier") / 100.0));
				double DoubleLoot = calcPercentage("double-loot-chance");
				if (DoubleLoot != 0.0 && new Random().nextDouble() < DoubleLoot / 100.0 && !e.getEntity().getCanPickupItems()) {
					for (int i = 0; i < e.getDrops().size(); i++)
						Bukkit.getWorld(e.getEntity().getWorld().getUID()).dropItemNaturally(e.getEntity().getLocation(), e.getDrops().get(i));
				}
			}
		}
		catch(NullPointerException error){
			Bukkit.getConsoleSender().sendMessage("NullPointerException. Enemytype Attack: "+e.getEntity().getKiller().getType()+" Enemytype Hit: "+e.getEntity().getType());
			// Ugly tmp fix
		}
	}

	@EventHandler
	public void onMined(BlockBreakEvent e) {
		if (onMined != 0 && blocks.contains(e.getBlock().getBlockData().getMaterial().name()) && (!e.getPlayer().getItemOnCursor().containsEnchantment(Enchantment.SILK_TOUCH) || silkTouchAllowed ) && e.getPlayer().getGameMode() != GameMode.CREATIVE) {
			worldAffinity = calcAffinity(worldAffinity + onMined);
		}
	}

	@EventHandler
	public void onHit(EntityDamageByEntityEvent e) {
		Entity prey = e.getEntity();
		Entity hunter = e.getDamager();
		try{
			if (prey instanceof Player) {
				if (!(hunter instanceof Player) && !(hunter instanceof EnderDragon) && !(hunter instanceof Wither)) {
					worldAffinity = calcAffinity(worldAffinity + onPlayerHit);
					double dam = e.getFinalDamage() * calcPercentage("damage-done-by-mobs") / 100.0;
					e.setDamage(dam);
				}
				if (!(hunter instanceof Player) && (hunter instanceof LivingEntity || hunter instanceof Arrow) && effectsWhenAttacked.get(calcDifficulty(null)))
					for (PotionEffectType effect : effects) {
						if (((LivingEntity) prey).hasPotionEffect(effect))
							((LivingEntity) prey).removePotionEffect(effect);
					}
			} else if (hunter instanceof Player && !(prey instanceof Player) && !(prey instanceof EnderDragon) && !(prey instanceof Wither)) {
				double dam = e.getFinalDamage() * calcPercentage("damage-done-on-mobs") / 100.0;
				e.setDamage(dam);
			}
		}
		catch(NullPointerException error){
			Bukkit.getConsoleSender().sendMessage("NullPointerException. Enemytype Attack: "+hunter+" Enemytype Hit: "+prey);
			// Ugly tmp fix
		}
	}

	// To increase/decrease score every few minutes
	@Override
    public void onInterval() {
        worldAffinity = calcAffinity(worldAffinity + onInterval);
    }
}
