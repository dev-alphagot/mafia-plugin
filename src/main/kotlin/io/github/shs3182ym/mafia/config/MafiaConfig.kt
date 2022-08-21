package io.github.shs3182ym.mafia.config

import io.github.monun.tap.config.Config
import io.github.monun.tap.config.ConfigSupport
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.reflect.full.memberProperties

@Suppress("UNUSED")
object MafiaConfig {
    private fun String.toConfigKey(): String {
        val builder = StringBuilder(this)

        var i = 0

        while (i < builder.count()) {
            val c = builder[i]

            if (c.isUpperCase()) {
                builder[i] = c.lowercaseChar()

                if (i > 0) {
                    builder.insert(i, '-')
                    i++
                }
            }

            i++
        }

        return builder.toString()
    }

    @Config
    var msgPrefix: String = GsonComponentSerializer.gson().serialize(Component.text("[")
        .append(Component.text("마피아").color(NamedTextColor.RED))
        .append(Component.text("] ")))

    @Config
    var isOnDebug: Boolean = true

    @Config
    var displayGuide: Boolean = true

    @Config
    var autoChangeTimeByGameState: Boolean = true

    @Config
    var isOnStream: Boolean = false

    fun load(configFile: File) {
        try {
            ConfigSupport.compute(this, configFile)
        }
        catch(_: java.lang.IllegalArgumentException) {
            getInstance().logger.warning("설정 파일이 올바르지 않습니다. 기본 설정으로 동작합니다.")
        }
    }

    fun save(configFile: File) {
        try {
            val conf = YamlConfiguration()

            this::class.memberProperties.forEach {
                if(isOnDebug){
                    getInstance().logger.info(it.name.toConfigKey())
                }

                conf[it.name.toConfigKey()] = it.getter.call(this)
            }

            conf.save(configFile)
        }
        catch(e: Exception){
            e.printStackTrace()

            getInstance().logger.warning("설정 저장에 실패했습니다.")
        }
    }
}