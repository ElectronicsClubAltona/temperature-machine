package bad.robot.temperature.server

import java.io._

import bad.robot.logging.info
import bad.robot.temperature.Files
import bad.robot.temperature.rrd.Host
import cats.effect.IO
import fs2.Scheduler
import org.http4s.Method.GET
import org.http4s.client.blaze.BlazeClientConfig._
import org.http4s.client.blaze._
//import org.http4s.server.{Server => Http4sServer}
import org.http4s.{EntityDecoder, Request, Status, Uri}
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll

import scala.concurrent.duration._

class HttpServerTest extends Specification with AfterAll {

  val server = new HttpServer(8080, List(Host("example"))).build(AllTemperatures(), Connections(), new Scheduler() {
    def scheduleOnce(delay: FiniteDuration)(thunk: => Unit): () => Unit = ???

    def scheduleAtFixedRate(period: FiniteDuration)(thunk: => Unit): () => Unit = ???
  }).start.unsafeRunSync()
  val client = Http1Client[IO](config = defaultConfig.copy(idleTimeout = 30 minutes, responseHeaderTimeout = 30 minutes)).unsafeRunSync()

  // todo wait for server to startup, not sure how.

  "index.html can be loaded" >> {
    assertOk(Request(GET, path("")))
  }

  "temperature.json can be loaded" >> {
    maybeCreateJsonFile()
    assertOk(Request(GET, path("/temperature.json")))
  }

  "temperature.csv can be loaded" >> {
    maybeCreateJsonFile()
    assertOk(Request(GET, path("/temperatures.csv")))
  }

  "RRD chart / image can be loaded" >> {
    maybeCreateFile("temperature-1-days.png")
    assertOk(Request(GET, path("/temperature-1-days.png")))
  }

  "Some java script can be loaded (note this changes with every UI deployment)" >> {
    val file = findFileIn("src/main/resources/static/js", startingWith("main", ".js"))
    assertOk(Request(GET, path("/static/js/" + file.head)))
  }

  "Some css can be loaded (note this changes with every UI deployment)" >> {
    val file = findFileIn("src/main/resources/static/css", startingWith("main", ".css"))
    assertOk(Request(GET, path("/static/css/" + file.head)))
  }

  "image can be loaded" >> {
    assertOk(Request(GET, path("/images/spinner.gif")))
  }

  "media can be loaded" >> {
    assertOk(Request(GET, path("/static/media/glyphicons-halflings-regular.f4769f9b.eot")))
  }

  "list of connections can be retrieved" >> {
    assertOk(Request(GET, path("/connections")))
  }

  "list of recent connections can be retrieved" >> {
    assertOk(Request(GET, path("/connections/active/within/5/mins")))
  }

  "get the local machine's log over http" >> {
    maybeCreateFile("temperature-machine.log")
    assertOk(Request(GET, path("/log")))
  }

  "get version info" >> {
    assertOk(Request(GET, path("/version")))
  }

  def assertOk(request: Request[IO]) = {
    val response = client.fetch(request)(IO.pure(_)).unsafeRunSync
    if (response.status != Status.Ok) {
      val body = response.as[String](implicitly, EntityDecoder.text).attempt.unsafeRunSync()
      println(s"Non-200 body was:\n$body")
    }
    response.status must be_==(Status.Ok).eventually(30, 1 minutes)
  }

  def path(url: String): Uri = Uri.fromString(s"http://localhost:8080$url").getOrElse(throw new Exception(s"bad url $url"))

  def maybeCreateJsonFile() = if (!JsonFile.exists) {
    val exampleJson =
      """
        |[
        |  {
        |    "label": "bedroom1-sensor-1",
        |    "data": [
        |      {
        |        "x": 1507709610000,
        |        "y": "NaN"
        |      },
        |      {
        |        "x": 1507709640000,
        |        "y": "+2.2062500000E01"
        |      },
        |      {
        |        "x": 1507709680000,
        |        "y": "+2.2262500000E01"
        |      }
        |    ]
        |  }
        |]
      """.stripMargin

    val writer = new BufferedWriter(new FileWriter(JsonFile.file))
    writer.write(exampleJson)
    writer.close()
  }

  def maybeCreateFile(filename: String) = {
    val file = new File(s"${Files.path}/$filename")
    if (!file.exists()) {
      val writer = new BufferedWriter(new FileWriter(file))
      writer.close()
    }
  }

  def startingWith(startsWith: String, extension: String): FileFilter = (file: File) => file.getName.startsWith(startsWith) && file.getName.endsWith(extension)

  def findFileIn(base: String, filter: FileFilter): List[String] = {
    val files = Option(new File(base).listFiles(filter))
    files.map(_.map(_.getName).toList).getOrElse(List())
  }

  override def afterAll(): Unit = {
    val shutdown = for {
      _ <- server.shutdown
      _ <- info(s"HTTP Server shutting down")
    } yield ()
    shutdown.unsafeRunSync
  }

}
