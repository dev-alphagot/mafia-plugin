package io.github.shs3182ym.mafia

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.monun.kommand.kommand
import io.github.shs3182ym.mafia.config.MafiaConfig
import io.github.shs3182ym.mafia.config.MafiaConfig.load
import io.github.shs3182ym.mafia.config.MafiaConfig.msgPrefix
import io.github.shs3182ym.mafia.config.getMaxProfessionCount
import io.github.shs3182ym.mafia.config.profession
import io.github.shs3182ym.mafia.profession.MafiaProfession
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.time.Duration
import java.util.*
import java.util.function.Consumer

class Main : JavaPlugin() {

    companion object {
        lateinit var instance: Main
            private set
    }

    private val configFile = File(dataFolder, "config.yml")
    val observers: MutableList<UUID> = mutableListOf()

    lateinit var randThread: Thread

    override fun onEnable() {
        instance = this
        load(configFile)

        val ic: Consumer<InventoryClickEvent> = Consumer {
            it.isCancelled = true
        }

        fun text(t: String): Component = GsonComponentSerializer.gson().deserialize(msgPrefix).append(Component.text(t))
        fun cmd(t: String): Component = Component.text(t).color(NamedTextColor.LIGHT_PURPLE)

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
                then("help"){
                    executes {
                        arrayOf(
                            text("도움말 (1/1)"),
                            text("").append(cmd("/mafia")).append(Component.text(": 버전 및 개발자를 볼 수 있습니다.")),
                            text("+  ").append(cmd("help")).append(Component.text(": 도움말을 볼 수 있습니다.")),
                            text("+  ").append(cmd("conf-gui")).append(Component.text(": 게임 설정 GUI를 띄웁니다."))
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

                        gui.addPane(bg)
                        gui.addPane(mainPane)

                        gui.update()
                        gui.show(player)
                    }
                }
                then("start"){
                    executes {
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

                            MafiaProfession.values().filter { it != MafiaProfession.NONE && it != MafiaProfession.OBSERVER }.forEach {
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

                            titleTask.cancel()

                            server.broadcast(text("직업 추첨이 끝났습니다."))

                            return@Thread
                        }

                        randThread.start()
                    }
                }
            }
        }
    }
}