package com.franzmandl.fileadmin

import com.franzmandl.fileadmin.generated.GitInfo
import com.franzmandl.fileadmin.security.PasswordEncrypter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import kotlin.system.exitProcess

@SpringBootApplication
class Server {
    companion object {
        private val version = "branch=${GitInfo.branch} shortHash=${GitInfo.shortHash} tags=[${GitInfo.tags.joinToString(",")}]"

        fun initAndRunApplication(args: Array<String>) {
            runApplication<Server>(*args) { setBanner { _, _, out -> out.println("FileAdmin $version") } }
        }
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        Server.initAndRunApplication(args)
    } else {
        val actionArgs = args.copyOfRange(1, args.size)
        when (val action = args[0]) {
            "password" -> PasswordEncrypter.main(actionArgs)
            "service" -> Server.initAndRunApplication(actionArgs)
            else -> {
                System.err.println("[ERROR] not an action '$action'")
                exitProcess(1)
            }
        }
    }
}