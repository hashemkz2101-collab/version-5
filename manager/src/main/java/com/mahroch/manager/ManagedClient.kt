package com.mahroch.manager

enum class ClientStatus { UNKNOWN, CHECKING, ONLINE, OFFLINE }

data class ManagedClient(
    var name: String,
    var ip: String,
    var token: String,
    @Transient var status: ClientStatus = ClientStatus.UNKNOWN,
    @Transient var statusDetail: String = ""
)
