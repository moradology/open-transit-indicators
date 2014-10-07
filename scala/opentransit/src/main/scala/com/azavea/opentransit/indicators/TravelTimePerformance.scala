package com.azavea.opentransit.indicators

import com.azavea.gtfs._
import com.azavea.opentransit._

/**
* This indicator calculates the average distance between
* arrival times predicted and actually observed
**/
object TravelTimePerformance extends Indicator
                   with AggregatesByAll {
  type Intermediate = Seq[Double]

  val name = "travel_time_performance"

  val calculation =
    new PerTripIndicatorCalculation[Seq[Double]] {
      def map(trip: Trip): Seq[Double] = {
        val observedTrip = observedTrips.filter(_.id == trip.id)

        val schedIdToArrival = for {
          schedStop <- trip.schedule
        } yield (schedStop.id -> schedStop.arrivalTime)
        val obsIdToArrival = for {
          obsStop <- observedTrip.schedule
        } yield (obsStop.id -> obsStop.arrivalTime)

        Seq(schedIdToArrival, obsIdToArrival)
          .combineMaps
          .values
          .map { case (t1, t2) =>
            (Seconds.secondsBetween(t1, t2).getSeconds / 60 / 60).toDouble
          }.toSeq

      }

      def reduce(timeDeltas: Seq[Double]): Double = {
        val (total, count) =
          timeDeltas.flatten.foldLeft((0.0, 0)) { case ((total, count), diff) =>
            (total + diff, count + 1)
          }
        if (count > 0) total / count else 0.0
      }
    }
  }
