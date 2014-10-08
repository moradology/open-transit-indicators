package com.azavea.opentransit.indicators

import com.azavea.gtfs._
import com.azavea.gtfs.io.csv._
import com.azavea.opentransit.io.GtfsIngest

import com.azavea.opentransit.testkit._

import com.github.nscala_time.time.Imports._

import org.scalatest._

/**
* Inherit from this trait if you're interested in using ad hoc GTFS data;
* it will be easier to reason about
*/
trait SeptaIndicatorSpec extends FlatSpec with Matchers {
  val realTimeRecords = // Simulated realtime system
    CsvGtfsRecords("/projects/open-transit-indicators/scala/testkit/data/septa_data/")
  val schedTimeRecords =
    CsvGtfsRecords("/projects/open-transit-indicators/scala/testkit/data/septa_realtime_data/")
  val realTimeSystemBuilder = TransitSystemBuilder(realTimeRecords)
  val schedTimeSystemBuilder = TransitSystemBuilder(schedTimeRecords)
  val periods =
    Seq(
      SamplePeriod(1, "night",
        new LocalDateTime("2014-05-01T00:00:00.000"),
        new LocalDateTime("2014-05-01T08:00:00.000")),

      SamplePeriod(1, "morning",
        new LocalDateTime("2014-05-01T08:00:00.000"),
        new LocalDateTime("2014-05-01T11:00:00.000")),

      SamplePeriod(1, "midday",
        new LocalDateTime("2014-05-01T11:00:00.000"),
        new LocalDateTime("2014-05-01T16:30:00.000")),

      SamplePeriod(1, "evening",
        new LocalDateTime("2014-05-01T16:30:00.000"),
        new LocalDateTime("2014-05-01T23:59:59.999")),

      SamplePeriod(1, "weekend",
        new LocalDateTime("2014-05-02T00:00:00.000"),
        new LocalDateTime("2014-05-02T23:59:59.999"))
    )

  val realTimeSystems =
    periods.map { period =>
      (period, realTimeSystemBuilder.systemBetween(period.start, period.end))
    }.toMap

  val schedTimeSystems =
    periods.map { period =>
      (period, schedTimeSystemBuilder.systemBetween(period.start, period.end))
    }.toMap


  def routeById(routeId: String)(implicit routeMap: Map[Route, Double]): Double = {
    val routeIdMap = routeMap.map{case (k, v) => (k.id -> v)}.toMap
    routeIdMap(routeId)
  }

}


