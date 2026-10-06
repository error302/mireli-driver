package com.mireli.driver.domain

enum class ServiceType { SHARED, CHARTER }
enum class TripStage { ASSIGNED, ACCEPTED, AT_PICKUP, IN_PROGRESS, COMPLETED }
enum class Boarding { WAITING, PARTIAL, BOARDED, NO_SHOW }
enum class AssignmentState { PENDING, ACCEPTED, DECLINED, WITHDRAWN, EXPIRED }
data class Passenger(val id: String, val name: String, val seats: Int, val code: String, val boarding: Boarding = Boarding.WAITING,
    val boardedCount: Int = if (boarding == Boarding.BOARDED) seats else 0,
    val noShowCount: Int = if (boarding == Boarding.NO_SHOW) seats else 0) {
    init { require(seats > 0 && boardedCount >= 0 && noShowCount >= 0 && boardedCount.toLong() + noShowCount <= seats.toLong()) }
    val unresolvedSeats get() = seats - boardedCount - noShowCount
}
data class Trip(
    val id: String, val origin: String, val destination: String, val reportingTime: String,
    val service: ServiceType, val vehicle: String, val capacity: Int,
    val fareMinor: Long, val passengers: List<Passenger>, val stage: TripStage = TripStage.ASSIGNED,
    val version: Int = 0,
    val assignment: AssignmentState = if (stage == TripStage.ASSIGNED) AssignmentState.PENDING else AssignmentState.ACCEPTED,
    val declineReason: String? = null,
) {
    init {
        require(capacity > 0 && fareMinor >= 0 && version >= 0)
        require(passengers.all { it.seats > 0 })
        require(passengers.map { it.id }.distinct().size == passengers.size)
        require(passengers.sumOf { it.seats.toLong() } <= capacity.toLong())
    }
    val bookedSeats get() = passengers.sumOf { it.seats }
    val boardedSeats get() = passengers.sumOf { it.boardedCount }
    val actionable get() = assignment in listOf(AssignmentState.PENDING, AssignmentState.ACCEPTED)
}
sealed interface TripCommand {
    data object Accept : TripCommand
    data object Arrive : TripCommand
    data class Board(val passengerId: String, val code: String, val count: Int? = null) : TripCommand
    data class Decline(val reason: String) : TripCommand
    data class MarkNoShow(val passengerId: String) : TripCommand
    data object Start : TripCommand
    data object Complete : TripCommand
}
sealed interface Change {
    data class Applied(val trip: Trip) : Change
    data class Rejected(val reason: String) : Change
    data class Queued(val commandId: String) : Change
}

/** Client-side guidance. The live backend must independently authorize and validate every command. */
object TripRules {
    fun apply(trip: Trip, expectedVersion: Int, command: TripCommand): Change {
        if (trip.version != expectedVersion) return Change.Rejected("This trip changed. Refresh before continuing.")
        if (!trip.actionable) return Change.Rejected("This assignment is ${trip.assignment.name.lowercase()}. Contact dispatch for another journey.")
        fun changed(next: Trip) = Change.Applied(next.copy(version = trip.version + 1))
        fun wrongStage() = Change.Rejected("This action is not available at this stage.")
        return when (command) {
            TripCommand.Accept -> if (trip.stage == TripStage.ASSIGNED && trip.assignment == AssignmentState.PENDING) changed(trip.copy(stage = TripStage.ACCEPTED, assignment = AssignmentState.ACCEPTED)) else wrongStage()
            is TripCommand.Decline -> when {
                trip.stage != TripStage.ASSIGNED || trip.assignment != AssignmentState.PENDING -> wrongStage()
                command.reason.trim().length !in 5..200 -> Change.Rejected("Give a decline reason between 5 and 200 characters.")
                else -> changed(trip.copy(assignment = AssignmentState.DECLINED, declineReason = command.reason.trim()))
            }
            TripCommand.Arrive -> if (trip.stage == TripStage.ACCEPTED) changed(trip.copy(stage = TripStage.AT_PICKUP)) else wrongStage()
            is TripCommand.Board -> {
                if (trip.stage != TripStage.AT_PICKUP) return wrongStage()
                val passenger = trip.passengers.find { it.id == command.passengerId }
                    ?: return Change.Rejected("Passenger is not on this manifest.")
                if (passenger.unresolvedSeats == 0) return Change.Rejected("This party already has a boarding outcome.")
                if (passenger.code != command.code.trim()) return Change.Rejected("The boarding code does not match.")
                val count = command.count ?: passenger.unresolvedSeats
                if (count !in 1..passenger.unresolvedSeats) return Change.Rejected("Choose a count within the remaining passenger seats.")
                val total = passenger.boardedCount + count
                changed(trip.copy(passengers = trip.passengers.map {
                    if (it.id == passenger.id) it.copy(boarding = if (total == it.seats) Boarding.BOARDED else Boarding.PARTIAL, boardedCount = total) else it
                }))
            }
            is TripCommand.MarkNoShow -> {
                if (trip.stage != TripStage.AT_PICKUP) return wrongStage()
                val passenger = trip.passengers.find { it.id == command.passengerId }
                    ?: return Change.Rejected("Passenger is not on this manifest.")
                if (passenger.unresolvedSeats == 0) return Change.Rejected("This party already has a boarding outcome.")
                changed(trip.copy(passengers = trip.passengers.map {
                    if (it.id == passenger.id) it.copy(boarding = if (it.boardedCount == 0) Boarding.NO_SHOW else Boarding.PARTIAL,
                        noShowCount = it.noShowCount + it.unresolvedSeats) else it
                }))
            }
            TripCommand.Start -> when {
                trip.stage != TripStage.AT_PICKUP -> wrongStage()
                trip.passengers.any { it.unresolvedSeats > 0 } -> Change.Rejected("Resolve every passenger before starting.")
                trip.boardedSeats == 0 -> Change.Rejected("No passengers boarded. Contact dispatch.")
                else -> changed(trip.copy(stage = TripStage.IN_PROGRESS))
            }
            TripCommand.Complete -> if (trip.stage == TripStage.IN_PROGRESS) changed(trip.copy(stage = TripStage.COMPLETED)) else wrongStage()
        }
    }
}
