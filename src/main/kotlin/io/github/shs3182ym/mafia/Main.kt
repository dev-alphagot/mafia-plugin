package io.github.shs3182ym.mafia

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.monun.kommand.StringType
import io.github.monun.kommand.getValue
import io.github.monun.kommand.kommand
import io.github.monun.tap.util.updateFromGitHubMagically
import io.github.shs3182ym.mafia.config.*
import io.github.shs3182ym.mafia.config.MafiaConfig.autoChangeTimeByGameState
import io.github.shs3182ym.mafia.config.MafiaConfig.displayGuide
import io.github.shs3182ym.mafia.config.MafiaConfig.isOnDebug
import io.github.shs3182ym.mafia.config.MafiaConfig.isOnStream
import io.github.shs3182ym.mafia.config.MafiaConfig.load
import io.github.shs3182ym.mafia.config.MafiaConfig.msgPrefix
import io.github.shs3182ym.mafia.profession.MafiaProfession
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random
import java.io.File
import java.time.Duration
import java.util.*
import java.util.function.Consumer
import kotlin.random.nextInt

@Suppress("ControlFlowWithEmptyBody")
class Main : JavaPlugin(), Listener {
    private fun text(t: String): Component = GsonComponentSerializer.gson().deserialize(msgPrefix).append(Component.text(t))
    private fun cmd(t: String): Component = Component.text(t).color(NamedTextColor.LIGHT_PURPLE).clickEvent(
        ClickEvent.suggestCommand(
            if(t.startsWith("/")) t
            else "/mafia $t"
        )
    )


    companion object {
        lateinit var instance: Main
            private set
    }

    private val configFile = File(dataFolder, "config.yml")
    private val observers: MutableList<UUID> = mutableListOf()

    private lateinit var randThread: Thread

    private var day = 1
    private var isNight = false

    private var voted: MutableList<UUID> = mutableListOf()
    private var abilityUsed: MutableList<UUID> = mutableListOf()

    private var voteMap = mutableMapOf<UUID, Int>()
    private var targetMap = mutableMapOf<MafiaProfession, MutableList<UUID>>()

    private var isPlaying: Boolean = false
	
    private var voteSum = 0

    private lateinit var gameMainThread: Thread
	
    private var couples: Pair<Pair<Player, Int>, Pair<Player, Int>>? = null

    @EventHandler
    fun onChat(e: AsyncChatEvent) {
		if(!isPlaying) return
	
        if(!e.player.isAlive) {
            e.isCancelled = true

            server.onlinePlayers.filter { !it.isAlive }.forEach {
                it.sendMessage(Component.text("<").append(e.player.displayName()).append(Component.text("> ")).append(e.originalMessage()))
            }

            return
        }

        if(e.player.isPlayer()) {
			if(
				PlainTextComponentSerializer.plainText().serialize(e.originalMessage()).startsWith("?????? ") ||
				PlainTextComponentSerializer.plainText().serialize(e.originalMessage()).startsWith("?????? ")
			){
                e.isCancelled = true

                val text = PlainTextComponentSerializer.plainText().serialize(e.originalMessage())

                when(text.split(" ")[0]){
                    "??????" -> {
                        val text = text.replace("?????? ", "")

                        if(server.onlinePlayers.any { it.name.lowercase() == text.lowercase() }){
                            if(voted.contains(e.player.uniqueId)){
                                e.player.sendMessage(text("?????? ????????? ????????????."))
                                return
                            }

                            if(!voteMap.containsKey(server.onlinePlayers.first { it.name == text }.uniqueId)) voteMap[server.onlinePlayers.first { it.name == text }.uniqueId] = 1
                            else voteMap[server.onlinePlayers.first { it.name == text }.uniqueId] = voteMap[server.onlinePlayers.first { it.name == text }.uniqueId]!! + 1

                            voted.add(e.player.uniqueId)
                            e.player.sendMessage(text("????????? ?????????????????????."))
							
							voteSum += 1
                        }
                        else {
                            e.player.sendMessage(text("???????????? ??????????????? ?????? ??? ????????????."))
                        }
                    }
                    "??????" -> {
                        if(e.player.isSpecial()){
                            val text = text.replace("?????? ", "")

                            if(server.onlinePlayers.any { it.name.lowercase() == text.lowercase() }){
                                if(abilityUsed.contains(e.player.uniqueId)){
                                    e.player.sendMessage(text("?????? ????????? ??????????????????."))
                                    return
                                }

                                val targetID = server.onlinePlayers.first { it.name == text }.uniqueId

                                if(targetMap.containsKey(e.player.profession)) targetMap[e.player.profession]!!.add(targetID)
                                else targetMap[e.player.profession] = mutableListOf(targetID)

                                abilityUsed.add(e.player.uniqueId)
                                e.player.sendMessage(text("????????? ??????????????????."))
                            }
                            else {
                                e.player.sendMessage(text("???????????? ??????????????? ?????? ??? ????????????."))
                            }
                        }
                        else {
                            e.player.sendMessage(text("????????? ????????????."))
                        }
                    }
                }
            }
            else {
                if(isNight) {
                    e.isCancelled = true

                    e.message(Component.text(" (${day}?????? ???): ").append(e.originalMessage()))

                    server.onlinePlayers.filter { it.profession == e.player.profession && e.player.profession != MafiaProfession.INNOCENT }.forEach {
                        it.sendMessage(e.message())
                    }

                    return
                }
                else {
                    e.message(Component.text(" (${day}?????? ???): ").append(e.originalMessage()))
                }
            }
        }
    }

