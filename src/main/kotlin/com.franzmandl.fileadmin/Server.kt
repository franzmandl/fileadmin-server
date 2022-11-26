package com.franzmandl.fileadmin

import com.franzmandl.fileadmin.security.PasswordEncrypter
import com.franzmandl.fileadmin.ticket.TicketCli
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

@SpringBootApplication
class Server

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        runApplication<Server>(*args)
    } else {
        val actionArgs = args.copyOfRange(1, args.size)
        when (val action = args[0]) {
            "password" -> PasswordEncrypter.main(actionArgs)
            "service" -> runApplication<Server>(*actionArgs)
            "tickets" -> TicketCli.main(actionArgs)
            else -> {
                System.err.println("[ERROR] not an action '$action'")
                exitProcess(1)
            }
        }
    }
}
