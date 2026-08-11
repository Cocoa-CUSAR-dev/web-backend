package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("")
class TestController : BaseController() {
    @GetMapping("/public/test")
    fun publicRoute(): String {
        return "This is Public route"
    }

    @GetMapping("/test")
    fun authRoute(): String {
        return "This is Authenticated route"
    }
}
