package io.github.shs3182ym.mafia.config

import io.github.monun.tap.config.Config
import io.github.monun.tap.config.ConfigSupport
import io.github.monun.tap.config.RangeInt
import io.github.shs3182ym.mafia.profession.MafiaProfession
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.io.File

@Suppress("UNUSED")
object MafiaConfig {
    @Config
    var professionMaxes: MutableMap<MafiaProfession, Int> = mutableMapOf()

    @Config
    var msgPrefix: String = GsonComponentSerializer.gson().serialize(Component.text("[")
        .append(Component.text("마피아").color(NamedTextColor.RED))
        .append(Component.text("] ")))

    @Config
    var isOnDebug: Boolean = true

    fun load(configFile: File) {
        ConfigSupport.compute(this, configFile)
    }
}