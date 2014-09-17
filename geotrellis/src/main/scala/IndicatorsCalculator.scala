package opentransitgt

import com.azavea.gtfs._
import com.azavea.gtfs.data._
import opentransitgt.DjangoAdapter._
import opentransitgt.indicators._

import scala.slick.jdbc.JdbcBackend.{DatabaseDef}

// Handles creating all indicator calculators and storing results
class IndicatorsCalculator(val data: GtfsData, val params: CalcParams, db: DatabaseDef) {
  // Create all indicator calculators.
  // Whenever a new indicator is created, it must be added to this list.
  //
  // TODO: it seems like this list should be able to be constructed automatically
  //   by making use of the reflection API, but the solution was not obvious after
  //   a fair amount of research.
  val calculators: List[IndicatorCalculator] = List(
    new NumStops(data, params, db),
    new NumRoutes(data, params, db),
    new Length(data, params, db),
    new TimeTraveledStops(data, params, db),
    new AvgServiceFreq(data, params, db),
    new DistanceStops(data, params, db),
    new CoverageRatio500M(data, params, db)
  )

  // Map of IndicatorCalculators by name
  lazy val calculatorsByName: Map[String, IndicatorCalculator] = {
    calculators.map(calc => (calc.name, calc)).toMap
  }

  // Ask each indicator calculator to store its results.
  // This will eventually be queued and processed in the background.
  def storeIndicators = {
    for (calc <- calculators) calc.storeIndicators
  }
}
