package com.azavea.opentransit.indicators

import com.azavea.gtfs._
import com.azavea.opentransit.io.GtfsIngest

import com.azavea.opentransit.testkit._

import com.github.nscala_time.time.Imports._
import com.typesafe.config.{ConfigFactory,Config}

import org.scalatest._

import scala.slick.jdbc.JdbcBackend.Session
import scala.util.{Try, Success, Failure}


trait IndicatorSpec extends DatabaseTestFixture { self: Suite =>
  /** It's horrible to load the data for each test. But I'm done pulling my hair
    * out trying to fix weird NullPointerExceptions and complaints that multiple
    * users are using the database. TODO: Not this.
    */
  val records = {
    val config = ConfigFactory.load
    val dbGeomNameUtm = config.getString("database.geom-name-utm")

    // Import the files into the database to do the reprojection.
    db withSession { implicit session =>
      GtfsIngest(TestFiles.septaPath)
      GtfsRecords.fromDatabase(dbGeomNameUtm).force
    }
  }

  val systemBuilder = TransitSystemBuilder(records)

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

  val period = periods.head
  val system = systemBuilder.systemBetween(period.start, period.end)
  val systems =
    periods.map { period =>
      (period, systemBuilder.systemBetween(period.start, period.end))
    }.toMap

  // test the indicators
  // TODO: refactor indicator tests into separate classes with a trait that does most of the work

  def septaOverall(indicator: Indicator): AggregatedResults =
    PeriodResultAggregator({
      val results = periods.map { period => {
        val calculation = indicator.calculation(period)
        val transitSystem = systems(period)
        val results = calculation(transitSystem)
        (period, results)}
      }
      results.toMap}
    )


  def findRouteById(routes: Iterable[Route], id: String): Option[Route] =
    routes.find(_.id == id)

  def getResultByRouteId(byRoute: Map[Route, Double], id: String) = {
    findRouteById(byRoute.keys, id) match {
      case Some(r) => byRoute(r)
      case None => sys.error(s"Route $id isn't in the result set")
    }
  }
}

class IndicatorCalculatorSpec extends FlatSpec with Matchers with IndicatorSpec {
  it should "calculate time_traveled_stops by mode for SEPTA" in {
    val calculation = TimeTraveledStops.calculation(period)
    val AggregatedResults(byRoute, byRouteType, bySystem) = calculation(system)
    byRouteType(Rail) should be (3.79081 +- 1e-5)
  }

