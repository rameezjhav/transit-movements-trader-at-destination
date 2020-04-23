/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.time.OffsetDateTime

import connectors.MessageConnector
import controllers.actions.AuthenticateActionProvider
import controllers.actions.AuthenticatedGetArrivalForWriteActionProvider
import controllers.actions.AuthenticatedGetOptionalArrivalForWriteActionProvider
import javax.inject.Inject
import models.MessageReceived.ArrivalSubmitted
import models.MessageState.SubmissionFailed
import models.MessageState.SubmissionSucceeded
import models.Arrival
import models.ArrivalId
import models.Arrivals
import models.MessageReceived
import models.MessageState
import models.MovementMessageWithState
import models.SubmissionResult
import models.request.ArrivalRequest
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.DefaultActionBuilder
import repositories.ArrivalMovementRepository
import services.ArrivalMovementService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.xml.NodeSeq

class MovementsController @Inject()(
  cc: ControllerComponents,
  arrivalMovementRepository: ArrivalMovementRepository,
  arrivalMovementService: ArrivalMovementService,
  authenticate: AuthenticateActionProvider,
  authenticatedOptionalArrival: AuthenticatedGetOptionalArrivalForWriteActionProvider,
  authenticateForWrite: AuthenticatedGetArrivalForWriteActionProvider,
  defaultActionBuilder: DefaultActionBuilder,
  messageConnector: MessageConnector
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  private val logger = Logger(getClass)

  def post: Action[NodeSeq] = authenticatedOptionalArrival().async(parse.xml) {
    implicit request =>
      request.arrival match {
        case Some(arrival) => appendNewArrivalMessageToMovement(arrival, request.body)
        case None =>
          arrivalMovementService.makeArrivalMovement(request.eoriNumber)(request.body) match {
            case None =>
              Future.successful(BadRequest("Invalid data: missing either DatOfPreMES9, TimOfPreMES10 or DocNumHEA5"))
            case Some(arrivalFuture) =>
              arrivalFuture
                .flatMap {
                  arrival =>
                    arrivalMovementRepository.insert(arrival) flatMap {
                      _ =>
                        messageConnector
                        // TODO: Fix this casting
                          .post(arrival.arrivalId, arrival.messages.head.asInstanceOf[MovementMessageWithState], OffsetDateTime.now)
                          .flatMap {
                            _ =>
                              for {
                                _ <- arrivalMovementRepository
                                  .setMessageState(arrival.arrivalId, 0, SubmissionSucceeded) // TODO: use the message's state transition here and don't hard code the index of the message
                                _ <- arrivalMovementRepository.setState(arrival.arrivalId, arrival.state.transition(ArrivalSubmitted))
                              } yield {
                                Accepted("Message accepted")
                                  .withHeaders("Location" -> routes.MovementsController.getArrival(arrival.arrivalId).url)
                              }
                          }
                          .recoverWith {
                            case error =>
                              logger.error(s"Call to EIS failed with the following Exception: ${error.getMessage}")
                              arrivalMovementRepository
                                .setMessageState(arrival.arrivalId, arrival.messages.length - 1, SubmissionFailed)
                                .map(_ => BadGateway)
                          }
                    }
                }
                .recover {
                  case _ => {
                    InternalServerError
                  }
                }
          }
      }

  }

  private def appendNewArrivalMessageToMovement(arrival: Arrival, body: NodeSeq)(implicit hc: HeaderCarrier) =
    arrivalMovementService.makeArrivalNotificationMessage(arrival.nextMessageCorrelationId)(body) match {
      case None => Future.successful(BadRequest("Invalid data: missing either DatOfPreMES9, TimOfPreMES10 or DocNumHEA5"))
      case Some(message) => {
        arrivalMovementRepository.addNewMessage(arrival.arrivalId, message).flatMap {
          case Failure(_) =>
            Future.successful(InternalServerError)
          case Success(()) =>
            messageConnector
              .post(arrival.arrivalId, message, OffsetDateTime.now)
              .flatMap {
                _ =>
                  for {
                    _ <- arrivalMovementRepository
                      .setMessageState(arrival.arrivalId, arrival.messages.length, MessageState.SubmissionSucceeded)
                    _ <- arrivalMovementRepository.setState(arrival.arrivalId, arrival.state.transition(MessageReceived.ArrivalSubmitted))
                  } yield {
                    Accepted("Message accepted")
                      .withHeaders("Location" -> routes.MovementsController.getArrival(arrival.arrivalId).url)
                  }
              }
              .recoverWith {
                case error =>
                  logger.error(s"Call to EIS failed with the following Exception: ${error.getMessage}")
                  arrivalMovementRepository
                    .setMessageState(arrival.arrivalId, arrival.messages.length, message.state.transition(SubmissionResult.Failure))
                    .map {
                      _ =>
                        BadGateway
                    }
              }

        }
      }
    }

  def putArrival(arrivalId: ArrivalId): Action[NodeSeq] = authenticateForWrite(arrivalId).async(parse.xml) {
    implicit request: ArrivalRequest[NodeSeq] =>
      appendNewArrivalMessageToMovement(request.arrival, request.body)
  }

  def getArrival(arrivalId: ArrivalId): Action[AnyContent] = defaultActionBuilder(_ => NotImplemented)

  def getArrivals(): Action[AnyContent] = authenticate().async {
    implicit request =>
      arrivalMovementRepository
        .fetchAllArrivals(request.eoriNumber)
        .map {
          allArrivals =>
            Ok(Json.toJsObject(Arrivals(allArrivals)))
        }
        .recover {
          case e =>
            InternalServerError(s"Failed with the following error: $e")
        }
  }
}
