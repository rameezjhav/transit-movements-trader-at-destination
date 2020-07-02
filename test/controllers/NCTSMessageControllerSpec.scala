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

import base.SpecBase
import controllers.actions._
import generators.ModelGenerators
import models.Arrival
import models.ArrivalId
import models.MessageSender
import models.SubmissionProcessingResult
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalacheck.Arbitrary
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.ArrivalMovementRepository
import repositories.LockRepository
import services.SaveMessageService

import scala.concurrent.Future

class NCTSMessageControllerSpec extends SpecBase with ScalaCheckPropertyChecks with ModelGenerators with BeforeAndAfterEach {

  private val mockArrivalMovementRepository: ArrivalMovementRepository = mock[ArrivalMovementRepository]
  private val mockLockRepository: LockRepository                       = mock[LockRepository]
  private val mockSaveMessageService: SaveMessageService               = mock[SaveMessageService]

  private val arrivalId     = ArrivalId(1)
  private val version       = 1
  private val messageSender = MessageSender(arrivalId, version)

  private val arrival = Arbitrary.arbitrary[Arrival].sample.value

  private val xml = <test></test>

  override def beforeEach: Unit = {
    super.beforeEach()
    reset(mockArrivalMovementRepository)
    reset(mockLockRepository)
    reset(mockSaveMessageService)
  }

  "post" - {

    "when a lock can be acquired" - {
      "must return OK, when the service validates and save the message" in {

        when(mockArrivalMovementRepository.get(any())).thenReturn(Future.successful(Some(arrival)))
        when(mockSaveMessageService.validateXmlAndSaveMessage(any(), any(), any(), any()))
          .thenReturn(Future.successful(SubmissionProcessingResult.SubmissionSuccess))
        when(mockLockRepository.lock(any())).thenReturn(Future.successful(true))
        when(mockLockRepository.unlock(any())).thenReturn(Future.successful(()))

        val application = baseApplicationBuilder
          .overrides(
            bind[ArrivalMovementRepository].toInstance(mockArrivalMovementRepository),
            bind[LockRepository].toInstance(mockLockRepository),
            bind[SaveMessageService].toInstance(mockSaveMessageService),
            bind[InboundMessageTransformerInterface].to[FakeInboundMessageTransformer]
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.NCTSMessageController.post(messageSender).url)
            .withXmlBody(xml)

          val result = route(application, request).value

          status(result) mustEqual OK
        }
      }

      "must lock, return NotFound and unlock when given a message for an arrival that does not exist" in {
        when(mockArrivalMovementRepository.get(any())).thenReturn(Future.successful(None))
        when(mockLockRepository.lock(any())).thenReturn(Future.successful(true))
        when(mockLockRepository.unlock(any())).thenReturn(Future.successful(()))

        val application = baseApplicationBuilder
          .overrides(
            bind[ArrivalMovementRepository].toInstance(mockArrivalMovementRepository),
            bind[LockRepository].toInstance(mockLockRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.NCTSMessageController.post(messageSender).url)
            .withXmlBody(xml)

          val result = route(application, request).value

          status(result) mustEqual NOT_FOUND
          verify(mockArrivalMovementRepository, never).addResponseMessage(any(), any(), any())
          verify(mockLockRepository, times(1)).lock(arrivalId)
          verify(mockLockRepository, times(1)).unlock(arrivalId)
        }
      }

      "must lock, return Internal Server Error and unlock if adding the message to the movement fails" in {
        when(mockArrivalMovementRepository.get(any())).thenReturn(Future.successful(Some(arrival)))
        when(mockSaveMessageService.validateXmlAndSaveMessage(any(), any(), any(), any()))
          .thenReturn(Future.successful(SubmissionProcessingResult.SubmissionFailureInternal))
        when(mockLockRepository.lock(any())).thenReturn(Future.successful(true))
        when(mockLockRepository.unlock(any())).thenReturn(Future.successful(()))

        val application = baseApplicationBuilder
          .overrides(
            bind[ArrivalMovementRepository].toInstance(mockArrivalMovementRepository),
            bind[LockRepository].toInstance(mockLockRepository),
            bind[SaveMessageService].toInstance(mockSaveMessageService),
            bind[InboundMessageTransformerInterface].to[FakeInboundMessageTransformer]
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.NCTSMessageController.post(messageSender).url)
            .withXmlBody(xml)

          val result = route(application, request).value

          status(result) mustEqual INTERNAL_SERVER_ERROR
          verify(mockLockRepository, times(1)).lock(arrivalId)
          verify(mockLockRepository, times(1)).unlock(arrivalId)
        }
      }

      "must lock the arrival, return BadRequest error and unlock when an XMessageType is invalid" in {

        when(mockArrivalMovementRepository.get(any())).thenReturn(Future.successful(Some(arrival)))
        when(mockLockRepository.lock(any())).thenReturn(Future.successful(true))
        when(mockLockRepository.unlock(any())).thenReturn(Future.successful(()))

        val application = baseApplicationBuilder
          .overrides(
            bind[ArrivalMovementRepository].toInstance(mockArrivalMovementRepository),
            bind[LockRepository].toInstance(mockLockRepository),
            bind[SaveMessageService].toInstance(mockSaveMessageService),
            bind[InboundMessageTransformerInterface].to[FakeInboundMessageNoneTransformer]
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.NCTSMessageController.post(messageSender).url)
            .withXmlBody(xml)

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          verify(mockLockRepository, times(1)).lock(arrivalId)
          verify(mockSaveMessageService, never()).validateXmlAndSaveMessage(any(), any(), any(), any())
          verify(mockLockRepository, times(1)).unlock(arrivalId)
        }
      }

      "must lock the arrival, return BadRequest error and unlock when fail to validate message" in {
        when(mockArrivalMovementRepository.get(any())).thenReturn(Future.successful(Some(arrival)))
        when(mockSaveMessageService.validateXmlAndSaveMessage(any(), any(), any(), any()))
          .thenReturn(Future.successful(SubmissionProcessingResult.SubmissionFailureExternal))
        when(mockLockRepository.lock(any())).thenReturn(Future.successful(true))
        when(mockLockRepository.unlock(any())).thenReturn(Future.successful(()))

        val application = baseApplicationBuilder
          .overrides(
            bind[ArrivalMovementRepository].toInstance(mockArrivalMovementRepository),
            bind[LockRepository].toInstance(mockLockRepository),
            bind[SaveMessageService].toInstance(mockSaveMessageService),
            bind[InboundMessageTransformerInterface].to[FakeInboundMessageTransformer]
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.NCTSMessageController.post(messageSender).url)
            .withXmlBody(xml)

          val result = route(application, request).value

          status(result) mustEqual BAD_REQUEST
          verify(mockLockRepository, times(1)).lock(arrivalId)
          verify(mockSaveMessageService, times(1)).validateXmlAndSaveMessage(any(), any(), any(), any())
          verify(mockLockRepository, times(1)).unlock(arrivalId)
        }
      }

    }

    "when a lock cannot be acquired" - {

      "must return Locked" in {
        when(mockArrivalMovementRepository.get(any())).thenReturn(Future.successful(Some(arrival)))
        when(mockLockRepository.lock(any())).thenReturn(Future.successful(false))

        val application = baseApplicationBuilder
          .overrides(
            bind[ArrivalMovementRepository].toInstance(mockArrivalMovementRepository),
            bind[LockRepository].toInstance(mockLockRepository)
          )
          .build()

        running(application) {
          val request = FakeRequest(POST, routes.NCTSMessageController.post(messageSender).url)
            .withXmlBody(xml)

          val result = route(application, request).value

          status(result) mustEqual LOCKED
        }
      }
    }
  }
}
