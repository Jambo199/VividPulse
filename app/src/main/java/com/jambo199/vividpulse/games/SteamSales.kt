package com.jambo199.vividpulse.games

import java.time.Instant

/** One of Steam's big seasonal sales. */
data class SteamSale(val name: String, val start: Instant, val end: Instant) {
    fun isOn(at: Instant): Boolean = !at.isBefore(start) && at.isBefore(end)
}

/**
 * Steam's big sales, as Valve announced them on Steamworks (they start and end at 10 am Pacific).
 * Updated with the app as Valve announces more.
 */
object SteamSales {
    val KNOWN = listOf(
        SteamSale("Steam Autumn Sale", Instant.parse("2026-10-01T17:00:00Z"), Instant.parse("2026-10-08T17:00:00Z")),
        SteamSale("Steam Winter Sale", Instant.parse("2026-12-17T18:00:00Z"), Instant.parse("2027-01-04T18:00:00Z")),
        SteamSale("Steam Spring Sale", Instant.parse("2027-03-18T17:00:00Z"), Instant.parse("2027-03-25T17:00:00Z")),
        SteamSale("Steam Summer Sale", Instant.parse("2027-06-24T17:00:00Z"), Instant.parse("2027-07-08T17:00:00Z")),
    )

    /** The sale on now, if any. */
    fun current(at: Instant, sales: List<SteamSale> = KNOWN): SteamSale? = sales.firstOrNull { it.isOn(at) }

    /** The next sale to start. */
    fun next(at: Instant, sales: List<SteamSale> = KNOWN): SteamSale? = sales.filter { it.start.isAfter(at) }.minByOrNull { it.start }
}
