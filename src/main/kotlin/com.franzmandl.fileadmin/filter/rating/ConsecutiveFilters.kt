package com.franzmandl.fileadmin.filter.rating

import com.franzmandl.fileadmin.common.EnumCollection
import com.franzmandl.fileadmin.filter.TagFilter
import java.util.*

typealias ConsecutiveFilters = EnumCollection<TagFilter.Reason, LinkedList<List<TagFilterRelatives>>>