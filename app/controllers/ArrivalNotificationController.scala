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

import javax.inject.Inject
import models.messages.NormalNotification
import play.api.libs.json.{JsError, Reads}
import play.api.mvc._
import services.SubmissionService
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ArrivalNotificationController @Inject()(cc: ControllerComponents, service: SubmissionService) extends BackendController(cc) {

  def validateJson[A: Reads]: BodyParser[A] = BodyParsers.parse.json.validate(
    _.validate[A].asEither.left.map(e =>
      BadRequest(JsError.toJson(e)).as("application/json")
    ))

  def post() = Action.async(validateJson[NormalNotification]) {
    implicit request =>
      service.submit(request.body) match {
        case 200 => Future.successful(Ok.as("application/json") )
        case _ => Future.successful(BadGateway)
      }
  }
}