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

import controllers.actions.ArrivalRetrievalActionProvider
import javax.inject.Inject
import models.MessageReceived
import models.MessageSender
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import repositories.ArrivalMovementRepository
import repositories.LockRepository
import services.ArrivalMovementService
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.xml.NodeSeq

class GoodsReleasedController @Inject()(
  cc: ControllerComponents,
  arrivalMovementService: ArrivalMovementService,
  getArrival: ArrivalRetrievalActionProvider,
  arrivalMovementRepository: ArrivalMovementRepository,
  lockRepository: LockRepository
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def post(messageSender: MessageSender): Action[NodeSeq] = getArrival(messageSender.arrivalId)(parse.xml).async {
    implicit request =>
      lockRepository.lock(messageSender.arrivalId).flatMap {
        case true =>
          arrivalMovementService.makeGoodsReleasedMessage()(request.request.body) match {
            case Some(message) =>
              val newState = request.arrival.state.transition(MessageReceived.GoodsReleased)
              arrivalMovementRepository.addMessage(request.arrival.arrivalId, message, newState).flatMap {
                messageAdded =>
                  lockRepository.unlock(request.arrival.arrivalId).map {
                    _ =>
                      messageAdded match {
                        case Success(_) => Ok
                        case Failure(_) => InternalServerError
                      }
                  }
              }
            case None =>
              lockRepository.unlock(request.arrival.arrivalId).map {
                _ =>
                  InternalServerError
              }
          }
        case false =>
          Future.successful(Locked)
      }
  }
}
