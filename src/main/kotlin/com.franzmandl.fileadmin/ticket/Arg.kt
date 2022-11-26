package com.franzmandl.fileadmin.ticket

sealed class Arg
class StringArg(val value: String) : Arg()
object KeywordArgs : Arg()
object KeywordArg : Arg()
