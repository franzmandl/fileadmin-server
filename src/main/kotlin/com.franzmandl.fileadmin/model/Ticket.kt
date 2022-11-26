package com.franzmandl.fileadmin.model

import com.franzmandl.fileadmin.ticket.TicketDate
import com.franzmandl.fileadmin.ticket.TicketObjects
import com.franzmandl.fileadmin.ticket.TicketUtil
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class Ticket(
    val actions: Map<String, String>,
    val date: String,
    val isWaiting: Boolean,
    val usesExpression: Boolean,
    val fileEnding: String,
) {
    companion object {
        fun create(
            file: File,
            pathParts: List<String>,
            ticketObjects: TicketObjects
        ): Ticket {
            val ticketDate = ticketObjects.ticketRegistry.getOrCreateTicketDate(file)
            val actions = HashMap<String, String>()
            ticketObjects.ticketStatuses.forEach { setIfNotStatus(actions, it, ticketDate, pathParts) }
            if (TicketUtil.doneStatus !in actions && !isTicketStatus(
                    TicketUtil.doneStatus,
                    pathParts
                ) && ticketDate.hasPeriod && ticketDate.canHandlePeriod
            ) {
                actions[TicketUtil.doneStatus] = ticketDate.getStatusPath(TicketUtil.doneStatus)
            }
            return Ticket(
                actions,
                "${ticketDate.date}",
                ticketDate.isWaiting,
                ticketDate.usesExpression,
                ticketDate.fileEnding
            )
        }

        private fun setIfNotStatus(
            actions: HashMap<String, String>,
            status: String,
            ticketDate: TicketDate,
            pathParts: List<String>
        ) {
            if (!isTicketStatus(status, pathParts) && ticketDate.canHandleStatus(status)) {
                actions[status] = ticketDate.getStatusPath(status)
            }
        }

        private fun isTicketStatus(status: String, pathParts: List<String>): Boolean {
            return pathParts[pathParts.size - 2] == status
        }
    }
}
