/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.LocalDateTime

import config.AppConfig
import connectors.MessageConnector
import javax.inject.Inject
import models.ArrivalNotificationXSD
import models.messages.ArrivalNotification
import models.messages.request.{ArrivalNotificationRequest, _}
import play.api.libs.json.{JsError, Reads}
import play.api.mvc._
import reactivemongo.api.commands.WriteResult
import repositories.FailedSavingArrivalNotification
import services._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.Node

class ArrivalNotificationController @Inject()(
                                               cc: ControllerComponents,
                                               bodyParsers: PlayBodyParsers,
                                               appConfig: AppConfig,
                                               databaseService: DatabaseService,
                                               messageConnector: MessageConnector,
                                               submissionModelService: SubmissionModelService,
                                               xmlBuilderService: XmlBuilderService,
                                               xmlValidationService: XmlValidationService
                                             )
  extends BackendController(cc) {

  def post(): Action[ArrivalNotification] = Action.async(validateJson[ArrivalNotification]) {
    implicit request =>

      val messageSender = MessageSender(appConfig.env, "eori")

      val arrivalNotification = request.body

      implicit val localDateTime: LocalDateTime = LocalDateTime.now()

      databaseService.getInterchangeControlReferenceId.flatMap {

        case Right(interchangeControlReferenceId) => {
          submissionModelService.convertToSubmissionModel(arrivalNotification, messageSender, interchangeControlReferenceId) match {

            case Right(arrivalNotificationRequestModel) => {
              xmlBuilderService.buildXml(arrivalNotificationRequestModel) match {

                case Right(xml) => {
                  xmlValidationService.validate(xml.toString(), ArrivalNotificationXSD) match {

                    case Right(XmlSuccessfullyValidated) => {
                      databaseService.saveArrivalNotification(arrivalNotification).flatMap {

                        sendMessage(xml, arrivalNotificationRequestModel)

                      }.recover {
                        case _ => InternalServerError
                      }
                    }
                    case Left(FailedToValidateXml) =>
                      Future.successful(BadRequest)
                    case Left(FailedFindingXSDFile) =>
                      Future.successful(InternalServerError)
                  }
                }
                case Left(FailedToCreateXml) =>
                  Future.successful(InternalServerError)
              }
            }
            case Left(FailedToConvertModel) =>
              Future.successful(BadRequest)
          }
        }
        case Left(FailedCreatingInterchangeControlReference) =>
          Future.successful(InternalServerError)
      }
  }

  private def sendMessage(xml: Node, arrivalNotificationRequestModel: ArrivalNotificationRequest)(implicit headerCarrier: HeaderCarrier): PartialFunction[Either[FailedSavingArrivalNotification, WriteResult], Future[Result]] = {
    case Right(_) => {
      messageConnector.post(xml.toString, arrivalNotificationRequestModel.messageCode).map {
        _ => NoContent
      }
      .recover {
        case _ => BadGateway
      }
    }
    case Left(FailedSavingArrivalNotification) =>
      Future.successful(InternalServerError)
  }

  private def validateJson[A: Reads]: BodyParser[A] = bodyParsers.json.validate(
    _.validate[A].asEither.left.map(e =>
      BadRequest(JsError.toJson(e)).as("application/json")
    ))

}
