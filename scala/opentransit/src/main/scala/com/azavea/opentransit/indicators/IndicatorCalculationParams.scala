package com.azavea.opentransit.indicators

import geotrellis.vector._

import com.azavea.opentransit.database.{ BoundariesTable, RoadsTable }
import scala.slick.jdbc.JdbcBackend.{Database, DatabaseDef, Session}

import com.azavea.opentransit._
import com.azavea.gtfs.{TransitSystem, Stop}

import scala.collection.mutable

import grizzled.slf4j.Logging

/**
 * Trait used to populate parameters with StopBuffer information
 */
trait StopBuffers {
  def bufferForStop(stop: Stop): Polygon
  def bufferForPeriod(period: SamplePeriod): MultiPolygon
  def totalBuffer: MultiPolygon
}

object StopBuffers {
  def apply(systems: Map[SamplePeriod, TransitSystem], bufferDistance: Double): StopBuffers = {
    // Cache buffers so they are only caclulated once
    val stopMap:mutable.Map[Stop, Polygon] = mutable.Map()
    val periodMap:mutable.Map[SamplePeriod, MultiPolygon] = mutable.Map()

    def calcBufferForStop(stop: Stop): Polygon =
      stop.point.geom.buffer(bufferDistance)

    // Calculate combined buffers for entire period
    def calcBufferForPeriod(period: SamplePeriod): MultiPolygon = {
      val system = systems(period)
      val stopBuffers =
        for(
          route <- system.routes;
          trip <- route.trips;
          scheduledStop <- trip.schedule
        ) yield calcBufferForStop(scheduledStop.stop)

      stopBuffers
        .foldLeft(MultiPolygon.EMPTY) { (mp, stopPolygon) =>
          mp.union(stopPolygon) match {
            case MultiPolygonResult(mp) => mp
            case PolygonResult(p) => MultiPolygon(p)
            case _ => mp
          }
        }
      }

    new StopBuffers {
      // Return buffer for a stop
      def bufferForStop(stop: Stop): Polygon = {
        if(!stopMap.contains(stop)) {
          stopMap(stop) = calcBufferForStop(stop)
        }
        stopMap(stop)
      }
      // Return buffers for a period
      def bufferForPeriod(period: SamplePeriod): MultiPolygon = {
        if(!periodMap.contains(period)) {
          periodMap(period) = calcBufferForPeriod(period)
        }
        periodMap(period)
      }

      lazy val totalBuffer: MultiPolygon =
        systems.keys
          .map(bufferForPeriod(_))
          .foldLeft(MultiPolygon.EMPTY) { (mp, systemBuffer) =>
            mp.union(systemBuffer) match {
              case MultiPolygonResult(mp) => mp
              case PolygonResult(p) => MultiPolygon(p)
              case _ => mp
            }
          }

    }
  }
}

// trait Demographics {
//   def populationUnder(polygon: MultiPolygon): Double
// }

// object Demographics {
//   def apply(db: DatabaseDef): MultiPolygon => Double =
//     db withSession { implicit session =>

//     }
// }

/**
 * Trait used to populate indicator parameters with boundaried
 */
trait Boundaries {
  def cityBoundary: MultiPolygon
  def regionBoundary: MultiPolygon
}

object Boundaries {
  def cityBoundary(id: Int)(implicit session: Session): MultiPolygon =
    BoundariesTable.boundary(id)

  def regionBoundary(id: Int)(implicit session: Session): MultiPolygon =
    BoundariesTable.boundary(id)
}

/**
 * Trait used to populate indicator parameters with Road Length
 */
trait RoadLength {
  def totalRoadLength: Double
}

object RoadLength extends Logging {
  def totalRoadLength(implicit session: Session): Double = {
    debug("Fetching Roads")
    val roadLines: List[Line] = RoadsTable.allRoads
    val distinctRoadLines: Array[Line] =
      (MultiLine(roadLines: _*).union match {
        case MultiLineResult(ml) => ml
        case LineResult(l) => MultiLine(l)
        case NoResult => MultiLine.EMPTY
      }).lines
    val len = distinctRoadLines.map(x => x.length).sum / 1000
    debug(s"Length of roadlines: $len")
    len
  }
}

/**
 * Represents parameters that do not change by period
 */
case class IndicatorSettings(
  povertyLine: Double,
  nearbyBufferDistance: Double,
  maxCommuteTime: Int,
  maxWalkTime: Int,
  averageFare: Double
)

trait IndicatorParams extends StopBuffers
                         with Boundaries
                         with RoadLength {
  val settings: IndicatorSettings
}

/**
 * Returns paramaters
 */
object DatabaseIndicatorParamsBuilder {
  def apply(request: IndicatorCalculationRequest, systems: Map[SamplePeriod, TransitSystem], db: DatabaseDef): IndicatorParams =
    db withSession { implicit session =>
      val stopBuffers = StopBuffers(systems, request.nearbyBufferDistance)

      new IndicatorParams {
        def bufferForStop(stop: Stop): Polygon = stopBuffers.bufferForStop(stop)
        def bufferForPeriod(period: SamplePeriod): MultiPolygon = stopBuffers.bufferForPeriod(period)
        def totalBuffer: MultiPolygon = stopBuffers.totalBuffer

        val settings =
          IndicatorSettings(
            request.povertyLine,
            request.nearbyBufferDistance,
            request.maxCommuteTime,
            request.maxWalkTime,
            request.averageFare
          )
        val cityBoundary = Boundaries.cityBoundary(request.cityBoundaryId)
        val regionBoundary = Boundaries.cityBoundary(request.regionBoundaryId)

        val totalRoadLength = RoadLength.totalRoadLength
      }
    }
}
