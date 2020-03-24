package services.repositories

import java.time.LocalDate
import java.time.LocalTime

import generators.MessageGenerators
import models.Arrival
import models.ArrivalMovement
import models.MessageType
import models.MovementMessage
import models.State
import models.request.ArrivalId
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.EitherValues
import org.scalatest.FreeSpec
import org.scalatest.MustMatchers
import org.scalatest.OptionValues
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection
import repositories.ArrivalMovementRepository
import repositories.CollectionNames
import services.mocks.MockDateTimeService
import utils.Format

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scala.util.Success

class ArrivalMovementRepositorySpec
    extends FreeSpec
    with MustMatchers
    with MongoSuite
    with FailOnUnindexedQueries
    with ScalaFutures
    with IntegrationPatience
    with MockDateTimeService
    with OptionValues
    with EitherValues
    with ScalaCheckPropertyChecks
    with MessageGenerators {

  private val eoriNumber: String                = arbitrary[String].sample.value
  private val arrivalMovement1: ArrivalMovement = arbitrary[ArrivalMovement].sample.value.ensuring(_.eoriNumber != eoriNumber)
  private val arrivalMovement2: ArrivalMovement = arbitrary[ArrivalMovement].sample.value.ensuring(_.eoriNumber != eoriNumber)

  private lazy val builder = new GuiceApplicationBuilder()

  override def beforeEach(): Unit = {
    super.beforeEach()
    database.flatMap(_.drop()).futureValue
  }

  "ArrivalMovementRepository" - {
    "insert" - {
      "must persist ArrivalMovement within mongoDB" in {

        val app: Application = builder.build()

        val arrival = arbitrary[Arrival].sample.value

        running(app) {
          started(app).futureValue

          val repository = app.injector.instanceOf[ArrivalMovementRepository]

          repository.insert(arrival).futureValue

          val selector = Json.obj("eoriNumber" -> arrival.eoriNumber)

          val result = database.flatMap {
            result =>
              result.collection[JSONCollection](CollectionNames.ArrivalMovementCollection).find(selector, None).one[Arrival]
          }.futureValue

          result.value mustBe arrival
        }
      }
    }

    "setState" - {
      "must update the state of an existing record" in {

        val app: Application = builder.build()

        val arrival = arbitrary[Arrival].sample.value copy (state = State.PendingSubmission)

        running(app) {
          started(app).futureValue

          val repository = app.injector.instanceOf[ArrivalMovementRepository]

          repository.insert(arrival).futureValue
          repository.setState(arrival.arrivalId, State.Submitted).futureValue

          val selector = Json.obj("_id" -> arrival.arrivalId)

          val result = database.flatMap {
            result =>
              result.collection[JSONCollection](CollectionNames.ArrivalMovementCollection).find(selector, None).one[Arrival]
          }.futureValue

          result.value.state mustEqual State.Submitted
        }
      }
    }

    "addMessage" - {
      "must add a message and update the state of a document" in {

        val app: Application = builder.build()

        val arrival = arbitrary[Arrival].sample.value

        val dateOfPrep = LocalDate.now()
        val timeOfPrep = LocalTime.of(1, 1)
        val messageBody =
          <CC025A>
            <DatOfPreMES9>{Format.dateFormatted(dateOfPrep)}</DatOfPreMES9>
            <TimOfPreMES10>{Format.timeFormatted(timeOfPrep)}</TimOfPreMES10>
            <HEAHEA>
              <DocNumHEA5>MRN</DocNumHEA5>
            </HEAHEA>
          </CC025A>

        val goodsReleasedMessage = MovementMessage(dateOfPrep, timeOfPrep, MessageType.GoodsReleased, messageBody)
        val newState             = State.GoodsReleased

        running(app) {
          started(app).futureValue

          val repository = app.injector.instanceOf[ArrivalMovementRepository]

          repository.insert(arrival).futureValue
          val addMessageResult = repository.addMessage(arrival.arrivalId, goodsReleasedMessage, newState).futureValue

          val selector = Json.obj("_id" -> arrival.arrivalId)

          val result = database.flatMap {
            result =>
              result.collection[JSONCollection](CollectionNames.ArrivalMovementCollection).find(selector, None).one[Arrival]
          }.futureValue

          val updatedArrival = result.value

          addMessageResult mustBe a[Success[Unit]]
          updatedArrival.state mustEqual newState
          updatedArrival.messages.size mustEqual arrival.messages.size + 1
          updatedArrival.messages.last mustEqual goodsReleasedMessage
        }
      }

      "must fail if the arrival cannot be found" in {

        val app: Application = builder.build()

        val arrival = arbitrary[Arrival].sample.value copy (state = State.Submitted, arrivalId = ArrivalId(1))

        val dateOfPrep = LocalDate.now()
        val timeOfPrep = LocalTime.of(1, 1)
        val messageBody =
          <CC025A>
            <DatOfPreMES9>{Format.dateFormatted(dateOfPrep)}</DatOfPreMES9>
            <TimOfPreMES10>{Format.timeFormatted(timeOfPrep)}</TimOfPreMES10>
            <HEAHEA>
              <DocNumHEA5>MRN</DocNumHEA5>
            </HEAHEA>
          </CC025A>

        val goodsReleasedMessage = MovementMessage(dateOfPrep, timeOfPrep, MessageType.GoodsReleased, messageBody)
        val newState             = State.GoodsReleased

        running(app) {
          started(app).futureValue

          val repository = app.injector.instanceOf[ArrivalMovementRepository]

          repository.insert(arrival).futureValue
          val result = repository.addMessage(ArrivalId(2), goodsReleasedMessage, newState).futureValue

          result mustBe a[Failure[Unit]]
        }
      }
    }

    "persistToMongo" - {
      "must persist ArrivalMovement within mongoDB" in {

        val app: Application = builder.build()

        running(app) {
          started(app).futureValue

          val service: ArrivalMovementRepository = app.injector.instanceOf[ArrivalMovementRepository]

          service.persistToMongo(arrivalMovement1).futureValue

          val selector = Json.obj("eoriNumber" -> arrivalMovement1.eoriNumber)

          val getValue: Option[ArrivalMovement] = database.flatMap {
            result =>
              result.collection[JSONCollection](CollectionNames.ArrivalMovementCollection).find(selector, None).one[ArrivalMovement]
          }.futureValue

          getValue.value mustBe arrivalMovement1
        }
      }
    }

    "get" - {
      "must get an arrival when it exists" in {
        val app: Application = builder.build()

        running(app) {
          started(app).futureValue

          val repository = app.injector.instanceOf[ArrivalMovementRepository]

          val arrival = arbitrary[Arrival].sample.value

          repository.insert(arrival).futureValue
          val result = repository.get(arrival.arrivalId).futureValue

          result.value mustEqual arrival
        }
      }

      "must return None when an arrival does not exist" in {

        val app: Application = builder.build()

        running(app) {
          started(app).futureValue

          val repository = app.injector.instanceOf[ArrivalMovementRepository]

          val arrival = arbitrary[Arrival].sample.value copy (arrivalId = ArrivalId(1))

          repository.insert(arrival).futureValue
          val result = repository.get(ArrivalId(2)).futureValue

          result must not be defined
        }
      }
    }

    "fetchAllMovements" - {
      "must fetch all the ArrivalMovements from mongoDB and filter non-matching eori's" in {
        val app: Application = builder.build()

        running(app) {
          started(app).futureValue

          val service: ArrivalMovementRepository = app.injector.instanceOf[ArrivalMovementRepository]

          val allMovements = Seq(arrivalMovement1, arrivalMovement2)

          val jsonArr = allMovements.map(Json.toJsObject(_))

          database.flatMap {
            db =>
              db.collection[JSONCollection](CollectionNames.ArrivalMovementCollection).insert(false).many(jsonArr)
          }.futureValue

          val result: Seq[ArrivalMovement] = service.fetchAllMovements(arrivalMovement1.eoriNumber).futureValue

          result mustBe Seq(arrivalMovement1)
        }
      }

      "must return an empty sequence when there are no movements with the same eori" in {
        val app: Application = builder.build()

        running(app) {
          started(app).futureValue

          val service: ArrivalMovementRepository = app.injector.instanceOf[ArrivalMovementRepository]

          val allMovements = Seq(arrivalMovement1, arrivalMovement2)

          val jsonArr = allMovements.map(Json.toJsObject(_))

          database.flatMap {
            db =>
              db.collection[JSONCollection](CollectionNames.ArrivalMovementCollection).insert(false).many(jsonArr)
          }.futureValue

          val result: Seq[ArrivalMovement] = service.fetchAllMovements(eoriNumber).futureValue

          result mustBe Seq.empty[ArrivalMovement]
        }
      }
    }
  }

}
