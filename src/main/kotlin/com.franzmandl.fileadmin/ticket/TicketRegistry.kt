package com.franzmandl.fileadmin.ticket

import com.franzmandl.fileadmin.generated.ticket.TicketParser.StartContext
import java.io.File
import java.time.LocalDate

class TicketRegistry(files: List<File>, val ticketBinaries: File, val now: LocalDate) {
    private val fileToTicketDate = HashMap<File, TicketDate>()
    private val idToFile: Map<Int, Payload<File>>
    private val trees: Map<File, Payload<StartContext>> = files.associateWith { file ->
        try {
            Value(TicketUtil.createTree(file.name))
        } catch (e: TicketException) {
            e
        }
    }

    init {
        val map = HashMap<Int, Payload<File>>()
        trees.forEach { (file, payload) ->
            if (payload is Value) {
                val id = TicketId(payload.value).id
                if (id != null) {
                    map[id] = if (id in map) {
                        TicketException("Semantic error: Duplicate id $id")
                    } else {
                        Value(file)
                    }
                }
            }
        }
        idToFile = map
    }

    fun getById(id: Int, caller: TicketDate, callers: MutableSet<TicketDate>): LocalDate {
        if (caller in callers) {
            throw TicketException("Semantic error: Cycle detected")
        }
        callers.add(caller)
        val file = idToFile[id] ?: throw TicketException("Semantic error: id $id not found")
        return when (file) {
            is TicketException -> throw file
            is Value -> getOrCreateTicketDate(file.value, callers).date
        }
    }

    fun getOrCreateTicketDate(file: File, callers: MutableSet<TicketDate> = HashSet()): TicketDate {
        val foundTicketDate = fileToTicketDate[file]
        if (foundTicketDate != null) {
            return foundTicketDate
        }
        return when (val payload = trees[file]) {
            null -> throw TicketException("Server error: Tree not found for ${file.absolutePath}")
            is TicketException -> throw payload
            is Value -> {
                val ticketDate = TicketDate(file, payload.value, this, callers)
                fileToTicketDate[file] = ticketDate
                ticketDate
            }
        }
    }
}
