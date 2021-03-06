# endpoints

An embedded DSL for defining HTTP endpoints in Scala.

Key features:

- endpoints are **first-class Scala values**, which can be reused, combined and abstracted over ;
- endpoints can be defined **independently of the HTTP server**, and thus can be used to derive a **statically typed HTTP client** in just 1 line of code ;
- the core algebra is **cross-compiled with Scala.js**, meaning that you can also get a **Scala.js statically typed HTTP client** in just 1 line of code ;
- the core algebra is **highly extensible**: you can define your own **vocabulary** (e.g. to control the cache related HTTP headers in a way that is specific to your app) and your own **semantics** (e.g. swagger-like documentation).

## Documentation

- [slides](http://julienrf.github.io/zrm-endpoints-2016/) of a talk, explaining the motivation and design ;
- Using object algebras to design embedded DSLs ([slides](http://julienrf.github.io/curry-on-2016), [video](https://www.youtube.com/watch?v=snbsYyBS4Bs)).

## Canonical example

### Description of the HTTP endpoints

Let’s define a first artifact, cross-compiled for Scala.js, and containing a description of the
endpoints of a Web service.

~~~ scala
import endpoints.{EndpointAlg, CirceCodecAlg}
import io.circe.generic.JsonCodec
/**
  * Defines the HTTP endpoints description of a Web service implementing a counter.
  * This Web service has two endpoints: one for getting the current value of the counter,
  * and one for incrementing it.
  * It uses circe.io for JSON marshalling.
  */
trait CounterEndpoints extends EndpointAlg with CirceCodecAlg {

  /**
    * Get the counter current value.
    * Uses the HTTP verb “GET” and URL path “/current-value”.
    * The response entity is a JSON document representing the counter value.
    */
  val currentValue = endpoint(get(path / "current-value"), jsonResponse[Counter])

  /**
    * Increments the counter value.
    * Uses the HTTP verb “POST” and URL path “/increment”.
    * The request entity is a JSON document representing the increment to apply to the counter.
    * The response entity is empty.
    */
  val increment = endpoint(post(path / "increment", jsonRequest[Increment]), emptyResponse)

}

@JsonCodec
case class Counter(value: Int)

@JsonCodec
case class Increment(step: Int)
~~~

### JavaScript (Scala.js) client

The following code is located in a Scala.js-only module, which depends on the first one.

~~~ scala
import endpoints.{CirceCodecXhrClient, XhrClientThenable}
/**
  * Defines an HTTP client for the endpoints described in the `CounterAlg` trait.
  * The derived HTTP client uses XMLHttpRequest to perform requests and returns results in a `js.Thenable`.
  */
object Counter extends CounterEndpoints with XhrClientThenable with CirceCodecXhrClient
~~~

And then:

~~~ scala
import scala.scalajs.js
/**
  * Performs an XMLHttpRequest on the `currentValue` endpoint, and then deserializes the JSON
  * response as a `Counter`.
  */
val eventuallyCounter: js.Thenable[Counter] = Counter.currentValue()
~~~

And also:

~~~ scala
/**
  * Serializes the `Increment` value into JSON and performs an XMLHttpRequest on the `increment` endpoint.
  */
val eventuallyDone: js.Thenable[Unit] = Counter.increment(Increment(42))
~~~

### Service implementation (backed by Play framework)

The following code is located in a JVM-only module, which depends on the first one.

~~~ scala
import endpoints.{EndpointPlayRouting, CirceCodecPlayRouting}
import scala.concurrent.stm.Ref

/**
  * Defines a Play router (and reverse router) for the endpoints described in the `CounterAlg` trait.
  */
object Counter extends CounterEndpoints with EndpointPlayRouting with CirceCodecPlayRouting {

  /** Dummy implementation of an in-memory counter */
  val counter = Ref(0)

  val routes = routesFromEndpoints(

    /** Implements the `currentValue` endpoint */
    currentValue.implementedBy(_ => counter.single.get),

    /** Implements the `increment` endpoint */
    increment.implementedBy(inc => counter.single += inc.step))

  )

}
~~~

The `Counter.routes` value is just a `PartialFunction[play.api.mvc.RequestHeader, play.api.mvc.Handler]`.
To get an executable Web server we need to setup a “main” like the following:

~~~ scala
import play.core.server.NettyServer

object Main extends App {
  NettyServer.fromRouter()(Counter.routes)
}
~~~

### What else?

You can also get a Scala/JVM client (which uses `play-ws` under the hood) as follows:

~~~ scala
import endpoints.{EndpointPlayClient, CirceCodecPlayClient}
import play.api.libs.ws.WSClient
import scala.concurrent.ExecutionContext

class Counter(wsClient: WSClient)(implicit ec: ExecutionContext) extends EndpointPlayClient(wsClient)
    with CounterEndpoints with CirceCodecPlayClient
~~~

Thus, you can distribute a (fully working) JVM client, which is independent of your implementation.

## How it works?

The `EndpointAlg` trait used in the first module provides members that bring **vocabulary** to describe HTTP
endpoints (e.g. the `endpoint`, `get`, `post`, etc. methods), but these members are all abstract. Furthermore,
**their types are also abstract**.

For instance, consider the following truncated version of `EndpointAlg`:

~~~ scala
trait EndpointAlg {
  def get[A](url: Url[A]): Request[A]

  /** A request that carries an `A` information */
  type Request[A]
  /** An URL that carries an `A` information */
  type Url[A]
}
~~~

The `get` method builds a `Request[A]` out of an `Url[A]`. Let’s put aside the `Url[A]` type and focus on
`Request[A]`.

The `Request[A]` type defines an HTTP request that *carries* an information `A`. From a client point
of view, this `A` is what is **needed** to build a `Request[A]`. From a server point of view, this `A`
is what is **provided** when processing a `Request[A]`.

Let’s see the semantics that is given to `Request[A]` by the `EndpointXhrClient` trait:

~~~ scala
trait EndpointXhrClient extends EndpointAlg {
  type Request[A] = js.Function1[A, XMLHttpRequest]
}
~~~

As previously said, from a client point of view we want to send requests and get responses. So, `Request[A]`
has the semantics of a recipe to build an `XMLHttpRequest` out of an `A` value.

Here is the semantics given by the `EndpointPlayRouting` trait:

~~~ scala
trait EndpointPlayRouting extends EndpointAlg {
  type Request[A] = RequestHeader => Option[BodyParser[A]]
}
~~~

The aim of the `EndpointPlayRouting` trait is to provide a Play router for a given set of HTTP endpoints. So,
a `Request[A]` is a function that checks if an incoming request matches this endpoint, and in such
a case it returns a `BodyParser[A]` that pulls an `A` out of the request.

As you can see, each implementation brings its own **concrete semantic type** for `Request[A]`.

According to B. Oliveira [1], we say that `EndpointAlg` is an *object algebra interface* and that `EndpointXhrClient`
and `EndpointPlayRouting` are *object algebras*. Note that we use an encoding borrowed from C. Hofer [2], which encodes
*carrier types* with type members rather than type parameters.

- [1] B. C. d. S. Oliveira et. al. Extensibility for the Masses, Practical Extensibility with Object Algebras, ECOOP,
2012 ([pdf](http://www.cs.utexas.edu/~wcook/Drafts/2012/ecoop2012.pdf))
- [2] C. Hofer at. al. Polymorphic Embedding of DSLs, GPCE, 2008 ([pdf](http://www.daimi.au.dk/~ko/papers/gpce50_hofer.pdf))

## Related works

### Autowire

With autowire, all the communications go through a single HTTP endpoint and use a single marshalling format.
This has the following consequences :

- You can not define RESTful APIs ;
- You can not control response caching ;
- You can not serve assets.

Another difference is that `endpoints` uses no macros at all.

### Swagger

Swagger provides a set of tools producing documentation resources from an API specification.
The drawback of Swagger is that you have to maintain both the implementation and the
specification and to keep them consistent (even when using the available “integrations”, which
are only able to automate a small part of the specification generation).

With `endpoints` you can write an object algebra producing documentation resources for
endpoint definitions, so that your documentation is derived from the exact same code
that is used to implement the API, hence being always consistent.

### Thrift?

## Contributing

See the [open issues](https://github.com/julienrf/endpoints/issues).

## License

This content is released under the [MIT License](http://opensource.org/licenses/mit-license.php).
