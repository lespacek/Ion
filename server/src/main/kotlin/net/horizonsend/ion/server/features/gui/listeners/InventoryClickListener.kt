package net.horizonsend.ion.server.features.gui.listeners

import net.horizonsend.ion.server.features.gui.ScreenManager.isInScreen
import net.horizonsend.ion.server.features.gui.ScreenManager.screen
import net.horizonsend.ion.server.listener.SLEventListener
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent

class InventoryClickListener : SLEventListener() {
	@EventHandler
	@Suppress("Unused")
	fun onInventoryClickEvent(event: InventoryClickEvent) {
		if ((event.whoClicked as? Player)?.isInScreen == true) {
			(event.whoClicked as Player).screen!!.handleInventoryClick(event)
			event.isCancelled = true
		}
	}
}