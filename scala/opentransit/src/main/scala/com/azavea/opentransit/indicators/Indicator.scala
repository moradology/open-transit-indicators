package com.azavea.opentransit.indicators

import com.azavea.gtfs._
import scala.collection.mutable

object Indicators {
  // These are indicators that don't need the request info.
  private val staticIndicators: List[Indicator] =
    List(
      AverageServiceFrequency,
      DistanceStops,
      Length,
      NumRoutes,
      NumStops,
      TimeTraveledStops,
      InterstopDistance,
      StopsToLength
    )

  // These are indicators that need to know things about the request
  private def paramIndicators(params: IndicatorParams): List[Indicator] =
    List(
      new CoverageRatioStopsBuffer(params),
      new TransitNetworkDensity(params),
      new TravelTimePerformance(params)
      new Affordability(params)
    )

  def list(params: IndicatorParams): List[Indicator] =
    staticIndicators ++ paramIndicators(params)
}

trait Indicator { self: AggregatesBy =>
  val name: String
  def calculation(period: SamplePeriod): IndicatorCalculation

  def aggregatesBy(aggregate: Aggregate) =
    self.aggregates.contains(aggregate)

  def perTripCalculation[T](mapFunc: Trip => T, reduceFunc: Seq[T] => Double): IndicatorCalculation =
    new PerTripIndicatorCalculation[T] {
      def apply(system: TransitSystem): AggregatedResults =
        apply(system, Indicator.this.aggregatesBy _)

      def map(trip: Trip): T = mapFunc(trip)
      def reduce(results: Seq[T]): Double = reduceFunc(results)
    }

  def perRouteCalculation[T](mapFunc: Seq[Trip] => T, reduceFunc: Seq[T] => Double): IndicatorCalculation =
    new PerRouteIndicatorCalculation[T] {
      def apply(system: TransitSystem): AggregatedResults =
        apply(system, Indicator.this.aggregatesBy _)

      def map(trips: Seq[Trip]): T = mapFunc(trips)
      def reduce(results: Seq[T]): Double = reduceFunc(results)
    }

  def perSystemCalculation(calculate: TransitSystem => AggregatedResults) =
    new IndicatorCalculation {
      def apply(system: TransitSystem) = calculate(system)
    }
}
