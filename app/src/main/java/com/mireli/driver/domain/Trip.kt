package com.mireli.driver.domain

enum class ServiceType { SHARED, CHARTER }
enum class TripStage { ASSIGNED, ACCEPTED, AT_PICKUP, IN_PROGRESS, COMPLETED }
enum class Boarding { WAITING, BOARDED, NO_SHOW }
data class Passenger(val id: String, val name: String, val seats: Int, val code: String, val boarding: Boarding = Boarding.WAITING)
data class Trip(
    val id: String, val origin: String, val destination: String, val reportingTime: String,
    val service: ServiceType, val vehicle: String, val capacity: Int,
    val fareMinor: Long, val passengers: List<Passenger>, val stage: TripStage = TripStage.ASSIGNED,
    val version: Int = 0,
) {
    init {
        require(capacity > 0 && fareMinor >= 0 && version >= 0)
        require(passengers.all { it.seats > 0 })
        require(passengers.map { it.id }.distinct().size == passengers.size)
        require(passengers.sumOf { it.seats.toLong() } <= capacity.toLong())
    }
    val bookedSeats get() = passengers.sumOf { it.seats }
    val boardedSeats get() = passengers.filter { it.boarding == Boarding.BOARDED }.sumOf { it.seats }
}
sealed interface TripCommand {
    data object Accept : TripCommand
    data object Arrive : TripCommand
    data class Board(val passengerId: String, val code: String) : TripCommand
    data class MarkNoShow(val passengerId: String) : TripCommand
    data object Start : TripCommand
    data object Complete : TripCommand
}
sealed interface Change {
    data class Applied(val trip: Trip) : Change
    data class Rejected(val reason: String) : Change
}

/** Client-side guidance. The live backend must independently authorize and validate every command. */
object TripRules {
    fun apply(trip: Trip, expectedVersion: Int, command: TripCommand): Change {
        if (trip.version != expectedVersion) return Change.Rejected("This trip changed. Refresh before continuing.")
        fun changed(next: Trip) = Change.Applied(next.copy(version = trip.version + 1))
        fun wrongStage() = Change.Rejected("This action is not available at this stage.")
        return when (command) {
            TripCommand.Accept -> if (trip.stage == TripStage.ASSIGNED) changed(trip.copy(stage = TripStage.ACCEPTED)) else wrongStage()
            TripCommand.Arrive -> if (trip.stage == TripStage.ACCEPTED) changed(trip.copy(stage = TripStage.AT_PICKUP)) else wrongStage()
            is TripCommand.Board -> {
                if (trip.stage != TripStage.AT_PICKUP) return wrongStage()
                val passenger = trip.passengers.find { it.id == command.passengerId }
                    ?: return Change.Rejected("Passenger is not on this manifest.")
                if (passenger.boarding != Boarding.WAITING) return Change.Rejected("This passenger already has a boarding outcome.")
                if (passenger.code != command.code.trim()) return Change.Rejected("The boarding code does not match.")
                changed(trip.copy(passengers = trip.passengers.map {
                    if (it.id == passenger.id) it.copy(boarding = Boarding.BOARDED) else it
                }))
            }
            is TripCommand.MarkNoShow -> {
                if (trip.stage != TripStage.AT_PICKUP) return wrongStage()
                val passenger = trip.passengers.find { it.id == command.passengerId }
                    ?: return Change.Rejected("Passenger is not on this manifest.")
                if (passenger.boarding != Boarding.WAITING) return Change.Rejected("This passenger already has a boarding outcome.")
                changed(trip.copy(passengers = trip.passengers.map {
                    if (it.id == passenger.id) it.copy(boarding = Boarding.NO_SHOW) else it
                }))
            }
            TripCommand.Start -> when {
                trip.stage != TripStage.AT_PICKUP -> wrongStage()
                trip.passengers.any { it.boarding == Boarding.WAITING } -> Change.Rejected("Resolve every passenger before starting.")
                trip.boardedSeats == 0 -> Change.Rejected("No passengers boarded. Contact dispatch.")
                else -> changed(trip.copy(stage = TripStage.IN_PROGRESS))
            }
            TripCommand.Complete -> if (trip.stage == TripStage.IN_PROGRESS) changed(trip.copy(stage = TripStage.COMPLETED)) else wrongStage()
        }
    }
}
