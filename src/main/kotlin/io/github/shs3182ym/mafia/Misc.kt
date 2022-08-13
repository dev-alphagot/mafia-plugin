package io.github.shs3182ym.mafia.config

import io.github.shs3182ym.mafia.Main
import io.github.shs3182ym.mafia.profession.MafiaProfession
import io.github.shs3182ym.mafia.profession.MafiaProfession.*
import org.bukkit.entity.Player
import java.util.*

private val playerProfessionStorage: MutableMap<UUID, MafiaProfession> = mutableMapOf()

fun getInstance() = Main.instance

var Player.profession: MafiaProfession
    get() {
        return playerProfessionStorage[this.uniqueId] ?: NONE
    }
    set(value) {
        playerProfessionStorage[this.uniqueId] = value
    }

fun getMaxProfessionCountUnspecified(p: MafiaProfession, c: Int): Int = when(p){
    MAFIA -> {
        when(c){
            in 0..6 -> 1
            in 7..10 -> 2
            else -> 3
        }
    }
    MEDIC, POLICE -> when(c){
        in 0..9 -> 1
        else -> 2
    }
    JUNSEOK -> when(c){
        in 0..7 -> 0
        else -> 1
    }
    else -> -1
}

fun getMaxProfessionCount(p: MafiaProfession, c: Int): Int = MafiaConfig.professionMaxes?.get(p) ?: getMaxProfessionCountUnspecified(p, c)