package com.sumpilot.ui.format

import com.sumpilot.domain.model.Operation
import java.text.DateFormat
import java.util.Date

/** Local date and time in the device's locale and time zone. */
fun formatDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

fun operationsLabel(ops: Set<Operation>): String =
    if (ops.size == Operation.entries.size) "Mixed (+ − × ÷)"
    else Operation.entries.filter { it in ops }.joinToString(", ") { it.label }
