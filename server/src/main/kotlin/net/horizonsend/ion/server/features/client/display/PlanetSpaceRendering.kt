package net.horizonsend.ion.server.features.client.display

import net.horizonsend.ion.server.IonServerComponent
import net.horizonsend.ion.server.features.space.Space
import net.horizonsend.ion.server.features.space.SpaceWorlds
import net.horizonsend.ion.server.miscellaneous.utils.Tasks
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object PlanetSpaceRendering : IonServerComponent() {
    private const val PLANET_UPDATE_RATE = 20L

    override fun onEnable() {
        Tasks.syncRepeat(0L, PLANET_UPDATE_RATE) {
            Bukkit.getOnlinePlayers().forEach { player ->
                renderPlanets(player)
            }
        }
    }

    /**
     * Renders client-side ItemEntity planets for each player.
     * @param player the player to send objects to
     */
    private fun renderPlanets(player: Player) {
        // Only render planets if the player is in a space world
        if (!SpaceWorlds.contains(player.world)) return

        val planetList = Space.getPlanets().filter { it.spaceWorld == player.world }
        for (planet in planetList) {
            // direction = the normalized vector that points from the player's location to the planet
            val direction = player.location.toVector().add(planet.location.toVector()).normalize()

            // send packet
            ClientDisplayEntities.sendEntityPacket(
                player,
                ClientDisplayEntities.displayPlanetEntity(player, direction),
                PLANET_UPDATE_RATE
            )
        }
    }
}