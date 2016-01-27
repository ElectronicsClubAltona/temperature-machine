package bad.robot.temperature

import bad.robot.temperature.ds18b20.SensorFile
import bad.robot.temperature.ds18b20.SensorFile._
import bad.robot.temperature.server.{Server, HttpServer}
import bad.robot.temperature.task.TemperatureMachineThreadFactory._
import bad.robot.temperature.task._

import scala.language.postfixOps
import scalaz.concurrent.Task

object Main extends App {

  val location = sys.props.getOrElse("sensor.location", BaseFolder)

  SensorFile.find(location) match {
    case Nil     => println(FailedToFindFile(location).message)
    case sensors => Tasks.application(sensors).run
  }

}