  it should "calculate overall time_traveled_stops by mode for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = septaOverall(TimeTraveledStops)
    byRouteType(Rail) should be (3.48110 +- 1e-5)
  }

  it should "calculate time_traveled_stops by route for SEPTA" in {
    val calculation = TimeTraveledStops.calculation(period)
    val AggregatedResults(byRoute, byRouteType, bySystem) = calculation(system)

    getResultByRouteId(byRoute, "AIR") should be (3.95000 +- 1e-5)
    getResultByRouteId(byRoute, "CHE") should be (2.96923 +- 1e-5)
    getResultByRouteId(byRoute, "CHW") should be (3.17777 +- 1e-5)
    getResultByRouteId(byRoute, "CYN") should be (4.52631 +- 1e-5)
    getResultByRouteId(byRoute, "FOX") should be (4.25531 +- 1e-5)
    getResultByRouteId(byRoute, "LAN") should be (3.84375 +- 1e-5)
    getResultByRouteId(byRoute, "MED") should be (3.18400 +- 1e-5)
    getResultByRouteId(byRoute, "NOR") should be (3.66129 +- 1e-5)
    getResultByRouteId(byRoute, "PAO") should be (3.56470 +- 1e-5)
    getResultByRouteId(byRoute, "TRE") should be (5.10370 +- 1e-5)
    getResultByRouteId(byRoute, "WAR") should be (3.95614 +- 1e-5)
    getResultByRouteId(byRoute, "WIL") should be (3.92948 +- 1e-5)
    getResultByRouteId(byRoute, "WTR") should be (3.67724 +- 1e-5)
  }

  it should "calculate overall time_traveled_stops by route for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = septaOverall(TimeTraveledStops)

    getResultByRouteId(byRoute, "AIR") should be (3.70315 +- 1e-5)
    getResultByRouteId(byRoute, "CHE") should be (2.81527 +- 1e-5)
    getResultByRouteId(byRoute, "CHW") should be (3.07009 +- 1e-5)
    getResultByRouteId(byRoute, "CYN") should be (4.59710 +- 1e-5)
    getResultByRouteId(byRoute, "FOX") should be (3.93347 +- 1e-5)
    findRouteById(byRoute.keys, "GLN") should be (None)
    getResultByRouteId(byRoute, "LAN") should be (3.56027 +- 1e-5)
    getResultByRouteId(byRoute, "MED") should be (2.91718 +- 1e-5)
    getResultByRouteId(byRoute, "NOR") should be (3.56457 +- 1e-5)
    getResultByRouteId(byRoute, "PAO") should be (3.18547 +- 1e-5)
    getResultByRouteId(byRoute, "TRE") should be (4.79009 +- 1e-5)
    getResultByRouteId(byRoute, "WAR") should be (3.59415 +- 1e-5)
    getResultByRouteId(byRoute, "WIL") should be (3.47082 +- 1e-5)
    getResultByRouteId(byRoute, "WTR") should be (3.45751 +- 1e-5)
  }

  it should "calculate time_traveled_stops by system for SEPTA" in {
    val calculation = TimeTraveledStops.calculation(period)
    val AggregatedResults(byRoute, byRouteType, bySystem) = calculation(system)
    bySystem.get should be (3.79081 +- 1e-5)
  }

  it should "calculate overall time_traveled_stops by system for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = septaOverall(TimeTraveledStops)
    bySystem.get should be (3.48110 +- 1e-5)
  }

  // TODO: the results of all the avg_service_freq tests after the refactor look off
  // NOTE: this may be a problem with units -- the numbers are similar, but the decimal place isn't
  /*
  it should "calculate avg_service_freq by mode for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = AverageServiceFrequency(system)
    byRouteType(Rail) should be (0.25888 +- 1e-5)
  }

  it should "calculate overall avg_service_freq by mode for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = septaOverall(AverageServiceFrequency)
    byRouteType(Rail) should be (0.34069 +- 1e-5)
  }

  it should "calculate avg_service_freq by route for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = AverageServiceFrequency(system)

    getResultByRouteId(byRoute, "AIR") should be (0.30820 +- 1e-5)
    getResultByRouteId(byRoute, "CHE") should be (0.44895 +- 1e-5)
    getResultByRouteId(byRoute, "CHW") should be (0.38082 +- 1e-5)
    getResultByRouteId(byRoute, "CYN") should be (0.66370 +- 1e-5)
    getResultByRouteId(byRoute, "FOX") should be (0.46021 +- 1e-5)
    getResultByRouteId(byRoute, "LAN") should be (0.32222 +- 1e-5)
    getResultByRouteId(byRoute, "MED") should be (0.35719 +- 1e-5)
    getResultByRouteId(byRoute, "NOR") should be (0.34982 +- 1e-5)
    getResultByRouteId(byRoute, "PAO") should be (0.25363 +- 1e-5)
    getResultByRouteId(byRoute, "TRE") should be (0.39818 +- 1e-5)
    getResultByRouteId(byRoute, "WAR") should be (0.50407 +- 1e-5)
    getResultByRouteId(byRoute, "WIL") should be (0.40664 +- 1e-5)
    getResultByRouteId(byRoute, "WTR") should be (0.39992 +- 1e-5)
  }

  it should "calculate overall avg_service_freq by route for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = septaOverall(AverageServiceFrequency)

    getResultByRouteId(byRoute, "AIR") should be (0.43618 +- 1e-5)
    getResultByRouteId(byRoute, "CHE") should be (0.52217 +- 1e-5)
    getResultByRouteId(byRoute, "CHW") should be (0.48045 +- 1e-5)
    getResultByRouteId(byRoute, "CYN") should be (0.21974 +- 1e-5)
    getResultByRouteId(byRoute, "FOX") should be (0.52466 +- 1e-5)
    getResultByRouteId(byRoute, "GLN") should be (0.00000 +- 1e-5)
    getResultByRouteId(byRoute, "LAN") should be (0.46935 +- 1e-5)
    getResultByRouteId(byRoute, "MED") should be (0.48672 +- 1e-5)
    getResultByRouteId(byRoute, "NOR") should be (0.48491 +- 1e-5)
    getResultByRouteId(byRoute, "PAO") should be (0.38301 +- 1e-5)
    getResultByRouteId(byRoute, "TRE") should be (0.47254 +- 1e-5)
    getResultByRouteId(byRoute, "WAR") should be (0.61901 +- 1e-5)
    getResultByRouteId(byRoute, "WIL") should be (0.51010 +- 1e-5)
    getResultByRouteId(byRoute, "WTR") should be (0.48118 +- 1e-5)
  }

  it should "calculate avg_service_freq by system for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = AverageServiceFrequency(system)

    bySystem.get should be (0.36349 +- 1e-5)
  }

  it should "calculate overall avg_service_freq by system for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = septaOverall(AverageServiceFrequency)

    bySystem.get should be (0.47632 +- 1e-5)
  }
  */

  // TODO: the following two tests no longer work, because SystemGeometries does not
  // follow the same interface
  /*
  it should "return map of Route ID's and their geometries" in {
    val SystemGeometries(byRoute, byRouteType, bySystem) = SystemGeometries(system)

    val l1 = byRoute(findRouteById(byRoute.keys, "AIR").get)
    l1.lines.size should be (1)
    l1.lines.head.points.length should be (160)


    val l2 = byRoute(findRouteById(byRoute.keys, "LAN").get)
    l2.lines.size should be (1)
    l2.lines.head.points.length should be (415)

    val l3 = byRoute(findRouteById(byRoute.keys, "TRE").get)
    l3.lines.size should be (1)
    l3.lines.head.points.length should be (805)
  }

  it should "return map of Route modes and their geometries" in {
    val SystemGeometries(byRoute, byRouteType, bySystem) = SystemGeometries(system)

    Try(byRouteType(Rail)) match {
      case Success(l1) => l1.lines.size should be (13)
      case Failure(e) => fail(e)
    }
  }
  */

  it should "calcuate overall distance_between_stops by route for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = septaOverall(DistanceStops)

    byRouteType(Rail) should be (2.31436 +- 1e-5)
  }

  it should "calcuate distance_between_stops by route for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = septaOverall(DistanceStops)

    getResultByRouteId(byRoute, "AIR") should be (1.90437 +- 1e-5)
    getResultByRouteId(byRoute, "CHE") should be (1.00636 +- 1e-5)
    getResultByRouteId(byRoute, "CHW") should be (1.32695 +- 1e-5)
    getResultByRouteId(byRoute, "CYN") should be (2.41703 +- 1e-5)
    getResultByRouteId(byRoute, "FOX") should be (1.55288 +- 1e-5)
    findRouteById(byRoute.keys, "GLN") should be (None)
    getResultByRouteId(byRoute, "LAN") should be (2.08694 +- 1e-5)
    getResultByRouteId(byRoute, "MED") should be (1.59278 +- 1e-5)
    getResultByRouteId(byRoute, "NOR") should be (1.72959 +- 1e-5)
    getResultByRouteId(byRoute, "PAO") should be (1.80415 +- 1e-5)
    getResultByRouteId(byRoute, "TRE") should be (5.39617 +- 1e-5)
    getResultByRouteId(byRoute, "WAR") should be (1.75354 +- 1e-5)
    getResultByRouteId(byRoute, "WIL") should be (5.30987 +- 1e-5)
    getResultByRouteId(byRoute, "WTR") should be (2.20600 +- 1e-5)
  }

  it should "calcuate overall distance_between_stops by system for SEPTA" in {
    val AggregatedResults(byRoute, byRouteType, bySystem) = septaOverall(DistanceStops)

    bySystem.get should be (2.31436 +- 1e-5)
  }

  // it should "calcuate overall hours_service by route for SEPTA" in {
  //   val hrsServByMode = septaRailCalc.calculatorsByName("hours_service").calcOverallByMode
  //   hrsServByMode(2) should be (146.48164 plusOrMinus 1e-5)
  // }

  // it should "calcuate overall hours_service by system for SEPTA" in {
  //   val dbsBySystem = septaRailCalc.calculatorsByName("hours_service").calcOverallBySystem
  //   dbsBySystem should be (146.48164 plusOrMinus 1e-5)
  // }

  // it should "calculate hours_service by route for SEPTA" in {
  //   val hrsServByRoute = septaRailCalc.calculatorsByName("hours_service").calcOverallByRoute
  //   hrsServByRoute("PAO") should be (141.84836 plusOrMinus 1e-5)
  //   hrsServByRoute("MED") should be (129.44850 plusOrMinus 1e-5)
  //   hrsServByRoute("WAR") should be (146.48164 plusOrMinus 1e-5)
  //   hrsServByRoute("NOR") should be (137.08175 plusOrMinus 1e-5)
  //   hrsServByRoute("LAN") should be (136.28176 plusOrMinus 1e-5)
  //   hrsServByRoute("CYN") should be (27.79353 plusOrMinus 1e-5)
  //   hrsServByRoute("WIL") should be (135.88176 plusOrMinus 1e-5)
  //   hrsServByRoute("AIR") should be (141.04837 plusOrMinus 1e-5)
  //   hrsServByRoute("CHW") should be (125.31522 plusOrMinus 1e-5)
  //   hrsServByRoute("WTR") should be (136.28176 plusOrMinus 1e-5)
  //   hrsServByRoute("FOX") should be (120.91527 plusOrMinus 1e-5)
  //   hrsServByRoute("CHE") should be (135.28177 plusOrMinus 1e-5)
  //   hrsServByRoute("TRE") should be (144.97888 plusOrMinus 1e-5)
  // }

  // it should "calculate a buffered stops coverage ratio by system for SEPTA" in {
  //   val coverageBufferBySystem = septaRailCalc.calculatorsByName("coverage_ratio_stops_buffer").calcBySystem(period)
  //   coverageBufferBySystem should be (0.09364127449154404 plusOrMinus 1e-5)
  // }

  // // this doesn't test an indicator, but is an example for how to read data from the db
  // it should "read trips from the database" in {
  //   db withSession { implicit session: Session =>
  //     dao.toGtfsData.trips.size should be (1662)
  //   }
  // }

  // it should "calculate the ratio of suburban rail lines" in {
  //   val suburbRateByMode = septaRailCalc.calculatorsByName("ratio_suburban_lines").calcByMode(period)
  //   suburbRateByMode(2) should be (0.846 plusOrMinus 1e-3)
  // }

  // it should "calculate the ratio of suburban lines" in {
  //   val suburbRateBySystem = septaRailCalc.calculatorsByName("ratio_suburban_lines").calcBySystem(period)
  //   suburbRateBySystem should be (0.846 plusOrMinus 1e-3)
  // }

  // it should "read OSM data from the test database" in {
  //   db withSession { implicit session: Session =>
  //     val osmRoads = Q.queryNA[String]("SELECT way FROM planet_osm_roads LIMIT 10").list
  //     val a = osmRoads map (WKB.read[Line](_))
  //     a.head.length should be (181.19214 plusOrMinus 1e-5)
  //   }
  // }

  // it should "calculate the ratio of transit lines to roads - system should be equal to mode" in {
  //   val transToRoadsRateBySystem = septaRailCalc.calculatorsByName("lines_roads").calcBySystem(period)
  //   val transToRoadsRateByMode = septaRailCalc.calculatorsByName("lines_roads").calcByMode(period)
  //   transToRoadsRateBySystem should be (transToRoadsRateByMode(2))
  // }

  // it should "calculate the ratio of transit lines to roads - and get the right answer" in {
  //   db withSession { implicit session: Session =>
  //     val transToRoadsRateBySystem = septaRailCalc.calculatorsByName("lines_roads").calcBySystem(period)
  //     transToRoadsRateBySystem should be (0.54791 plusOrMinus 1e-5)
  //   }
  // }

}
