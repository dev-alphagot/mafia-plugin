package io.github.shs3182ym.mafia.config

import io.github.shs3182ym.mafia.Main
import io.github.shs3182ym.mafia.profession.MafiaProfession
import io.github.shs3182ym.mafia.profession.MafiaProfession.*
import org.bukkit.entity.Player
import java.util.*

private val playerProfessionStorage: MutableMap<UUID, MafiaProfession> = mutableMapOf()
private val playerAliveStorage: MutableMap<UUID, Boolean> = mutableMapOf()

fun getInstance() = Main.instance

var Player.profession: MafiaProfession
    get() {
        return playerProfessionStorage[this.uniqueId] ?: NONE
    }
    set(value) {
        playerProfessionStorage[this.uniqueId] = value
    }

var Player.isAlive: Boolean
    get() {
        return playerAliveStorage[this.uniqueId] ?: false
    }
    set(value) {
        playerAliveStorage[this.uniqueId] = value
    }

fun getMaxProfessionCount(p: MafiaProfession, c: Int): Int = when(p){
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
    COUPLE -> when(c) {
        in ..8 -> 0
        else -> 2
    }
    POJU -> when(c) -> {
        in ..9 -> 0
        else -> 1
    } 
    else -> -1
}

fun Player?.isInnocent(): Boolean {
    if(this == null) return false

    return arrayOf(
        MEDIC,
        POLICE,
        INNOCENT,
        JUNSEOK,
        COUPLE
    ).contains(this.profession)
}

fun Player?.isPlayer(): Boolean {
    if(this == null) return false

    return !(arrayOf(
        OBSERVER,
        NONE
    ).contains(this.profession))
}

fun Player?.isSpecial(): Boolean { // 능력 보유 여부
    if(this == null) return false

    return arrayOf(
        MEDIC,
        POLICE,
        MAFIA,
        POJU
    ).contains(this.profession)
}
