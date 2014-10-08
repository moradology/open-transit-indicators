package com.azavea.opentransit.indicators

import com.azavea.opentransit._
import com.azavea.gtfs._
import com.azavea.gtfs.io.csv._
import com.azavea.opentransit.io.GtfsIngest

import com.azavea.opentransit.testkit._

import com.github.nscala_time.time.Imports._
import com.typesafe.config.{ConfigFactory,Config}

import org.scalatest._

import scala.slick.jdbc.JdbcBackend.Session
import scala.util.{Try, Success, Failure}

trait TestParams extends DatabaseTestFixture  { self: Suite =>
  println("in testparamsobject")
  val request = IndicatorCalculationRequest(
      "fakeToken",
      "someVersion",
      povertyLine = 30000,
      nearbyBufferDistance = 500,
      maxCommuteTime = 30,
      maxWalkTime = 10,
      cityBoundaryId = 1,
      regionBoundaryId = 1,
      averageFare = 2.5,
      samplePeriods = List (
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
  )
  println("in testparamsobject")

  val records =
    CsvGtfsRecords("/projects/open-transit-indicators/scala/testkit/data/septa_data")
  println("in testparamsobject")
  val systemBuilder = TransitSystemBuilder(records)
  println("in testparamsobject")
  val systemsByPeriod = request.samplePeriods.map { period =>
    (period -> systemBuilder.systemBetween(period.start, period.end))
  }.toMap
  println("in testparamsobject")
  val period = request.samplePeriods.head
  val system = systemsByPeriod(period)

  val dbip = DatabaseIndicatorParamsBuilder(request, systemsByPeriod, db)
  println("out testparamsobject")

}


class TravelTimePerformanceSpec extends FlatSpec
    with Matchers
    with TestParams {
  println("in ttperspec")
  val params = dbip
  it should "calculate travel time performance by mode for SEPTA" in {
    val calculation = new TravelTimePerformance(params: ObservedStopTimes).calculation(period)
    val AggregatedResults(byRoute, byRouteType, bySystem) = calculation(system)
    byRouteType(Rail) should be (13)
  }

  it should "calculate num_routes by route for SEPTA" in {
    val calculation = new TravelTimePerformance(params: ObservedStopTimes).calculation(period)
    val AggregatedResults(byRoute, byRouteType, bySystem) = calculation(system)

    println("test!")
    println(byRoute)
    println(byRouteType)
    println(bySystem)
    /*
    getResultByRouteId(byRoute, "AIR") should be (15.0 plusOrMinus 5)
    getResultByRouteId(byRoute, "CHE") should be (15.0 plusOrMinus 5)
    getResultByRouteId(byRoute, "CHW") should be (15.0 plusOrMinus 5)
    getResultByRouteId(byRoute, "CYN") should be (15.0 plusOrMinus 5)
    getResultByRouteId(byRoute, "FOX") should be (15.0 plusOrMinus 5)
    getResultByRouteId(byRoute, "LAN") should be (15.0 plusOrMinus 5)
    */
  }
  println("out ttperspec")
}

