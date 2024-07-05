package net.horizonsend.ion.proxy.commands.discord

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.horizonsend.ion.common.database.cache.nations.NationCache
import net.horizonsend.ion.common.database.cache.nations.SettlementCache
import net.horizonsend.ion.common.database.schema.misc.SLPlayer
import net.horizonsend.ion.common.database.schema.nations.Nation
import net.horizonsend.ion.common.database.schema.nations.Territory
import net.horizonsend.ion.common.utils.miscellaneous.toCreditsString
import net.horizonsend.ion.proxy.features.discord.DiscordCommand
import net.horizonsend.ion.proxy.features.discord.DiscordSubcommand.Companion.subcommand
import net.horizonsend.ion.proxy.features.discord.ExecutableCommand
import net.horizonsend.ion.proxy.features.discord.SlashCommandManager
import net.horizonsend.ion.proxy.utils.messageEmbed
import org.litote.kmongo.eq
import java.util.Date

object DiscordNationCommand : DiscordCommand("nation", "Commands relating to nations") {
	override fun setup(commandManager: SlashCommandManager) {
		commandManager.registerCompletion("nations") { NationCache.all().map { it.name } }

		registerSubcommand(onInfo)
	}

	private val onInfo = subcommand(
		"info",
		"Get info about a nation",
		listOf(ExecutableCommand.CommandField("nation", OptionType.STRING, "nations", "The name of the nation"))
	) { event ->
		val nationName = event.getOption("nation")?.asString ?: fail { "You must enter a nation name!" }

		val nationId = resolveNation(nationName)
		val nation = Nation.findById(nationId) ?: fail { "Failed to load data" }
		val cached = NationCache[nationId]

		val outposts = Territory.findProps(Territory::nation eq nationId, Territory::name)
			.map { it[Territory::name] }

		val outpostsField = if (!outposts.none()) MessageEmbed.Field(
			"Outposts (${outposts.count()})",
			outposts.joinToString(),
			false
		) else null

		val settlements: List<SettlementCache.SettlementData> = Nation.getSettlements(nationId)
			.sortedByDescending { SLPlayer.count(SLPlayer::settlement eq it) }
			.toList()
			.map { SettlementCache[it] }

		val settlementsField = MessageEmbed.Field(
			"Settlements: (${settlements.size})",
			settlements.joinToString { it.name },
			false
		)

		val leaderField = MessageEmbed.Field(
			"Leader:",
			getPlayerName(cached.leader),
			false
		)

		val balanceField = MessageEmbed.Field(
			"Balance:",
			nation.balance.toCreditsString(),
			false
		)

		val members: List<Pair<String, Date>> = SLPlayer
			.findProps(SLPlayer::nation eq nationId, SLPlayer::lastKnownName, SLPlayer::lastSeen)
			.map { it[SLPlayer::lastKnownName] to it[SLPlayer::lastSeen] }
			.sortedByDescending { it.second }

		val playersField = MessageEmbed.Field(
			"Players (${members.size})",
			members.joinToString { it.first }.take(1024),
			false
		)

		val fields: List<MessageEmbed.Field> = listOfNotNull(
			outpostsField,
			settlementsField,
			balanceField,
			leaderField,
			playersField
		)

		respondEmbed(
			event,
			messageEmbed(
				title = "Nation: $name",
				fields = fields,
				color = nation.color
			)
		)
	}
}
