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

package services

import java.time.LocalDate
import java.time.LocalTime

import base.SpecBase
import models.ArrivalId
import models.ArrivalStatus._
import models.GoodsReleasedResponse
import models.MessageSender
import models.SubmissionResult
import repositories.ArrivalMovementRepository
import utils.Format
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterEach
import play.api.inject.bind

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class SaveMessageServiceSpec extends SpecBase with BeforeAndAfterEach {

  private val mockArrivalMovementRepository = mock[ArrivalMovementRepository]
  private val mockXmlValidationService      = mock[XmlValidationService]

  override def beforeEach = {
    super.beforeEach()
    reset(mockArrivalMovementRepository)
    reset(mockXmlValidationService)
  }

  "doLotsOfThings" - {

    "Returns Success when we successfully save a message" in {
      when(mockArrivalMovementRepository.addResponseMessage(any(), any(), any())).thenReturn(Future.successful(Success(())))
      when(mockXmlValidationService.validate(any(), any())).thenReturn(Success(()))

      val application = baseApplicationBuilder
        .overrides(
          bind[ArrivalMovementRepository].toInstance(mockArrivalMovementRepository),
          bind[XmlValidationService].toInstance(mockXmlValidationService)
        )
        .build()

      val saveMessageService = application.injector.instanceOf[SaveMessageService]

      val dateOfPrep = LocalDate.now()
      val timeOfPrep = LocalTime.of(1, 1)

      val arrivalId            = ArrivalId(1)
      val messageCorrelationId = 1
      val messageSender        = MessageSender(arrivalId, messageCorrelationId)

      val requestGoodsReleasedXmlBody =
        <CC025A>
        <DatOfPreMES9>{Format.dateFormatted(dateOfPrep)}</DatOfPreMES9>
        <TimOfPreMES10>{Format.timeFormatted(timeOfPrep)}</TimOfPreMES10>
      </CC025A>

      val result = saveMessageService.validateXmlAndSaveMessage(requestGoodsReleasedXmlBody, messageSender, GoodsReleasedResponse, GoodsReleased).futureValue

      result mustBe SubmissionResult.Success
      verify(mockArrivalMovementRepository, times(1)).addResponseMessage(eqTo(arrivalId), any(), eqTo(GoodsReleased))
    }

    "return Failure when we cannot save the message" in {
      when(mockArrivalMovementRepository.addResponseMessage(any(), any(), any())).thenReturn(Future.successful(Failure(new Exception)))
      when(mockXmlValidationService.validate(any(), any())).thenReturn(Success(()))

      val application = baseApplicationBuilder
        .overrides(
          bind[ArrivalMovementRepository].toInstance(mockArrivalMovementRepository),
          bind[XmlValidationService].toInstance(mockXmlValidationService)
        )
        .build()

      val saveMessageService = application.injector.instanceOf[SaveMessageService]

      val dateOfPrep = LocalDate.now()
      val timeOfPrep = LocalTime.of(1, 1)

      val arrivalId            = ArrivalId(1)
      val messageCorrelationId = 1
      val messageSender        = MessageSender(arrivalId, messageCorrelationId)

      val requestGoodsReleasedXmlBody =
        <CC025A>
          <DatOfPreMES9>{Format.dateFormatted(dateOfPrep)}</DatOfPreMES9>
          <TimOfPreMES10>{Format.timeFormatted(timeOfPrep)}</TimOfPreMES10>
        </CC025A>

      val result = saveMessageService.validateXmlAndSaveMessage(requestGoodsReleasedXmlBody, messageSender, GoodsReleasedResponse, GoodsReleased).futureValue

      result mustBe SubmissionResult.FailureInternal
      verify(mockArrivalMovementRepository, times(1)).addResponseMessage(any(), any(), any())
    }

    "return Failure when we cannot parse the message" in {
      when(mockXmlValidationService.validate(any(), any())).thenReturn(Failure(new Exception))

      val application = baseApplicationBuilder
        .overrides(
          bind[ArrivalMovementRepository].toInstance(mockArrivalMovementRepository),
          bind[XmlValidationService].toInstance(mockXmlValidationService)
        )
        .build()

      val saveMessageService = application.injector.instanceOf[SaveMessageService]

      val arrivalId            = ArrivalId(1)
      val messageCorrelationId = 1
      val messageSender        = MessageSender(arrivalId, messageCorrelationId)

      val requestInvalidXmlBody = <Invalid> invalid </Invalid>

      val result = saveMessageService.validateXmlAndSaveMessage(requestInvalidXmlBody, messageSender, GoodsReleasedResponse, GoodsReleased).futureValue

      result mustBe SubmissionResult.FailureExternal
      verify(mockArrivalMovementRepository, never()).addResponseMessage(any(), any(), any())

    }

  }
}
