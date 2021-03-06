package chapters

import cats.data.{IndexedStateT, State, Writer, WriterT}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.Try

object Chapter4Monads {

  /**
    * Whereas a Functor allows us to sequence computations, a Monad (which is also Functor)
    * allows us to sequence computations whilst taking into account an intermediate complication.
    * E.g the flatmap method of Option takes intermediate Options into account
    *
    * The function passed to a flatmap specifies the application-specific part of the computation
    * and flatMap takes care of the complication
    */

  import scala.util.Try

  def foo(s: String): Option[Int] = Try(s.toInt).toOption

  foo("1")
    .flatMap(a => foo("2")
      .flatMap(b => foo("3")
        .map(c => a + b + c)))
  for {
    a <- foo("1")
    b <- foo("2")
    c <- foo("3")
  } yield a + b + c //Some(6)

  for {
    a <- foo("1")
    b <- foo("?")
    c <- foo("3")

  } yield a + b + c //None


  /**
    * Simple definition of a Monad
    *
    * Laws
    * - Left identity - calling pure and transforming the result with func is the same as calling func
    * pure(a).flatMap(func) == func(a)
    *
    * - Right identity - passing pure to flatMap is the same as doing nothing
    *    m.flatMap(pure) == m
    *
    *  - Associativity - flatMapping over two functions f and g is the same as flatMapping over f and
    * then flatMapping over g
    *   m.flatMap(f).flatMap(g) == m.flatMap(x => f(x).flatMap(g))
    *
    */
  trait Monad[F[_]] {
    def pure[A](value: A): F[A]

    def flatMap[A, B](value: F[A])(func: A => F[B]): F[B]

    // Exercise.  Define map
    def map[A, B](value: F[A])(func: A => B): F[B] =
      flatMap(value)(a => pure(func(a)))
  }

  //  trait MonadWithLaws[F[_]] extends Monad[F[_]] {
  //
  //    def flatMap[A, B](value: F[A])(func: A => F[B]): Boolean
  //
  //  }

  /**
    * Monads in Cats
    */

  import cats.Monad
  import cats.instances.list._
  import cats.instances.option._

  val opt1: Option[Int] = Monad[Option].pure(3) // Some(3)

  val opt2: Option[Int] = Some(1).flatMap(i => Some(i * 2)) // Some(6)

  val opt3 = Monad[Option].map(opt2)(_ * 3) //Some(18)

  val list1 = Monad[List].pure(3) //List(3)

  val list2 = Monad[List].flatMap(List(1, 2, 3))(i => List(i, i * 9)) //List(1, 9, 2, 18, 3, 27)

  val list3 = Monad[List].flatMap(list2)(x => List(x * 10)) //List(10, 90, 20, 180, 30, 270)

}

object Chapter4Monad_2 {

  /**
    * Syntax
    *
    * Example abstracting over different monads
    */

  import cats.Monad
  import cats.syntax.flatMap._
  import cats.syntax.functor._ // for flatMap
  //  import scala.language.higherKinds


  def sumSquare[F[_] : Monad](a: F[Int], b: F[Int]): F[Int] = {
    for {
      aVal <- a
      bVal <- b
    } yield aVal * aVal + bVal * bVal

  }

  import cats.instances.list._
  import cats.instances.option._ // for Monad”

  sumSquare(List(1, 2, 3), List(4, 5)) // res0: List[Int] = List(17, 26, 20, 29, 25, 34)
  sumSquare(Option(2), Option(9)) // res1: Option[Int] = Some(85)


  /**
    * Identity Monad
    *
    * Allows similar to above but can use Monadic and non-Monadic parameters
    * This is extremely powerful as it means that we can run code asynchronously in production
    * and synchronously in tests
    */

  import cats.Id

  sumSquare(3: Id[Int], 4: Id[Int]) // 25

  /*
   * type Id[A] = A
   *
   * Id is a type alias that turns at atomic type into a single-parameter type constructor
   *
   */

  val a = Monad[Id].pure(3)

  /**
    * Implement pure map and flatMap for Id
    */

  type MyId[A] = A

  def pure[A](value: A): MyId[A] = value

