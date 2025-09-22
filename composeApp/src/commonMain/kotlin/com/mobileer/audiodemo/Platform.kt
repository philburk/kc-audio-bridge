package com.mobileer.audiodemo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform