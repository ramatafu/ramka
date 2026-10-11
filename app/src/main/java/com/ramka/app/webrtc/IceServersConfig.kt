package com.ramka.app.webrtc

import org.webrtc.PeerConnection

object IceServersConfig {
    fun iceServers(): List<PeerConnection.IceServer> {
        return listOf(
            // STUN (для определения публичного IP)
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            
            // TURN через UDP (основной, быстрый)
            PeerConnection.IceServer.builder("turn:217.25.238.148:3478")
                .setUsername("ramka")
                .setPassword("ramka123")
                .createIceServer(),

            // TURN через TCP (резервный, для мобильных операторов, блокирующих UDP)
            PeerConnection.IceServer.builder("turn:217.25.238.148:3478?transport=tcp")
                .setUsername("ramka")
                .setPassword("ramka123")
                .createIceServer()
        )
    }
}