  def map[A, B](value: MyId[A])(f: A => B): MyId[B] = f(value)

  def flatMap[A, B](value: MyId[A])(f: A => MyId[B]): MyId[B] = f(value)

  /*
   * The purpose of a Monad is to allow us to sequence operations ignoring a complication
   * However, in this of Id / MyId there is no intermediate complication, these are atomic types.
   * MyId[A] is simply an alias of A!
   * Pure returns the argument.
   * Map and flatMap are identical since A => B === A => Id[B]
   */

  /**
    * Either
    */

  import cats.syntax.either._

  4.asRight[String]

  case class Error(e: String)

  /*
    * These “smart constructors” have advantages over Left.apply and Right.apply
    * because they return results of type Either instead of Left and Right.
    * This helps avoid type inference bugs caused by over-narrowing
    */
  def sumPositive(l: List[Int]): Either[Error, Int] = {
    l.foldLeft(0.asRight[Error]) { (acc: Either[Error, Int], i: Int) =>
      if (i > 0) {
        acc.map(_ + i)
      } else {
        Left(Error(s"negative value in list $i"))
      }
    }
  }


  sumPositive(List(1, 2, 3, -1, 4)) // Left(Error(negative value in list -1))
  sumPositive(List(1, 2, 3, 4)) // Right(10)

  /*
    * Other useful Either extension methods
    */
  Either.catchOnly[NumberFormatException]("foo".toInt) // res3: Either[NumberFormatException,Int] = Left(java.lang.NumberFormatException: For input string: "foo")

  Either.catchNonFatal(sys.error("uh oh")) // res4: Either[Throwable,Nothing] = Left(java.lang.RuntimeException: uh oh)
  Either.fromTry(scala.util.Try("foo".toInt)) // res5: Either[Throwable,Int] = Left(java.lang.NumberFormatException: For input string: "foo")


  /*
   * Transforming Eithers
   */


  // p 373 Either from a try with a left map to handle error case

  def squareString(s: => String): Either[String, Int] = {
    Either.fromTry(
      Try {
        val i = s.toInt
        i * i
      }
    ).leftMap {
      case _: NumberFormatException => "Boom - number format exception!"
      case _: NullPointerException => "Bang - null pointer exception!"
    }
  }


  squareString("1") // res0: Either[String,Int] = Right(1)
  squareString("s") // res1: Either[String,Int] = Left(Boom - number format exception!)
  squareString(throw new NullPointerException) //res2: Either[String,Int] = Left(Bang - null pointer exception!)

  // Ensuring -  must satisfy a predicate

  def squareString(s: String, p: Int => Boolean): Either[String, Int] = {
    Either.fromTry( // Either from Try
      Try {
        val i = s.toInt
        i * i
      }
    ).leftMap { case _: NumberFormatException => "Boom - number format exception!" } // Left map
  }.ensure("result must not equal 9")(p) // Predicate to check with result satisfies a predicate

  val p: Int => Boolean = _ != 9

  squareString("2", p) // Right(4)
  squareString("2!", p) // Left(Boom - number format exception!!)
  squareString("3", p) // Left(must not equal 9)

  // Bimap
  def cubeString(s: String): Either[Error, String] = {
    Either.fromTry(
      Try {
        val i = s.toInt
        i * i * i
      }
    ).bimap(
      e => Error(e.getMessage),
      s => s"$s!!!"
    )
  }

  cubeString("4") // Right(64!!!)
  cubeString("foo") // Left(Error(For input string: "foo"))


  import cats.Eval

  /**
    * Eval Monad
    * Think of val, lazy val and def
    * val / now        - both eagerly evaluated and the result is memoized i.e it can be recalled without recomputing
    * lazy val / later - both lazily evaluated when called and the result is memoized
    * def / always     - both lazily evaluated and the result is not memoized
    **/

  val now = Eval.now(math.random() + 1000) // now: cats.Eval[Double] = Now(1000.3050385215279)

  val later = Eval.later(math.random() + 1000) // later: cats.Eval[Double] = cats.Later@28375e21

