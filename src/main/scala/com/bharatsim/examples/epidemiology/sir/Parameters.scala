package com.bharatsim.examples.epidemiology.sir

import com.bharatsim.engine.ScheduleUnit

object Parameters {

  // Tick is one iteration of the simulation
  final val numberOfTicksInADay: Int = 2
  // The infection rate beta will be adjusted according to dt
  final val dt: Double = 1.0/numberOfTicksInADay

  // Define length of ticks in terms of schedule units.
  final val myTick: ScheduleUnit = new ScheduleUnit(1)
  final val myDay: ScheduleUnit = new ScheduleUnit(myTick * numberOfTicksInADay)

  // Fraction of people infected before the simulation starts.
  final val initialInfectedFraction = 0.1

  // Disease parameters.
  final val beta: Double = 0.1      // How fast the disease spreads
  final val gamma: Double = 1.0/7   // How fast person recovers
}
