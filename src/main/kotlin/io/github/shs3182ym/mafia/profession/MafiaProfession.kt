package io.github.shs3182ym.mafia.profession

import io.github.shs3182ym.mafia.config.MafiaConfig
import io.github.shs3182ym.mafia.config.getInstance
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.ChatColor
import kotlin.random.Random
import kotlin.random.nextInt

enum class MafiaProfession {
    NONE,
    INNOCENT,
    MAFIA,
    MEDIC,
    POLICE,
    JUNSEOK, // 정치인
    OBSERVER;

    override fun toString(): String {
        return when(this){
            NONE -> "무직"
            INNOCENT -> "시민"
            MAFIA -> "마피아"
            MEDIC -> "의사"
            POLICE -> "경찰"
            JUNSEOK -> {
                val rni = Random.nextInt(0..200)

                if(MafiaConfig.isOnDebug) getInstance().logger.info("$rni")

                if(rni == 0) "${ChatColor.STRIKETHROUGH}이준석${ChatColor.RESET}정치인"
                else "정치인"
            }
            OBSERVER -> "관전자"
        }
    }

    fun getColor(): TextColor {
        return when(this) {
            NONE -> NamedTextColor.WHITE
            INNOCENT -> NamedTextColor.GRAY
            MAFIA -> TextColor.color(150, 0, 0)
            MEDIC -> TextColor.color(22, 222, 22)
            POLICE -> TextColor.color(44, 44, 222)
            JUNSEOK -> TextColor.color(0xE61E2B)
            OBSERVER -> NamedTextColor.WHITE
        }
    }
}