  val always = Eval.always(math.random + 3000) // always: cats.Eval[Double] = cats.Always@308287f9


  now.value
  now.value

  later.value
  later.value

  always.value
  always.value

  /*
   * Eval has a memoize method that allows us to memoize a chain of computations. The result of the chain up to the
   * call to memoize is cached, whereas calculations after the call retain their original semantics:
   */

  val saying = Eval.
    always {
      println("Step 1");
      "The cat"
    }
    .map { str => println("Step 2"); s"$str sat on" }
    .memoize
    .map { str => println("Step 3"); s"$str the mat" }
  // saying: cats.Eval[String] = cats.Eval$$anon$8@7a0389b5
  saying.value // first access
  // Step 1
  // Step 2
  // Step 3
  // res18: String = The cat sat on the mat
  saying.value // second access
  // Step 3
  // res19: String = The cat sat on the mat

  /**
    * Trampolining and Eval.defer
    * One useful property of Eval is that its map and flatMap methods are trampolined.
    * This means we can nest calls to map and flatMap arbitrarily without consuming stack frames.
    * Eval is therefore stacksafe
    */

  def factorial(n: BigInt): Eval[BigInt] =
    if (n == 1) {
      Eval.now(n)
    } else {
      Eval.defer(factorial(n - 1).map(_ * n))
    }

  factorial(50000).value

  /**
    * Exercise 4.6.5
    * Safer folding using Eval
    *
    * Make the naive implementation of foldRight stacksafe
    */
  def foldRightNaive[A, B](as: List[A], acc: B)(fn: (A, B) => B): B =
    as match {
      case head :: tail =>
        fn(head, foldRightNaive(tail, acc)(fn))
      case Nil =>
        acc
    }

  def foldRightEval[A, B](as: List[A], acc: Eval[B])(fn: (A, Eval[B]) => Eval[B]): Eval[B] =
    as match {
      case head :: tail =>
        Eval.defer(fn(head, foldRightEval(tail, acc)(fn)))
      case Nil =>
        acc
    }

  def myFoldRight[A, B](as: List[A], acc: B)(fn: (A, B) => B): B = {
    foldRightEval(as, Eval.now(acc)) { (a, b) =>
      b.map(fn(a, _))
    }
  }.value


  /**
    * The Writer Monad
    * cats.data.Writer is a monad that lets us carry a log along with a computation. We can use it to record messages,
    * errors, or additional data about a computation, and extract the log alongside the final result.
    * A Writer[W, A] carries two values: a log of type W and a result of type A
    */

  val w1: WriterT[cats.Id, Vector[String], Int] = //Notice that the type is WriterT.  Writer is implemented in terms of WriterT
    Writer(
      Vector(
        "I am message one",
        "I am message two"
      ),
      42
    )

  //Syntax can be used with a way by only specifying the log or result
  //To do this we must have a Monoid[W] in scope so Cats knows how to produce an empty log:

  import cats.instances.vector._
  import cats.syntax.applicative._ // for pure

  type Logged[A] = Writer[Vector[String], A]

  123.pure[Logged]

  //If we have a log and no result we can create a Writer[Unit] using the tell syntax from cats.syntax.writer:

  import cats.syntax.writer._

  Vector("Log message 1", "Log message 2").tell


  //If we have both...

  123.writer(Vector("Log message 1", "Log message 2"))

  //To get the result...

  w1.value //cats.Id[Int] = 42

  //To get the result
  w1.written //cats.Id[Vector[String]] = Vector(I am message one, I am message two)

  //To get both

  w1.run //cats.Id[(Vector[String], Int)] = (Vector(I am message one, I am message two),42)


  /**
    * Composing and transforming writers
    * The log is preserved when we map and flatmap over it
    * It’s good practi􏰀ce to use a log type that has an efficient append and concatenate opera􏰀ons, such as a Vector:
    */

  //cats.data.WriterT[cats.Id,Vector[String],Int] =
  // WriterT((Vector(123 result, 456 result, another message),579))
  val w2 = for {
    a <- 123.pure[Logged]
    _ <- Vector("123 result").tell
    b <- 456.writer(Vector("456 result"))
    _ <- Vector("another message").tell
  } yield a + b
  w2.run
  // cats.Id[(Vector[String], Int)] = (Vector(123 result, 456 result, another message),579)


  //We can transform the logs
  w2.mapWritten(_.map(_.toUpperCase))
  // cats.data.WriterT[cats.Id,scala.collection.immutable.Vector[String],Int] = WriterT((Vector(123 RESULT, 456 RESULT, ANOTHER MESSAGE),579))

  //Or transform both
  w2.bimap(
    _.map(_.toUpperCase),
    _ * 2
  )
  // cats.data.WriterT[cats.Id,scala.collection.immutable.Vector[String],Int] = WriterT((Vector(123 RESULT, 456 RESULT, ANOTHER MESSAGE),1158))

  w2.mapBoth { (log, result) =>
    (log.map(_ + "1"), result * 3)
  }
  // cats.data.WriterT[cats.Id,scala.collection.immutable.Vector[String],Int] = WriterT((Vector(123 result!, 456 result!, another message!),1737))

  /**
    * 4.7.3 Exercise: Show Your Working
    * Transform factorial to run in parallel in such a way that, instead of interleaving the log,
    * each log is seperate
    */

  import scala.concurrent.duration._

  def slowly[A](body: => A) =
    try body finally Thread.sleep(100)

  def factorial(n: Int): Logged[Int] = {
    for {
      ans <- if (n == 0) {
        1.pure[Logged]
      } else {
        slowly(factorial(n - 1).map(_ * n))
      }
      _ <- Vector(s"fact $n $ans").tell
    } yield ans
  }

  val Vector((logA, ansA), (logB, ansB)) =
    Await.result(Future.sequence(Vector(
      Future(factorial(3).run),
      Future(factorial(5).run)
    )), 5.seconds)


  /**
    * The Reader Monad
    * cats.data.Reader is a monad that allows us to sequence opera􏰀ons that de- pend on some input. Instances of Reader
    * wrap up func􏰀ons of one argument, providing us with useful methods for composing them.
    * One common use for Readers is dependency injecti􏰀on. If we have a number of opera􏰀ons that all depend on some
    * external configurati􏰀on, we can chain them together using a Reader to produce one large opera􏰀on that accepts
    * the configurati􏰀on as a parameter and runs our program in the order specified.
    *
    */


  import cats.data.Reader

  case class Cat(name: String, favoriteFood: String)

  // Creating a Reader through the apply function
  val catName: Reader[Cat, String] = Reader(cat => cat.name)


  /**
    * 4.8.3 Exercise: Hacking on Readers
    * The classic use of Readers is to build programs that accept a configuration as a
    * parameter. Let’s ground this with a complete example of a simple login system.
    * Our configuration will consist of two databases: a list of valid users and a
    * list of their passwords:
    */

  case class Db(usernames: Map[Int, String],
                passwords: Map[String, String]
               )


  type DbReader[A] = Reader[Db, A]

  def findUsername(userId: Int): DbReader[Option[String]] = Reader(_.usernames.get(userId))

  def checkPassword(username: String,
                    password: String): DbReader[Boolean] =
    Reader(_.passwords.get(username).contains(password))

  import cats.syntax.applicative._ // for pure

  def checkLogin(userId: Int,
                 password: String): DbReader[Boolean] =
    for {
      maybeUsername <- findUsername(userId)
      passwordOk <- maybeUsername match {
        case Some(u) => checkPassword(u, password)
        case None => false.pure[DbReader]
      }
    } yield passwordOk


  val users = Map(
    1 -> "dade",
    2 -> "kate",
    3 -> "margo"
  )
  val passwords = Map(
    "dade" -> "zerocool",
    "kate" -> "acidburn",
    "margo" -> "secret"
  )

  val db = Db(users, passwords)
  checkLogin(1, "zerocool").run(db)
  // res10: cats.Id[Boolean] = true
  checkLogin(4, "davinci").run(db)
  // res11: cats.Id[Boolean] = false


  /**
    * State Monad
    * State[S,A] represents function of type S => (S , A)
    * S is the state and A is the result
    */

