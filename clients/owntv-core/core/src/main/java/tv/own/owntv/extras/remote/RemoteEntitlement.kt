// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

/**
 * Gate interface for the Phone Remote feature.
 * In the free version, this always returns true.
 * Ready for future entitlement/subscription checks without architectural changes.
 */
interface RemoteEntitlement {
    fun canUseRemote(): Boolean
}

class DefaultRemoteEntitlement : RemoteEntitlement {
    override fun canUseRemote(): Boolean = true
}
