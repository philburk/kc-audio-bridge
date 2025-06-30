package com.mobileer.audiobridge

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform