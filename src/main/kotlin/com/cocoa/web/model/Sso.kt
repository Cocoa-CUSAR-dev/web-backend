package com.cocoa.web.model

import java.util.UUID

// US2-6: lets the LINE diary card's "view full history" button open the web
// app already logged in, without ever handling a password. Same
// service-to-service trust as Diary.Request.Generate (see ServiceKeyFilter).
object Sso {
    object Request {
        data class Mint(
            val userId: UUID,
        )

        data class Exchange(
            val token: String,
        )
    }
}
