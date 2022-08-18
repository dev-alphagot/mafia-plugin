package io.github.shs3182ym.mafia

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.monun.kommand.StringType
import io.github.monun.kommand.getValue
import io.github.monun.kommand.kommand
import io.github.shs3182ym.mafia.config.*
import io.github.shs3182ym.mafia.config.MafiaConfig.autoChangeTimeByGameState
import io.github.shs3182ym.mafia.config.MafiaConfig.displayGuide
import io.github.shs3182ym.mafia.config.MafiaConfig.isOnDebug
import io.github.shs3182ym.mafia.config.MafiaConfig.load
import io.github.shs3182ym.mafia.config.MafiaConfig.msgPrefix
import io.github.shs3182ym.mafia.profession.MafiaProfession
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
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
import java.io.File
import java.time.Duration
import java.util.*
import java.util.function.Consumer

@Suppress("ControlFlowWithEmptyBody")
class Main : JavaPlugin(), Listener {
    private fun text(t: String): Component = GsonComponentSerializer.gson().deserialize(msgPrefix).append(Component.text(t))
    private fun cmd(t: String): Component = Component.text(t).color(NamedTextColor.LIGHT_PURPLE)


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

    @EventHandler
    fun onChat(e: AsyncChatEvent) {
		if(!isPlaying) return
	
        if(!e.player.isAlive) {
            e.isCancelled = true
            return
        }

        if(e.player.isPlayer()) {
			logger.info("${e.player.name}: ${PlainTextComponentSerializer.plainText().serialize(e.originalMessage())}")
		
            if(
				PlainTextComponentSerializer.plainText().serialize(e.originalMessage()).startsWith("투표 ") ||
				PlainTextComponentSerializer.plainText().serialize(e.originalMessage()).startsWith("능력 ")
			){
                e.isCancelled = true

                val text = PlainTextComponentSerializer.plainText().serialize(e.originalMessage())

                when(text.split(" ")[0]){
                    "투표" -> {
                        val text = text.replace("투표 ", "")

                        if(server.onlinePlayers.any { it.name == text }){
                            if(voted.contains(e.player.uniqueId)){
                                e.player.sendMessage(text("이미 투표를 했습니다."))
                                return
                            }

                            if(!voteMap.containsKey(server.onlinePlayers.first { it.name == text }.uniqueId)) voteMap[server.onlinePlayers.first { it.name == text }.uniqueId] = 1
                            else voteMap[server.onlinePlayers.first { it.name == text }.uniqueId] = voteMap[server.onlinePlayers.first { it.name == text }.uniqueId]!! + 1

                            voted.add(e.player.uniqueId)
                            e.player.sendMessage(text("투표가 완료되었습니다."))
                        }
                    }
                    "능력" -> {
                        val text = text.replace("능력 ", "")

                        if(server.onlinePlayers.any { it.name == text }){
                            if(abilityUsed.contains(e.player.uniqueId)){
                                e.player.sendMessage(text("이미 능력을 사용했습니다."))
                                return
                            }

                            val targetID = server.onlinePlayers.first { it.name == text }.uniqueId

                            if(targetMap.containsKey(e.player.profession)) targetMap[e.player.profession]!!.add(targetID)
                            else targetMap[e.player.profession] = mutableListOf(targetID)

                            abilityUsed.add(e.player.uniqueId)
                            e.player.sendMessage(text("능력을 사용했습니다."))
                        }
                    }
                }
            }
            else {
                if(isNight) {
                    e.isCancelled = true
                    return
                }
                else {
                    e.message(Component.text(" (${day}일차): ").append(e.originalMessage()))
                }
            }
        }
    }

    override fun onEnable() {
        instance = this
        load(configFile)
		
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
            mta.displayName(n)
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
                        text("마피아 플러그인 버전 ${instance.description.version}"),
                        text(" by AlphaGot#0388"),
                        text("명령어 목록을 보려면 ").append(cmd("/mafia help")).append(Component.text(" 명령어를 사용하세요."))
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

                        server.onlinePlayers.forEach {
                            it.profession = MafiaProfession.NONE
                            it.isAlive = false
                        }

                        sender.sendMessage(text("게임 강제 초기화 완료"))
                    }
                }
                then("help"){
                    executes {
                        arrayOf(
                            text("도움말 (1/1)"),
                            text("").append(cmd("/mafia")).append(Component.text(": 버전 및 개발자를 볼 수 있습니다.")),
                            text("+  ").append(cmd("help")).append(Component.text(": 도움말을 볼 수 있습니다.")),
                            text("+  ").append(cmd("conf-gui")).append(Component.text(": 게임 설정 GUI를 띄웁니다.")),
                            text("+  ").append(cmd("start")).append(Component.text(": 게임을 시작합니다.")),
                            text("─".repeat(18)),
                            text("디버그 모드 전용: "),
                            text("+  ").append(cmd("d-sudo (플레이어) (명령어)")).append(Component.text(": 플레이어가 강제로 명령을 실행하도록 합니다.")),
                            text("+  ").append(cmd("d-freset")).append(Component.text(": 게임을 강제로 초기화합니다."))
                        ).forEach (sender::sendMessage)
                    }
                }
                then("conf-gui"){
                    executes {
                        val gui = ChestGui(5, "[${ChatColor.RED}마피아${ChatColor.RESET}] 설정")

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
                                    if(MafiaConfig.isOnDebug) Material.GREEN_WOOL else Material.RED_WOOL,
                                ),
                                Component.text("디버그 모드").color(NamedTextColor.GRAY),
                                listOf(
                                    Component.text("디버그 모드: ").color(NamedTextColor.GRAY).append(
                                        if(MafiaConfig.isOnDebug) Component.text("켜짐").color(NamedTextColor.GREEN)
                                        else Component.text("꺼짐").color(NamedTextColor.RED)
                                    ),
                                    Component.text("클릭하여 디버그 모드를 ${if(MafiaConfig.isOnDebug) "비" else ""}활성화할 수 있습니다.").color(NamedTextColor.GRAY)
                                ).map {
                                    it.decoration(TextDecoration.ITALIC, false)
                                }
                            ) {
                                MafiaConfig.isOnDebug = !MafiaConfig.isOnDebug

                                it.clickedInventory?.close()
                                if(it.whoClicked is Player) (it.whoClicked as Player).chat("/mafia conf-gui")

                                it.isCancelled = true
                            },
                            3,
                            0
                        )

                        mainPane.addItem(
                            namedItem(
                                ItemStack(
                                    if(displayGuide) Material.GREEN_WOOL else Material.RED_WOOL,
                                ),
                                Component.text("가이드").color(NamedTextColor.GRAY),
                                listOf(
                                    Component.text("가이드: ").color(NamedTextColor.GRAY).append(
                                        if(displayGuide) Component.text("켜짐").color(NamedTextColor.GREEN)
                                        else Component.text("꺼짐").color(NamedTextColor.RED)
                                    ),
                                    Component.text("클릭하여 가이드를 ${if(displayGuide) "비" else ""}활성화할 수 있습니다.").color(NamedTextColor.GRAY)
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
                                    if(MafiaConfig.autoChangeTimeByGameState) Material.GREEN_WOOL else Material.RED_WOOL,
                                ),
                                Component.text("낮/밤과 마인크래프트 시간 동기화").color(NamedTextColor.GRAY),
                                listOf(
                                    Component.text("낮/밤과 마인크래프트 시간 동기화: ").color(NamedTextColor.GRAY).append(
                                        if(MafiaConfig.autoChangeTimeByGameState) Component.text("켜짐").color(NamedTextColor.GREEN)
                                        else Component.text("꺼짐").color(NamedTextColor.RED)
                                    ),
                                    Component.text("클릭하여 낮/밤과 마인크래프트 시간 동기화를 ${if(MafiaConfig.autoChangeTimeByGameState) "비" else ""}활성화할 수 있습니다.").color(NamedTextColor.GRAY)
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

                        gui.addPane(bg)
                        gui.addPane(mainPane)

                        gui.update()
                        gui.show(player)
                    }
                }
                then("start"){
                    executes {
                        if(isPlaying) {
                            sender.sendMessage(text("이미 게임이 진행 중입니다."))
                            return@executes
                        }
                        isPlaying = true

                        server.onlinePlayers.filter { observers.contains(it.uniqueId) }.forEach { it.profession = MafiaProfession.OBSERVER }

                        var players = server.onlinePlayers.filter { it.profession == MafiaProfession.NONE }.shuffled()
                        val playerCount = players.size

                        server.broadcast(text("직업 추첨을 시작합니다."))

                        val titleTask = object: BukkitRunnable() {
                            override fun run() {
                                server.onlinePlayers.filter { it.profession != MafiaProfession.OBSERVER }.forEach {
                                    it.showTitle(
                                        Title.title(
                                            Component.empty(),
                                            Component.text("직업 추첨 중!").color(NamedTextColor.GRAY),
                                            Title.Times.times(
                                                Duration.ZERO,
                                                Duration.ofMillis(50L * 22L),
                                                Duration.ZERO
                                            )
                                        )
                                    )
                                }
                            }
                        }.runTaskTimer(
                            instance,
                            0L,
                            20L
                        )

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
                                    players[i].sendMessage(text("당신의 직업은 ").append(Component.text(it.toString()).color(it.getColor())).append(Component.text("입니다.")))
                                    players[i].playSound(players[i], Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.615f)
                                    players[i].showTitle(
                                        Title.title(
                                            Component.text(it.toString()).color(it.getColor()),
                                            Component.text("직업 추첨 중!").color(NamedTextColor.GRAY),
                                            Title.Times.times(
                                                Duration.ZERO,
                                                Duration.ofMillis(50L * 50L),
                                                Duration.ZERO
                                            )
                                        )
                                    )

                                    logger.info(players[i].profession.toString())

                                    Thread.sleep(2000)
                                }

                                players = server.onlinePlayers.filter { it.profession == MafiaProfession.NONE }

                                if(players.isEmpty()){
                                    logger.info("asdf")

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
                                it.sendMessage(text("당신의 직업은 ").append(Component.text(MafiaProfession.INNOCENT.toString()).color(MafiaProfession.INNOCENT.getColor())).append(Component.text("입니다.")))
                                it.playSound(it, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.615f)
                                it.showTitle(
                                    Title.title(
                                        Component.text(it.toString()).color(MafiaProfession.INNOCENT.getColor()),
                                        Component.text("직업 추첨 중!").color(NamedTextColor.GRAY),
                                        Title.Times.times(
                                            Duration.ZERO,
                                            Duration.ofMillis(50L * 50L),
                                            Duration.ZERO
                                        )
                                    )
                                )

                                logger.info(it.profession.toString())

                                Thread.sleep(1500L)
                            }

                            titleTask.cancel()

                            server.broadcast(text("직업 추첨이 끝났습니다."))

                            logger.info(server.onlinePlayers.joinToString(" ") { it.profession.toString() })

                            return@Thread
                        }

                        randThread.start()

                        while(randThread.state != Thread.State.TERMINATED){}
						
						var fl = false

                        MafiaProfession.values().filter { it != MafiaProfession.NONE && it != MafiaProfession.OBSERVER && it != MafiaProfession.INNOCENT && it != MafiaProfession.JUNSEOK }.forEach { prof ->
                            server.onlinePlayers.filter { it.profession == prof }.forEach { pl ->
                                pl.sendMessage(
                                    text("당신의 동료는 ")
                                        .append {
                                            var comp = Component.text()
                                            val lis = mutableListOf<Component>()

                                            server.onlinePlayers.filter { it.profession == prof && it != pl }.forEach {
                                                lis.add(it.displayName())
                                                lis.add(Component.text("님, "))
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
                                        .append(Component.text(if(fl) "없습니다." else "님입니다."))
                                )
                            }
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

                        Thread {
                            while(
                                server.onlinePlayers.count { it.isPlayer() && !it.isInnocent() && it.isAlive } < server.onlinePlayers.count { it.isInnocent() && it.isAlive } ||
                                server.onlinePlayers.any { it.isPlayer() && !it.isInnocent() && it.isAlive }
                            ){
                                isNight = false
                                day += 1

                                // player.world.time = 6000

                                voted.removeAll(voted)
                                voteMap = mutableMapOf()

                                server.onlinePlayers.forEach {
                                    it.showTitle(
                                        Title.title(
                                            Component.text("낮").color(NamedTextColor.GOLD),
                                            Component.text("${day}일차").color(NamedTextColor.GRAY),
                                            Title.Times.times(
                                                Duration.ofMillis(50L * 10L),
                                                Duration.ofMillis(50L * 20L),
                                                Duration.ofMillis(50L * 10L)
                                            )
                                        )
                                    )

                                    // TODO 마이크 관련 기능 추가
                                }

                                if(day > 1){
                                    val woowoowoo = targetMap[MafiaProfession.POLICE]!!.random()

                                    if(server.getPlayer(woowoowoo)!!.isPlayer() && !server.getPlayer(woowoowoo)!!.isInnocent()){
                                        server.onlinePlayers.filter { it.profession == MafiaProfession.POLICE }.forEach {
                                            it.sendMessage(text("").append(server.getPlayer(woowoowoo)!!.displayName()).append(Component.text("님은 마피아로 밝혀졌습니다.")))
                                        }
                                    }
                                    else {
                                        server.onlinePlayers.filter { it.profession == MafiaProfession.POLICE }.forEach {
                                            it.sendMessage(text("").append(server.getPlayer(woowoowoo)!!.displayName()).append(Component.text("님은 무고한 시민으로 밝혀졌습니다.")))
                                        }
                                    }

                                    val willDead = targetMap[MafiaProfession.MAFIA]!!.random()
                                    val willCured = targetMap[MafiaProfession.MEDIC]!!.random()

                                    if(willDead == willCured){
                                        server.broadcast(
                                            text("전날 밤 마피아의 살해 대상이 되었던 ")
                                                .append(server.getPlayer(willDead)!!.displayName())
                                                .append(Component.text("님이 기적적으로 살아남았습니다."))
                                        )
                                    }
                                    else {
                                        server.broadcast(
                                            text("전날 밤 마피아의 살해 대상이 되었던 ")
                                                .append(server.getPlayer(willDead)!!.displayName())
                                                .append(Component.text("님이 오늘 아침 싸늘한 주검으로 발견되었습니다."))
                                        )

                                        server.onlinePlayers.forEach {
                                            it.playSound(it, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f)
                                        }

                                        server.getPlayer(willDead)!!.isAlive = false
                                        // player.chat("/gamemode spectator ${server.getPlayer(willDead)!!.name}")
                                    }
                                }

                                if(displayGuide) {
                                    server.broadcast(text("채팅에 \"투표 (닉네임)\"이라고 적으면 투표가 가능합니다."))
                                    server.broadcast(text("모두가 투표하면 결과가 발표됩니다. 이후 밤으로 넘어갑니다."))
                                    server.broadcast(text("득표 수가 제일 높은 사람이 한 사람 이상일 경우 아무도 사형당하지 않습니다."))
                                    server.broadcast(text("이 메시지가 뜨지 않게 하려면 설정에서 \"가이드\"를 비활성화해주세요."))
                                }

                                while(server.onlinePlayers.count { it.isPlayer() && it.isAlive } > voteMap.values.sum()){}

                                server.broadcast(text("투표가 모두 끝났습니다."))

                                val voteResultThread = Thread ThreadV@{
                                    val resr = voteMap.maxBy { it.value }
                                    val result = resr.key

                                    if(voteMap.values.count { it == resr.value } > 1){
                                        voteMap.forEach {
                                            server.broadcast(
                                                text("")
                                                    .append(server.getPlayer(it.key)!!.displayName())
                                                    .append(Component.text(": ${it.value}표"))
                                            )

                                            Thread.sleep(1000L)
                                        }

                                        Thread.sleep(1750L)

                                        server.broadcast(text("득표 수가 제일 높은 사람이 한 사람 이상이므로 아무도 사형당하지 않습니다."))
                                    }

                                    voteMap.forEach {
                                        server.broadcast(
                                            text("")
                                                .append(server.getPlayer(it.key)!!.displayName())
                                                .append(Component.text(": ${it.value}표"))
                                                .append(Component.text(
                                                    if(result == it.key) {
                                                        "로 사형"
                                                    }
                                                    else ""
                                                ))
                                        )

                                        Thread.sleep(1000L)
                                    }

                                    Thread.sleep(1750L)

                                    if(server.getPlayer(result)!!.profession == MafiaProfession.JUNSEOK){
                                        server.broadcast(text("").append(server.getPlayer(result)!!.displayName()).append(Component.text(": ")).append(Component.text(MafiaProfession.JUNSEOK.toString()).color(MafiaProfession.JUNSEOK.getColor())).append(Component.text("이라서 죽지 않습니다.")))
                                    }
                                    else {
                                        server.broadcast(text("").append(server.getPlayer(result)!!.displayName()).append(Component.text(": 사망!")))

                                        Thread.sleep(500L)
                                        server.onlinePlayers.forEach {
                                            it.playSound(it, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f)
                                        }

                                        server.getPlayer(result)!!.isAlive = false
                                        // player.chat("/gamemode spectator ${server.getPlayer(result)!!.name}")
                                    }

                                    return@ThreadV
                                }

                                voteResultThread.start()

                                while(voteResultThread.state != Thread.State.TERMINATED){}

                                isNight = true
                                abilityUsed.removeAll(abilityUsed)
                                targetMap = mutableMapOf()

                                // player.world.time = 18000

                                server.onlinePlayers.forEach {
                                    it.showTitle(
                                        Title.title(
                                            Component.text("밤").color(NamedTextColor.DARK_BLUE),
                                            Component.text("${day}일차").color(NamedTextColor.GRAY),
                                            Title.Times.times(
                                                Duration.ofMillis(50L * 10L),
                                                Duration.ofMillis(50L * 20L),
                                                Duration.ofMillis(50L * 10L)
                                            )
                                        )
                                    )

                                    // TODO 마이크 관련 기능 추가
                                }

                                if(displayGuide) {
                                    server.broadcast(text("채팅에 \"능력 (닉네임)\"이라고 적으면 능력 사용이 가능합니다."))
                                    server.broadcast(text("같은 직업인 두 사람이 각각 다른 사람에게 능력을 사용할 경우 각각 지목한 사람들 중 무작위로 사용 대상이 지정됩니다."))
                                    server.broadcast(text("모두가 능력을 사용하면 결과가 발표됩니다. 이후 다음날 낮으로 넘어갑니다."))
                                    server.broadcast(text("이 메시지가 뜨지 않게 하려면 설정에서 \"가이드\"를 비활성화해주세요."))
                                }

                                while(abilityUsed.size < server.onlinePlayers.count { it.isSpecial() }){}
                            }

                            if(server.onlinePlayers.none { it.isPlayer() && !it.isInnocent() && it.isAlive }){
                                server.broadcast(text("마피아가 다 죽었으므로 시민 팀의 승리입니다!"))
                            }
                            else {
                                server.broadcast(text("마피아와 시민의 수가 동등하거나 더 적으므로 마피아 팀의 승리입니다!"))
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
						}.start()
                    }
                }
            }
        }
    }
}