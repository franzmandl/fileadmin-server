package com.franzmandl.fileadmin.config

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.*

object PasswordEncrypter {
    val passwordEncoder: PasswordEncoder = BCryptPasswordEncoder(10)

    @JvmStatic
    fun main(args: Array<String>) {
        val scanner = Scanner(System.`in`)
        print("Password: ")
        val password = scanner.nextLine()
        println(passwordEncoder.encode(password))
    }
}