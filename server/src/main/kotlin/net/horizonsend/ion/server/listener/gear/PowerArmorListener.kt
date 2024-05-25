package net.horizonsend.ion.server.listener.gear

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import net.horizonsend.ion.common.database.cache.nations.NationCache
import net.horizonsend.ion.server.features.cache.PlayerCache
import net.horizonsend.ion.server.features.gear.powerarmor.PowerArmorManager
import net.horizonsend.ion.server.features.gear.powerarmor.PowerArmorModule
import net.horizonsend.ion.server.features.misc.getPower
import net.horizonsend.ion.server.features.misc.removePower
import net.horizonsend.ion.server.features.starship.active.ActiveStarships
import net.horizonsend.ion.server.listener.SLEventListener
import net.horizonsend.ion.server.listener.misc.ProtectionListener
import net.horizonsend.ion.server.miscellaneous.registrations.legacy.CustomItems
import net.horizonsend.ion.server.miscellaneous.utils.Tasks
import net.horizonsend.ion.server.miscellaneous.utils.action
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import java.time.Instant
import java.util.Locale
import java.util.UUID

private val lastMoved = HashMap<UUID, Long>()

fun hasMovedInLastSecond(player: Player): Boolean {
	return lastMoved.containsKey(player.uniqueId) && Instant.now().toEpochMilli() - (
		lastMoved[player.uniqueId]
			?: 0
		) < 1000
}

object PowerArmorListener : SLEventListener() {
	@EventHandler
	fun onEquipPowerArmor(event: PlayerArmorChangeEvent) {
		val player: Player = event.player
		val slot: PlayerArmorChangeEvent.SlotType = event.slotType

		Tasks.sync {
			if (!player.isOnline) {
				return@sync
			}

			val item: ItemStack = player.inventory.armorContents[3 - slot.ordinal] ?: return@sync
			val customItem: CustomItems.PowerArmorItem = CustomItems[item] as? CustomItems.PowerArmorItem ?: return@sync

			val meta = item.itemMeta as LeatherArmorMeta
			if (meta.displayName != customItem.displayName) {
				return@sync
			}

			val nation = PlayerCache[player].nationOid?.let(NationCache::get) ?: return@sync
			val nationColor = nation.color

			if (meta.color.asRGB() == nationColor) {
				return@sync
			}

			val bukkitColor: Color = Color.fromRGB(nationColor)
			meta.setColor(bukkitColor)
			item.itemMeta = meta
			player.updateInventory()
			player action "&7&oPower armor color changed to match nation color (rename it in an anvil to fix this)"
		}
	}

	@EventHandler
	fun onEntityDamage(event: EntityDamageEvent) {
		if (event.entity !is Player) return
		val player = event.entity as Player
		var modifier = 0.0
		val modules = HashMap<PowerArmorModule, ItemStack>()
		val cause = event.cause

		for (item in player.inventory.armorContents) {
			if (!PowerArmorManager.isPowerArmor(item)) {
				continue
			}

			if (getPower(item!!) < 100) {
				continue
			}

			if (item.enchantments.none()) {
				modifier += 0.5 / 4
			}

			if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
				!player.world.name.lowercase(Locale.getDefault()).contains("arena") &&
				!ProtectionListener.isProtectedCity(player.location)
			) {
				removePower(item, 100)
			}

			for (module in PowerArmorManager.getModules(item)) {
				modules[module] = item
			}
		}

		for ((module, moduleItem) in modules) {
			if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
				modifier = 0.0
				if (!player.world.name.lowercase(Locale.getDefault()).contains("arena")) {
					removePower(moduleItem, 10)
				}
			}
		}

		if (modifier == 0.0) {
			return
		}

		if (!event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
			return
		}

		event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, -event.damage * modifier)
	}

	@EventHandler
	fun onMove(event: PlayerMoveEvent) {
		lastMoved[event.player.uniqueId] = Instant.now().toEpochMilli()
	}

	@EventHandler
	fun onCraft(event: PrepareItemCraftEvent) {
		var armor: ItemStack? = null
		var module: ItemStack? = null

		val matrix = event.inventory.matrix

		for (item in matrix) {
			if (PowerArmorManager.isPowerArmor(item)) {
				armor = item
			} else if (PowerArmorManager.isModule(item)) module = item
		}

		for (item in matrix) {
			if (item != null && item !== armor && item !== module) {
				return
			}
		}

		if (armor == null || module == null || module.amount > 1) return

		val newArmor = armor.clone()
		val meta = newArmor.itemMeta
		val lore = meta.lore ?: return

		if (lore.stream().anyMatch { s -> s.startsWith("Module: ") }) {
			return
		}

		val powerArmorModule = PowerArmorModule[module] ?: return

		if (!powerArmorModule.isCompatible(PowerArmorManager.getPowerArmorType(armor))) {
			return
		}

		lore.add("Module: " + powerArmorModule.name)
		meta.lore = lore
		newArmor.itemMeta = meta
		event.inventory.result = newArmor
	}

	@EventHandler
	fun onToggleRocketBoosters(event: PlayerToggleSneakEvent) {
		val player = event.player
		if(ActiveStarships.findByPilot(player) != null && player.inventory.itemInMainHand.type == Material.CLOCK) return
		for (item in player.inventory.armorContents) {
			if (!PowerArmorManager.isPowerArmor(item) || getPower(item!!) == 0) continue
			for (module in PowerArmorManager.getModules(item)) {
				if (module == PowerArmorModule.ROCKET_BOOSTING) {
					PowerArmorManager.toggleGliding(player)
				}
			}
		}
	}

	@EventHandler
	fun onEntityKnockBackEvent(event: EntityKnockbackByEntityEvent) {
		val player = event.entity as? Player ?: return

		for (item in player.inventory.armorContents) {
			if (!PowerArmorManager.isPowerArmor(item) || getPower(item!!) == 0) continue

			for (module in PowerArmorManager.getModules(item))
				if (module == PowerArmorModule.SHOCK_ABSORBING) {
					event.isCancelled = true
					return
				}
		}
	}

	@EventHandler
	fun onEntityToggleGlideEvent(event: EntityToggleGlideEvent) {
		val player = event.entity as? Player ?: return
		if(player.isGliding && player.isSneaking && PowerArmorManager.glideDisabledPlayers[player.uniqueId] == 0L) {
			event.isCancelled = true
		}
	}

	@EventHandler
	fun onPlayerRocketBootDamage(event: EntityDamageEvent) {
		if (event.entity !is Player) return
		if (event.cause != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return

		event.isCancelled = true
	}
}
