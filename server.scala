//> using scala "3.2.1"
//> using resourceDir "."
//> using packaging.packageType "assembly"
//> using lib "org.http4s::http4s-ember-server::0.23.17"
//> using lib "org.http4s::http4s-dsl::0.23.17"
//> using lib "com.monovore::decline-effect::2.4.1"
//> using lib "ch.qos.logback:logback-classic:1.4.5"

import cats.effect.{ExitCode, IO}
import cats.effect.kernel.Async
import cats.syntax.all.*
import com.comcast.ip4s.{ipv4, port}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.http4s.{HttpRoutes, MediaType, Response, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.server.middleware.CORS
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.*

object Server extends CommandIOApp("helloServer", "Titles you in HTML") {

  val titleOpt: Opts[String] =
    Opts.env[String]("TITLE", "Page title").withDefault("Hello")

  val baseUrlOpt: Opts[Uri] = Opts
    .env[String]("BASE_URL", "The base url")
    .mapValidated(
      Uri
        .fromString(_)
        .leftMap(_.message)
        .ensure("base url must be absolute")(_.path.addEndsWithSlash.absolute)
        .map(uri => uri.withPath(uri.path.dropEndsWithSlash))
        .toValidatedNel
    )

  def page(uri: Uri, title: String): String =
    s"""|<html>
        |<head><title>$title</title></head>
        |<body>Hello from ${uri.toString}</body>
        |</html>""".stripMargin

  def routes[F[_]: Async: Logger](baseUrl: Uri, title: String): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root / "health" => Response[F](Status.Ok).pure[F]
      case GET -> path =>
        Logger[F].info(s"Serving $path") >>
          Response[F](Status.Ok)
            .withEntity(page(baseUrl.withPath(baseUrl.path.merge(path)), title))
            .withContentType(`Content-Type`(MediaType.text.html))
            .pure[F]
    }

  def main: Opts[IO[ExitCode]] = (baseUrlOpt, titleOpt).mapN((baseUrl, title) =>
    for {
      given Logger[IO] <- Slf4jFactory.create[IO]
      exitCode <- EmberServerBuilder
        .default[IO]
        .withHttp2
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(
          CORS.policy.withAllowOriginAll(routes[IO](baseUrl, title)).orNotFound
        )
        .build
        .useForever
        .as(ExitCode.Success)
    } yield exitCode
  )
}
