package com.azavea.opentransit.indicators

import org.joda.time.Seconds

import com.azavea.gtfs._
import com.azavea.opentransit._

/**
* This indicator calculates the average distance between
* arrival times predicted and actually observed
**/
class TravelTimePerformance(params: ObservedStopTimes) extends Indicator
                   with AggregatesByAll {
  type Intermediate = Seq[Double]

  val name = "travel_time_performance"

  def calculation(period: SamplePeriod) = {
    def map(trip: Trip): Seq[Double] = {
      val observedTrip = params.observedForTrip(period, trip.id)
      println(observedTrip)

      val schedIdToArrival = {
        for {
          schedStop <- trip.schedule
        } yield (schedStop.stop.id -> schedStop.arrivalTime)
      }.toMap
      val obsIdToArrival = {
        for {
          obsStop <- observedTrip.schedule
        } yield (obsStop.stop.id -> obsStop.arrivalTime)
      }.toMap

      println("=============================")
      println(schedIdToArrival)
      println(obsIdToArrival)
      println("=============================")
      schedIdToArrival.keys.map { key =>
        Seconds.secondsBetween(schedIdToArrival(key),
                               obsIdToArrival(key))
          .getSeconds
          .toDouble / 60
      }.toSeq
      /*
      Seq(schedIdToArrival, obsIdToArrival)
        .values.
        .map { case (t1, t2) =>
          (Seconds.secondsBetween(t1, t2).getSeconds / 60 / 60).toDouble
        }.toSeq*/

    }

    def reduce(timeDeltas: Seq[Seq[Double]]): Double = {
      val (total, count) =
        timeDeltas.flatten.foldLeft((0.0, 0)) { case ((total, count), diff) =>
          (total + diff, count + 1)
        }
      if (count > 0) total / count else 0.0
    }
    perTripCalculation(map, reduce)
  }
}
