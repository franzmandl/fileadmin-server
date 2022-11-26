package com.franzmandl.fileadmin.ticket

import java.util.*

data class TicketObjects(val ticketRegistry: TicketRegistry, val ticketStatuses: SortedSet<String>)
