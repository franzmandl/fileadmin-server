package com.franzmandl.fileadmin.ticket

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess


object TicketCli {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 4) {
            System.err.println("[ERROR] not 4 arguments")
            exitProcess(1)
        }
        var exitStatus = 0
        val tickets = File(args[0])
        val ticketBinaries = File(args[1])
        val start = LocalDate.parse(args[2], DateTimeFormatter.ISO_LOCAL_DATE)
        val now = LocalDate.parse(args[3], DateTimeFormatter.ISO_LOCAL_DATE)
        tickets.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            projectDir.listFiles()?.filter { it.isDirectory }?.forEach { statusDir ->
                val files = statusDir.listFiles()?.asList() ?: listOf()
                val ticketRegistry = TicketRegistry(files, ticketBinaries, now)
                files.forEach { ticketFile ->
                    try {
                        val ticket = ticketRegistry.getOrCreateTicketDate(ticketFile)
                        if (start <= ticket.date && ticket.date <= now && ticket.getLastModified() != now) {
                            println("ticket/${projectDir.name}/${statusDir.name}/${ticketFile.name}")
                            println()
                            exitStatus = 201
                        }
                    } catch (e: TicketException) {
                        System.err.println(ticketFile.absolutePath)
                        System.err.println(e)
                        System.err.println()
                        exitStatus = 201
                    }
                }
            }
        }
        exitProcess(exitStatus)
    }
}