    override fun onDisable() {
        MafiaConfig.save(configFile)
    }

    override fun onEnable() {
        instance = this
        load(configFile)

        this.updateFromGitHubMagically("shs3182ym", "mafia-plugin", "mafia-plugin.jar", logger::info)
		
		server.pluginManager.registerEvents(instance, instance)

        val ic: Consumer<InventoryClickEvent> = Consumer {
            it.isCancelled = true
        }

        object: BukkitRunnable() {
            override fun run(){
                if(isPlaying){
                    server.onlinePlayers.forEach {
                        if(!it.isAlive) it.gameMode = GameMode.SPECTATOR
                    }

                    if(autoChangeTimeByGameState){
                        if(isNight){
                            server.worlds.forEach {
                                it.time = 18000
                            }
                        }
                        else {
                            server.worlds.forEach {
                                it.time = 6000
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(instance, 0L, 1L)

        fun namedItem(s: ItemStack, n: Component = Component.empty(), l: List<Component> = listOf(), o: Consumer<InventoryClickEvent> = ic
        ): GuiItem {
            val st = s.clone()
            val mta = st.itemMeta
            mta.displayName(n.decoration(TextDecoration.ITALIC, false))
            mta.lore(l)
            st.itemMeta = mta

            val ite = GuiItem(
                st
            )
            ite.setAction(o)

            return ite
        }

        kommand {
            register("mafia"){
                executes {
                    arrayOf(
                        text("????????? ???????????? ?????? ${instance.description.version} by AlphaGot${if(!isOnStream) "#0388" else ""} ")
                            .append(Component.text("(shs3182ym)").clickEvent(
                                ClickEvent.openUrl(
                                    "https://github.com/shs3182ym"
                                )
                            )),
                        text("??? ??????????????? GNU GPLv3 ??????????????? ????????????. ????????? ????????? ")
                            .append(Component.text("GitHub ???????????????").clickEvent(
                                ClickEvent.openUrl(
                                    "https://github.com/shs3182ym/mafia-plugin"
                                )
                            ))
                            .append(Component.text("??? ??????????????????.")),
                        text("????????? ????????? ????????? ").append(cmd("/mafia help")).append(Component.text(" ???????????? ???????????????."))
                    ).forEach (sender::sendMessage)
                }
                then("d-sudo"){
                    requires { isOnDebug }
                    then("victim" to player()){
                        then("command" to string(StringType.GREEDY_PHRASE)){
                            executes {
                                val victim: Player by it
                                val command: String by it

                                victim.chat(command)
                            }
                        }
                    }
                }
                then("d-freset"){
                    requires { isOnDebug }
                    executes {
                        isPlaying = false

                        day = 0
                        isNight = false

                        voted.removeAll(voted)
                        voteMap = mutableMapOf()
                        abilityUsed.removeAll(abilityUsed)
                        targetMap = mutableMapOf()
						
						voteSum = 0

                        server.onlinePlayers.forEach {
                            it.profession = MafiaProfession.NONE
                            it.isAlive = false
                        }

                        gameMainThread.stop()

                        sender.sendMessage(text("?????? ?????? ????????? ??????"))
                    }
                }
                then("help"){
                    executes {
                        val helpMsg = if(isOnStream){
                            arrayOf(
                                text("????????? (1/1)"),
                                text("").append(cmd("/mafia")).append(Component.text(": ?????? ??? ???????????? ??? ??? ????????????.")),
                                text("+  ").append(cmd("help")).append(Component.text(": ???????????? ??? ??? ????????????.")),
                                text("+  ").append(cmd("conf-gui")).append(Component.text(": ?????? ?????? GUI??? ????????????.")),
                                text("+  ").append(cmd("start")).append(Component.text(": ????????? ???????????????."))
                            )
                        }
                        else {
                            arrayOf(
                                text("????????? (1/1)"),
                                text("").append(cmd("/mafia")).append(Component.text(": ?????? ??? ???????????? ??? ??? ????????????.")),
                                text("+  ").append(cmd("help")).append(Component.text(": ???????????? ??? ??? ????????????.")),
                                text("+  ").append(cmd("conf-gui")).append(Component.text(": ?????? ?????? GUI??? ????????????.")),
                                text("+  ").append(cmd("start")).append(Component.text(": ????????? ???????????????.\n")),
                                text("???".repeat(18)),
                                text("????????? ?????? ??????: "),
                                text("+  ").append(cmd("d-sudo (????????????) (?????????)")).append(Component.text(": ??????????????? ????????? ????????? ??????????????? ?????????.")),
                                text("+  ").append(cmd("d-freset")).append(Component.text(": ????????? ????????? ??????????????????."))
                            )
                        }

                        helpMsg.forEach (sender::sendMessage)
                    }
                }
                then("conf-gui"){
                    executes {
                        val gui = ChestGui(5, "[${ChatColor.RED}?????????${ChatColor.RESET}] ??????")

                        val bg = OutlinePane(9, 5)
                        bg.addItem(
                            namedItem(
                                ItemStack(
                                    Material.BLACK_STAINED_GLASS_PANE
                                ),
                                Component.text("")
                            )
                        )
                        bg.setRepeat(true)

                        val mainPane = StaticPane(1, 1, 7, 3, Pane.Priority.HIGHEST)
                        mainPane.addItem(
                            namedItem(
                                ItemStack(
                                    if(MafiaConfig.isOnDebug) Material.LIME_WOOL else Material.RED_WOOL,
                                ),
                                Component.text("????????? ??????").color(NamedTextColor.GRAY),
                                listOf(
                                    Component.text("????????? ??????: ").color(NamedTextColor.GRAY).append(
                                        if(MafiaConfig.isOnDebug) Component.text("??????").color(NamedTextColor.GREEN)
                                        else Component.text("??????").color(NamedTextColor.RED)
                                    ),
                                    Component.text("???????????? ????????? ????????? ${if(MafiaConfig.isOnDebug) "???" else ""}???????????? ??? ????????????.").color(NamedTextColor.GRAY)
                                ).map {
                                    it.decoration(TextDecoration.ITALIC, false)
                                }
                            ) {
                                MafiaConfig.isOnDebug = !MafiaConfig.isOnDebug

                                it.clickedInventory?.close()
                                if(it.whoClicked is Player) (it.whoClicked as Player).chat("/mafia conf-gui")

                                it.isCancelled = true
                            },
                            2,
                            0
                        )

                        mainPane.addItem(
                            namedItem(
                                ItemStack(
                                    if(displayGuide) Material.LIME_WOOL else Material.RED_WOOL,
                                ),
                                Component.text("?????????").color(NamedTextColor.GRAY),
                                listOf(
                                    Component.text("?????????: ").color(NamedTextColor.GRAY).append(
                                        if(displayGuide) Component.text("??????").color(NamedTextColor.GREEN)
                                        else Component.text("??????").color(NamedTextColor.RED)
                                    ),
                                    Component.text("???????????? ???????????? ${if(displayGuide) "???" else ""}???????????? ??? ????????????.").color(NamedTextColor.GRAY)
                                ).map {
                                    it.decoration(TextDecoration.ITALIC, false)
                                }
                            ) {
                                displayGuide = !displayGuide

                                it.clickedInventory?.close()
                                if(it.whoClicked is Player) (it.whoClicked as Player).chat("/mafia conf-gui")

                                it.isCancelled = true
                            },
                            4,
                            2
                        )

                        mainPane.addItem(
                            namedItem(
                                ItemStack(
                                    if(MafiaConfig.autoChangeTimeByGameState) Material.LIME_WOOL else Material.RED_WOOL,
                                ),
                                Component.text("???/?????? ?????????????????? ?????? ?????????").color(NamedTextColor.GRAY),
                                listOf(
                                    Component.text("???/?????? ?????????????????? ?????? ?????????: ").color(NamedTextColor.GRAY).append(
                                        if(MafiaConfig.autoChangeTimeByGameState) Component.text("??????").color(NamedTextColor.GREEN)
                                        else Component.text("??????").color(NamedTextColor.RED)
                                    ),
                                    Component.text("???????????? ???/?????? ?????????????????? ?????? ???????????? ${if(MafiaConfig.autoChangeTimeByGameState) "???" else ""}???????????? ??? ????????????.").color(NamedTextColor.GRAY)
                                ).map {
                                    it.decoration(TextDecoration.ITALIC, false)
                                }
                            ) {
                                MafiaConfig.autoChangeTimeByGameState = !MafiaConfig.autoChangeTimeByGameState

                                it.clickedInventory?.close()
                                if(it.whoClicked is Player) (it.whoClicked as Player).chat("/mafia conf-gui")

                                it.isCancelled = true
                            },
                            2,
                            2
                        )

                        mainPane.addItem(
                            namedItem(
                                ItemStack(
                                    if(MafiaConfig.isOnStream) Material.LIME_WOOL else Material.RED_WOOL,
                                ),
                                Component.text("???????????? ??????").color(NamedTextColor.GRAY),
                                listOf(
                                    Component.text("???????????? ??????: ").color(NamedTextColor.GRAY).append(
                                        if(MafiaConfig.isOnStream) Component.text("??????").color(NamedTextColor.GREEN)
                                        else Component.text("??????").color(NamedTextColor.RED)
                                    ),
                                    Component.text("???????????? ???????????? ????????? ${if(MafiaConfig.isOnStream) "???" else ""}???????????? ??? ????????????.").color(NamedTextColor.GRAY)
                                ).map {
                                    it.decoration(TextDecoration.ITALIC, false)
                                }
                            ) {
                                MafiaConfig.isOnStream = !MafiaConfig.isOnStream

                                it.clickedInventory?.close()
                                if(it.whoClicked is Player) (it.whoClicked as Player).chat("/mafia conf-gui")

                                it.isCancelled = true
                            },
                            4,
                            0
                        )

                        gui.addPane(bg)
                        gui.addPane(mainPane)

                        gui.update()
                        gui.show(player)
                    }
                }
                then("start"){
                    executes {
                        if(isPlaying) {
                            sender.sendMessage(text("?????? ????????? ?????? ????????????."))
                            return@executes
                        }
                        isPlaying = true

                        server.onlinePlayers.filter { observers.contains(it.uniqueId) }.forEach { it.profession = MafiaProfession.OBSERVER }

                        var players = server.onlinePlayers.filter { it.profession == MafiaProfession.NONE }.shuffled()
                        val playerCount = players.size

                        server.onlinePlayers.forEach {
                            it.profession = MafiaProfession.NONE
                        }

                        server.broadcast(text("?????? ????????? ???????????????."))

                        randThread = Thread {
                            Thread.sleep(3000)

                            MafiaProfession.values().filter { it != MafiaProfession.NONE && it != MafiaProfession.OBSERVER && it != MafiaProfession.INNOCENT }.forEach {
                                for (i in 0 until getMaxProfessionCount(it, playerCount)){
                                    try {
                                        players[i].profession = it
                                    }
                                    catch(e: IndexOutOfBoundsException){
                                        return@forEach
                                    }
                                    players[i].sendMessage(text("????????? ????????? ").append(Component.text(it.toString()).color(it.getColor())).append(Component.text("?????????.")))
                                    players[i].playSound(players[i], Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.615f)
                                    players[i].showTitle(
                                        Title.title(
                                            Component.text(it.toString()).color(it.getColor()),
                                            Component.text(""),
                                            Title.Times.times(
                                                Duration.ZERO,
                                                Duration.ofMillis(50L * 50L),
                                                Duration.ZERO
                                            )
                                        )
                                    )

                                    logger.info(players[i].profession.toString())

                                    Thread.sleep(750)
                                }

                                players = server.onlinePlayers.filter { it.profession == MafiaProfession.NONE }

                                if(players.isEmpty()){
                                    return@forEach
                                }
                                else {
                                    players = players.shuffled()
                                }
                            }

                            server.onlinePlayers.filter { it.profession == MafiaProfession.NONE }.forEach {
                                try {
                                    it.profession = MafiaProfession.INNOCENT
                                }
                                catch(e: IndexOutOfBoundsException){
                                    return@forEach
                                }
                                it.sendMessage(text("????????? ????????? ").append(Component.text(MafiaProfession.INNOCENT.toString()).color(MafiaProfession.INNOCENT.getColor())).append(Component.text("?????????.")))
                                it.playSound(it, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.615f)
                                it.showTitle(
                                    Title.title(
                                        Component.text(MafiaProfession.INNOCENT.toString()).color(MafiaProfession.INNOCENT.getColor()),
                                        Component.text(""),
                                        Title.Times.times(
                                            Duration.ZERO,
                                            Duration.ofMillis(50L * 50L),
                                            Duration.ZERO
                                        )
                                    )
                                )

                                logger.info(it.profession.toString())
                            }

                            server.broadcast(text("?????? ????????? ???????????????."))

                            return@Thread
                        }

                        randThread.start()

                        while(randThread.state != Thread.State.TERMINATED){}

                        var fl = false

                        MafiaProfession.values().filter { it != MafiaProfession.NONE && it != MafiaProfession.OBSERVER && it != MafiaProfession.INNOCENT && it != MafiaProfession.JUNSEOK && it != MafiaProfession.COUPLE }.forEach { prof ->
                            server.onlinePlayers.filter { it.profession == prof }.forEach { pl ->
                                pl.sendMessage(
                                    text("????????? ????????? ")
                                        .append {
                                            var comp = Component.text()
                                            val lis = mutableListOf<Component>()

                                            server.onlinePlayers.filter { it.profession == prof && it != pl }.forEach {
                                                lis.add(it.displayName())
                                                lis.add(Component.text("???, "))
                                            }

                                            if(lis.isEmpty()){
                                                fl = true
                                            }
                                            else {
                                                lis.removeLast()
                                            }

                                            lis.forEach {
                                                comp = comp.append(it)
                                            }

                                            comp.build()
                                        }
                                        .append(Component.text(if(fl) "????????????." else "????????????."))
                                )
                            }
                        }

                        val _couple = server.onlinePlayers.filter { it.profession == MafiaProfession.COUPLE }
                        if(_couple.isNotEmpty()){
                            couples = Pair(Pair(_couple[0], 2), Pair(_couple[1], 2))
                        }

                        _couple.forEach {
                            it.sendMessage(
                                text(
                                    "????????? ????????? "
                                )
                                    .append(
                                        (if(it == _couple[0]) _couple[1] else _couple[0]).displayName()
                                    )
                                    .append(
                                        Component.text("????????????.")
                                    )
                            )
                        }

                        day = 0
                        isNight = false

                        voted.removeAll(voted)
                        voteMap = mutableMapOf()
                        abilityUsed.removeAll(abilityUsed)
                        targetMap = mutableMapOf()

                        server.onlinePlayers.filter { it.profession != MafiaProfession.OBSERVER && it.profession != MafiaProfession.NONE }.forEach {
                            it.isAlive = true
                        }

                        gameMainThread = Thread {
                            while(
                                true
                            ){
                                isNight = false
                                day += 1

                                voted.removeAll(voted)
                                voteMap = mutableMapOf()

                                server.onlinePlayers.forEach {
                                    it.showTitle(
                                        Title.title(
                                            Component.text("???").color(NamedTextColor.GOLD),
                                            Component.text("${day}??????").color(NamedTextColor.GRAY),
                                            Title.Times.times(
                                                Duration.ofMillis(50L * 10L),
                                                Duration.ofMillis(50L * 20L),
                                                Duration.ofMillis(50L * 10L)
                                            )
                                        )
                                    )

                                    // TODO ????????? ?????? ?????? ??????
                                }
                                
                                if(Random.nextInt(0, 100) < 22) {
					                if(couples != null){
                                        val aids = couples!!.toList().random().first
                                        
                                        aids.isAIDS = true
                                        aids.sendMessage(text("${if(isOnStream) "" else "???"}?????? ???????????????. ????????? ????????? ?????? ?????? ?????? ?????? ?????? ???????????????."))
                                    }
			                	}

                                if(day > 1){
                                    if(
                                        couples != null
                                    ) {
                                        if(!couples!!.first.first.isAlive || !couples!!.second.first.isAlive){
                                            server.broadcast(
                                                text(
                                                    "????????? ???????????? ????????? ?????? "
                                                )
                                                .append(
                                                    couples!!.toList().first { it.first.isAlive }.first.displayName()
                                                )
                                                .append(
                                                    Component.text("?????? ?????? ?????? ????????? ????????? ?????????????????????.")
                                                )
                                            )
                                        }
                                        
                                        if(!couples!!.first.first.isAlive) {
                                                couples!!.second.first.isAlive = false
                                        }
                                        else {
                                            couples!!.first.first.isAlive = false
                                        }
                                    }
                                    
                                    val woowoowoo = targetMap[MafiaProfession.POLICE]?.random()

                                    if(woowoowoo != null) {
                                        if(server.getPlayer(woowoowoo)!!.isPlayer() && !server.getPlayer(woowoowoo)!!.isInnocent()){
                                            server.onlinePlayers.filter { it.profession == MafiaProfession.POLICE }.forEach {
                                                it.sendMessage(text("").append(server.getPlayer(woowoowoo)!!.displayName()).append(Component.text("?????? ???????????? ??????????????????.")))
                                            }
                                        }
                                        else {
                                            server.onlinePlayers.filter { it.profession == MafiaProfession.POLICE }.forEach {
                                                it.sendMessage(text("").append(server.getPlayer(woowoowoo)!!.displayName()).append(Component.text("?????? ????????? ???????????? ??????????????????.")))
                                            }
                                        }
                                    }

                                    val willDead = targetMap[MafiaProfession.MAFIA]?.random()
                                    var willCured = targetMap[MafiaProfession.MEDIC]?.random()

                                    if(willDead != null){
                                        if(willCured != null){
                                            if(willDead == willCured){
                                                server.broadcast(
                                                    text("?????? ??? ???????????? ?????? ????????? ????????? ")
                                                        .append(server.getPlayer(willDead)!!.displayName())
                                                        .append(Component.text("?????? ??????????????? ?????????????????????."))
                                                )
                                            }
                                            else {
                                                server.broadcast(
                                                    text("?????? ??? ???????????? ?????? ????????? ????????? ")
                                                        .append(server.getPlayer(willDead)!!.displayName())
                                                        .append(Component.text("?????? ?????? ?????? ????????? ???????????? ?????????????????????."))
                                                )

                                                server.onlinePlayers.forEach {
                                                    it.playSound(it, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f)
                                                }

                                                server.getPlayer(willDead)!!.isAlive = false
                                            }

                                            willCured = null
                                        }
                                        else {
                                            server.broadcast(
                                                text("?????? ??? ???????????? ?????? ????????? ????????? ")
                                                    .append(server.getPlayer(willDead)!!.displayName())
                                                    .append(Component.text("?????? ?????? ?????? ????????? ???????????? ?????????????????????."))
                                            )

                                            server.onlinePlayers.forEach {
                                                it.playSound(it, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f)
                                            }

                                            server.getPlayer(willDead)!!.isAlive = false
                                        }
                                    }

                                    if(willCured != null) {
                                        if(server.getPlayer(willCured)!!.isAIDS) {
                                            server.getPlayer(willCured)!!.isAIDS = false

                                            if(couples!!.first.first.uniqueId == willCured){
                                                couples = Pair(Pair(couples!!.second.first, 2), couples!!.second)
                                            }
                                            else {
                                                couples = Pair(couples!!.first, Pair(couples!!.second.first, 2))
                                            }

                                            server.broadcast(
                                                text("????????? ")
                                                    .append(server.getPlayer(willCured)!!.displayName())
                                                    .append(Component.text("?????? ${if (isOnStream) "" else "???"}?????? ??????????????????."))
                                            )
                                        }
                                    }
                                }

                                if(!(server.onlinePlayers.filter { it.isPlayer() && !it.isInnocent() }.count { it.isAlive } < (server.onlinePlayers.filter { it.isInnocent() }.count { it.isAlive } - 1) &&
                                            server.onlinePlayers.filter { it.isPlayer() && !it.isInnocent() }.any { it.isAlive })){
                                    break
                                }

                                if(displayGuide) {
                                    server.broadcast(text("????????? \"?????? (?????????)\"????????? ????????? ????????? ???????????????."))
                                    server.broadcast(text("????????? ???????????? ????????? ???????????????. ?????? ????????? ???????????????."))
                                    server.broadcast(text("?????? ?????? ?????? ?????? ????????? ??? ?????? ????????? ?????? ????????? ??????????????? ????????????."))
                                    server.broadcast(text("??? ???????????? ?????? ?????? ????????? ???????????? \"?????????\"??? ????????????????????????."))
                                }

                                voteSum = 0

                                while(server.onlinePlayers.count { it.isPlayer() && it.isAlive } > voteSum){}

                                server.broadcast(text("????????? ?????? ???????????????."))

                                Thread.sleep(1750L)

                                val voteResultThread = Thread ThreadV@{
                                    val resr = voteMap.maxBy { it.value }
                                    val result = resr.key

                                    if(voteMap.values.count { it == resr.value } > 1){
                                        voteMap.forEach {
                                            server.broadcast(
                                                text("")
                                                    .append(server.getPlayer(it.key)!!.displayName())
                                                    .append(Component.text(": ${it.value}???"))
                                            )

                                            Thread.sleep(1000L)
                                        }

                                        Thread.sleep(1750L)

                                        server.broadcast(text("?????? ?????? ?????? ?????? ????????? ??? ?????? ??????????????? ????????? ??????????????? ????????????."))

                                        return@ThreadV
                                    }

                                    voteMap.forEach {
                                        server.broadcast(
                                            text("")
                                                .append(server.getPlayer(it.key)!!.displayName())
                                                .append(Component.text(": ${it.value}???"))
                                                .append(Component.text(
                                                    if(result == it.key) {
                                                        "??? ??????"
                                                    }
                                                    else ""
                                                ))
                                        )

                                        Thread.sleep(1000L)
                                    }

                                    Thread.sleep(1750L)

                                    if(server.getPlayer(result)!!.profession == MafiaProfession.JUNSEOK){
                                        server.broadcast(text("").append(server.getPlayer(result)!!.displayName()).append(Component.text(": ")).append(Component.text(MafiaProfession.JUNSEOK.toString()).color(MafiaProfession.JUNSEOK.getColor())).append(Component.text("????????? ?????? ????????????.")))
                                    }
                                    else {
                                        server.broadcast(text("").append(server.getPlayer(result)!!.displayName()).append(Component.text(": ??????!")))

                                        Thread.sleep(500L)
                                        server.onlinePlayers.forEach {
                                            it.playSound(it, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f)
                                        }

                                        server.getPlayer(result)!!.isAlive = false
                                    }

                                    return@ThreadV
                                }

                                voteResultThread.start()

                                while(voteResultThread.state != Thread.State.TERMINATED){}

                                if(((server.onlinePlayers.filter { it.isPlayer() && !it.isInnocent() }.count { it.isAlive } >= server.onlinePlayers.filter { it.isInnocent() }.count { it.isAlive })
                                            ||
                                            server.onlinePlayers.filter { it.isPlayer() && !it.isInnocent() }.none { it.isAlive })){
                                    break
                                }

                                isNight = true
                                abilityUsed.removeAll(abilityUsed)
                                targetMap = mutableMapOf()

                                server.onlinePlayers.forEach {
                                    it.showTitle(
                                        Title.title(
                                            Component.text("???").color(NamedTextColor.DARK_BLUE),
                                            Component.text("${day}??????").color(NamedTextColor.GRAY),
                                            Title.Times.times(
                                                Duration.ofMillis(50L * 10L),
                                                Duration.ofMillis(50L * 20L),
                                                Duration.ofMillis(50L * 10L)
                                            )
                                        )
                                    )

                                    // TODO ????????? ?????? ?????? ??????
                                }

                                if(displayGuide) {
                                    server.broadcast(text("????????? \"?????? (?????????)\"????????? ????????? ?????? ????????? ???????????????."))
                                    server.broadcast(text("?????? ????????? ??? ????????? ?????? ?????? ???????????? ????????? ????????? ?????? ?????? ????????? ????????? ??? ???????????? ?????? ????????? ???????????????."))
                                    server.broadcast(text("????????? ????????? ???????????? ????????? ???????????????. ?????? ????????? ????????? ???????????????."))
                                    server.broadcast(text("??? ???????????? ?????? ?????? ????????? ???????????? \"?????????\"??? ????????????????????????."))
                                }

                                while(abilityUsed.size < server.onlinePlayers.count { it.isSpecial() && it.isAlive }){}
                            }

                            if(server.onlinePlayers.filter { it.isPlayer() && !it.isInnocent() }.none { it.isAlive }){
                                server.broadcast(text("???????????? ??? ??????????????? ?????? ?????? ???????????????!"))
                            }
                            else {
                                server.broadcast(text("???????????? ????????? ?????? ??????????????? ??? ???????????? ????????? ?????? ???????????????!"))
                            }

                            server.onlinePlayers.filter { it.isPlayer() }.forEach {
                                server.broadcast(
                                    text("")
                                        .append(it.displayName())
                                        .append(Component.text(": "))
                                        .append(Component.text(it.profession.toString())).color(it.profession.getColor())
                                )
                            }

                            isPlaying = false

                            day = 0
                            isNight = false

                            voted.removeAll(voted)
                            voteMap = mutableMapOf()
                            abilityUsed.removeAll(abilityUsed)
                            targetMap = mutableMapOf()

                            voteSum = 0

                            server.onlinePlayers.forEach {
                                it.profession = MafiaProfession.NONE
                                it.isAlive = false
                            }
                        }

                        gameMainThread.name = "????????? ?????? ?????? ?????????"

                        gameMainThread.start()
                    }
                }
            }
        }
    }
}
