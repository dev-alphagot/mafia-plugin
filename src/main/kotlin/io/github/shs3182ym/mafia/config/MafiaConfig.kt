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

    @Config
    var displayGuide: Boolean = true

    fun load(configFile: File) {
        try {
            ConfigSupport.compute(this, configFile)
        }
        catch(_: java.lang.IllegalArgumentException) {
            getInstance().logger.warning("설정 파일이 올바르지 않습니다. 기본 설정으로 동작합니다.")
            getInstance().logger.warning("profession-maxes를 설정해주세요.")
        }
    }
}