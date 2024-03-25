package net.horizonsend.ion.server.features.client.display

import net.horizonsend.ion.server.features.client.display.ClientDisplayEntityFactory.createBlockDisplay
import net.horizonsend.ion.server.features.client.display.ClientDisplayEntityFactory.createItemDisplay
import net.horizonsend.ion.server.features.client.display.ClientDisplayEntityFactory.getNMSData
import net.horizonsend.ion.server.miscellaneous.registrations.legacy.CustomItems
import net.horizonsend.ion.server.miscellaneous.utils.Tasks
import net.horizonsend.ion.server.miscellaneous.utils.Vec3i
import net.horizonsend.ion.server.miscellaneous.utils.debugAudience
import net.horizonsend.ion.server.miscellaneous.utils.minecraft
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Slime
import org.bukkit.Bukkit
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Display.*
import org.bukkit.entity.Player
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.min

/**
 * Functions for creating client-side display entities.
 */
object ClientDisplayEntities {

    /**
     * Sends a client-side entity to a client.
     * @param bukkitPlayer the player that the entity should be visible to
     * @param entity the NMS entity to send
     * @param duration the duration that the entity should be visible for
     */
    fun sendEntityPacket(bukkitPlayer: Player, entity: net.minecraft.world.entity.Entity, duration: Long) {
        val player = bukkitPlayer.minecraft
        val conn = player.connection

        conn.send(ClientboundAddEntityPacket(entity))
        entity.entityData.refresh(player)

        Tasks.syncDelayTask(duration) { conn.send(ClientboundRemoveEntitiesPacket(entity.id)) }
    }

    /**
     * Creates a glowing block outline.
     * @return an invisible, glowing NMS slime object that represents the glowing outline
     * @param player the player that the entity should be visible to
     * @param pos the position of the entity
     */
    fun highlightBlock(player: Player, pos: Vec3i): net.minecraft.world.entity.Entity {
        return Slime(EntityType.SLIME, player.minecraft.level()).apply {
            setPos(pos.x + 0.5, pos.y + 0.25, pos.z + 0.5)
            this.setSize(1, true)
            setGlowingTag(true)
            isInvisible = true

        }
    }

    fun Audience.highlightBlock(pos: Vec3i, duration: Long) {
        when (this) {
            is Player -> sendEntityPacket(this, highlightBlock(this, pos), duration)
            is ForwardingAudience -> for (player in audiences().filterIsInstance<Player>()) { sendEntityPacket(player, highlightBlock(player, pos), duration) }
        }
    }

    private fun Audience.highlightBlocks(positions: Collection<Vec3i>, duration: Long) {
        for (pos in positions) this.highlightBlock(pos, duration)
    }

    fun debugHighlightBlock(x: Number, y: Number, z: Number, duration: Long = 5L) = debugAudience.highlightBlock(Vec3i(x.toInt(), y.toInt(), z.toInt()), duration)
    fun debugHighlightBlock(pos: Vec3i, duration: Long = 5L) = debugAudience.highlightBlock(pos, duration)
    fun debugHighlightBlocks(blocks: Collection<Vec3i>, duration: Long = 5L) = debugAudience.highlightBlocks(blocks, duration)

    /**
     * Creates a client-side block object.
     * @return the NMS BlockDisplay object
     * @param player the player that the entity should be visible to
     * @param blockData the block information to use
     * @param pos the position of the entity
     * @param scale the size of the entity
     * @param glow if the BlockDisplay should glow
     */
    fun displayBlock(
        player: Player,
        blockData: BlockData,
        pos: Vector,
        scale: Float = 1.0f,
        glow: Boolean = false
    ): Display.BlockDisplay {

        val block = createBlockDisplay(player)
        val offset = (-scale / 2) + 0.5
        block.block = blockData
        block.isGlowing = glow
        block.transformation = Transformation(Vector3f(0f), Quaternionf(), Vector3f(scale), Quaternionf())

        return block.getNMSData(pos.x + offset, pos.y + offset, pos.z + offset)
    }

    /**
     * Creates a client-side ItemDisplay entity for rendering planet icons in space.
     * @return the NMS ItemDisplay object
     * @param player the player that the entity should be visible to
     * @param direction the direction that the entity will render from with respect to the player
     */
    fun displayPlanetEntity(
        player: Player,
        direction: Vector
    ): Display.ItemDisplay {

        val item = createItemDisplay(player)
        // render the entity from 1 chunk away
        val renderDistance = min(player.viewDistance - 1, Bukkit.getWorlds()[0].viewDistance - 1) * 16

        item.itemStack = CustomItems.PLANET_ICON_ARET.itemStack(1)
        item.billboard = Billboard.CENTER
        item.viewRange = 5.0f
        // use the direction vector to offset the entity's position from the player
        val position = player.eyeLocation.toVector().add(direction.clone().normalize().multiply(renderDistance))

        return item.getNMSData(position)
    }
}