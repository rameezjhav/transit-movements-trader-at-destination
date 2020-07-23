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

import controllers.actions.AuthenticatedGetArrivalForReadActionProvider
import javax.inject.Inject
import models.ArrivalId
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import services.UnloadingPermissionPDFService
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext

class PDFGenerationController @Inject()(cc: ControllerComponents,
                                        authenticateForRead: AuthenticatedGetArrivalForReadActionProvider,
                                        unloadingPermissionPDFService: UnloadingPermissionPDFService)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def getPDF(arrivalId: ArrivalId): Action[AnyContent] = authenticateForRead(arrivalId).async {
    implicit request =>
      unloadingPermissionPDFService.getPDF(request.arrival).map {
        response =>
          response
            .map {
              result =>
                result.status match {
                  case 200 => Ok(result.body)
                  case _   => BadRequest
                }
            }
            .getOrElse(NotFound)
      }
  }
}