  import cats.data.State

  val aState = State[Int, String] { state =>
    val result = state * 2
    (result, s"The result of step a is $result")
  }

  val bState = State[Int, String] { state =>
    val result = state * 4
    (result, s"The result of step b is $result")
  }

  val both = for {
    ares <- aState
    bres <- bState
  } yield (ares, bres)

  //Get the state and result
  both.run(10).value // res0: (Int, (String, String)) = (80,(The result of step a is 20,The result of step b is 80))


  //Get the state ignore the result
  both.runS(10).value //res1: Int = 80

  //Get the result ignore the state
  both.runA(10).value //res2: (String, String) = (The result of step a is 20,The result of step b is 80)

  //Updates state and returns unit as result
  State.set[Int](10)

  // Extracts state via transformation function
  val inspectDemo = State.inspect[Int, String](_ + "!")
  inspectDemo.run(10).value // res4: (Int, String) = (10,10!)

  // Modify updates the state using a modify function
  val modifyDemo = State.modify[Int](_ + 8)
  modifyDemo.run(5).value // res5: (Int, Unit) = (13,())


  import cats.data.State._

  // Using a for comp.  We would normally ignore the intermediate steps e.g a and b
  val program: IndexedStateT[Eval, Int, Int, (Int, Int, Int)] = for {
    a <- get[Int]
    _ <- set[Int](a + 1)
    b <- get[Int]
    _ <- modify[Int](_ + 1)
    c <- inspect[Int, Int](_ * 1000)
    d <- inspect[Int, Int](_ * 1000)
  } yield (a, b, c)

  val (state, result) = program.run(1).value // result: (Int, Int, Int) = (1,2,4000)

  /**
    * 4.9.3: Post-Order calculator
    */
  import cats.data.State

  type CalcState[A] = State[List[Int], A]

  def evalOne(sym: String): CalcState[Int] = sym match {
    case "+" => operator(_ + _)
    case "-" => operator(_ - _)
    case "*" => operator(_ * _)
    case "/" => operator(_ / _)
    case s => operand(s.toInt)
  }

  def operand(num: Int): CalcState[Int] =
    State[List[Int], Int] { stack =>
      (num :: stack, num)
    }


  def operator(func: (Int, Int) => Int): CalcState[Int] =
    State[List[Int], Int] {
      case a :: b :: tail =>
        val ans = func(a, b)
        (ans :: tail, ans)
      case _ =>
        sys.error("Fail!")
    }

  evalOne("*").run(List(2, 2)).value
  evalOne("42").runA(Nil).value

  val stateProgram = for {
    _ <- evalOne("1")
    _ <- evalOne("2")
    ans <- evalOne("+")
  } yield ans
  // stateProgram: cats.data.IndexedStateT[cats.Eval,List[Int],List[Int],Int]
  //  = cats.data.IndexedStateT@4b10e96e
  stateProgram.runA(Nil).value
  // res4: Int = 3


  //Step 2 - Implement evalAll
  import cats.syntax.applicative._ // for pure
  def evalAll(input: List[String]): CalcState[Int] =
    input.foldLeft(0.pure[CalcState]) { (a, b) =>
      a.flatMap(_ => evalOne(b))
    }

  val stateProgram2 = evalAll(List("1", "2", "+", "3", "*"))
  // stateProgram: CalcState[Int] = cats.data.IndexedStateT@2e788ab0
  stateProgram2.run(Nil).value
  // res6: Int = 9

  /*
    * Step 3
    * Complete the exercise by implementing an evalInput function that splits an input String into symbols,
    * calls evalAll, and runs the result with an initial stack.
    */

  //Based on assumption that symbols are space separated
  def evalInput(str: String): Int = {
    evalAll(str.split(" ").toList).runA(Nil).value
  }

  val stateProgram3 = evalInput("1 2 + 3 *")


  // Composition
  val stateProgram4 = for {
    _   <- evalAll(List("1", "2", "+"))
    _   <- evalAll(List("3", "4", "+"))
    ans <- evalOne("*")
  } yield ans

  stateProgram4.runA(Nil).value
  //res4: Int = 21





}

