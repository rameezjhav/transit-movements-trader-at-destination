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

import base.SpecBase
import cats.data._
import generators.ModelGenerators
import models.MessageStatus._
import models.MessageType._
import models.Arrival
import models.MessageId
import models.MessageType
import models.MessagesSummary
import models.MovementMessage
import models.MovementMessageWithStatus
import models.MovementMessageWithoutStatus
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class ArrivalMessageSummaryServiceSpec extends SpecBase with ModelGenerators with ScalaCheckDrivenPropertyChecks {
  import ArrivalMessageSummaryServiceSpec.MovementMessagesHelpers._

  def messageGeneratorSent(messageType: MessageType): Gen[MovementMessageWithStatus] = {
    val message = xml.XML.loadString(s"<${messageType.rootNode}>test</${messageType.rootNode}>")
    arbitrary[MovementMessageWithStatus].map(_.copy(messageType = messageType, message = message, status = SubmissionPending))
  }

  def messageGeneratorResponse(messageType: MessageType): Gen[MovementMessageWithoutStatus] = {
    val message = xml.XML.loadString(s"<${messageType.rootNode}>test</${messageType.rootNode}>")
    arbitrary[MovementMessageWithoutStatus].map(_.copy(messageType = messageType, message = message))
  }

  val ie007Gen = messageGeneratorSent(ArrivalNotification)
  val ie008Gen = messageGeneratorResponse(ArrivalRejection)
  val ie043Gen = messageGeneratorResponse(UnloadingPermission)
  val ie044Gen = messageGeneratorResponse(UnloadingRemarks)
  val ie058Gen = messageGeneratorResponse(UnloadingRemarksRejection)

  def arrivalMovement(msgs: NonEmptyList[MovementMessage]): Gen[Arrival] =
    for {
      arrival <- arbitrary[Arrival]
    } yield arrival.copy(messages = msgs)

  "arrivalNotificationR" - {

    "must return the original IE007 when there have been no other messages" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen) {
        ie007 =>
          forAll(arrivalMovement(NonEmptyList.one(ie007))) {
            arrival =>
              val (message, messageId) = service.arrivalNotificationR(arrival)

              message mustEqual ie007
              messageId mustEqual MessageId.fromMessageIdValue(1).value

          }
      }
    }

    "must return the original IEOO7 and first IE008 when there is only an IE007 and a IE008" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen, ie008Gen) {
        (ie007, ie008) =>
          val messages = NonEmptyList.of(ie007, ie008)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.arrivalNotificationR(arrival)

              message mustEqual ie007
              messageId mustEqual MessageId.fromMessageIdValue(1).value
          }
      }

    }

    "must return the new IEOO7 when there has been a correction to a rejected arrival message" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie008Gen.msgCorrId(1), ie007Gen.msgCorrId(2)) {
        case (ie007Old, ie008Old, ie007) =>
          val messages = NonEmptyList.of(ie007Old, ie008Old, ie007)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.arrivalNotificationR(arrival)

              message mustEqual ie007
              messageId mustEqual MessageId.fromMessageIdValue(3).value
          }
      }

    }

    "must return the latest IEOO7 when all IE007 have been rejected" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie008Gen.msgCorrId(1), ie007Gen.msgCorrId(2), ie008Gen.msgCorrId(2)) {
        case (ie007Old, ie008Old, ie007, ie008) =>
          val messages = NonEmptyList.of(ie007Old, ie008Old, ie007, ie008)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.arrivalNotificationR(arrival)

              message mustEqual ie007
              messageId mustEqual MessageId.fromMessageIdValue(3).value
          }
      }

    }

  }

  "arrivalRejectionR" - {

    "must return None when there are none in the movement" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen) {
        ie007 =>
          forAll(arrivalMovement(NonEmptyList.one(ie007))) {
            arrival =>
              service.arrivalRejectionR(arrival) must not be (defined)

          }
      }
    }

    "must the latest IE008 when there is only an IE007 and a IE008" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen, ie008Gen) {
        (ie007, ie008) =>
          val messages = NonEmptyList.of(ie007, ie008)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.arrivalRejectionR(arrival).value

              message mustEqual ie008
              messageId mustEqual MessageId.fromMessageIdValue(2).value
          }
      }

    }

    "must return None when there has been an rejected arrival and correction arrival" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie008Gen.msgCorrId(1), ie007Gen.msgCorrId(2)) {
        case (ie007Old, ie008Old, ie007) =>
          val messages = NonEmptyList.of(ie007Old, ie008Old, ie007)

          forAll(arrivalMovement(messages)) {
            arrival =>
              service.arrivalRejectionR(arrival) must not be (defined)
          }
      }

    }

    "must return IE008 when all IE007 have been rejected" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie008Gen.msgCorrId(1), ie007Gen.msgCorrId(2), ie008Gen.msgCorrId(2)) {
        case (ie007Old, ie008Old, ie007, ie008) =>
          val messages = NonEmptyList.of(ie007Old, ie008Old, ie007, ie008)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.arrivalRejectionR(arrival).value

              message mustEqual ie008
              messageId mustEqual MessageId.fromMessageIdValue(4).value
          }
      }

    }

  }

  "unloadingPermissionR" - {

    "must return None when there are none in the movement" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen) {
        ie007 =>
          forAll(arrivalMovement(NonEmptyList.one(ie007))) {
            arrival =>
              service.unloadingPermissionR(arrival) must not be (defined)

          }
      }
    }

    "must return the latest IE043 when there is only an IE007 and a IE043" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen, ie043Gen) {
        (ie007, ie043) =>
          val messages = NonEmptyList.of(ie007, ie043)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingPermissionR(arrival).value

              message mustEqual ie043
              messageId mustEqual MessageId.fromMessageIdValue(2).value
          }
      }

    }

    "must return last IE043 when multiple unloading permission messages exist" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie043Gen.msgCorrId(2), ie043Gen.msgCorrId(3)) {
        case (ie007, ie043Old, ie043) =>
          val messages = NonEmptyList.of(ie007, ie043Old, ie043)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingPermissionR(arrival).value

              message mustEqual ie043
              messageId mustEqual MessageId.fromMessageIdValue(3).value
          }
      }

    }

    "must return IE043 when IE044 exists" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie043Gen.msgCorrId(2), ie044Gen.msgCorrId(3)) {
        case (ie007, ie043, ie044) =>
          val messages = NonEmptyList.of(ie007, ie043, ie044)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingPermissionR(arrival).value

              message mustEqual ie043
              messageId mustEqual MessageId.fromMessageIdValue(2).value
          }
      }

    }

    "must return IE043 when IE044 and IE058 exists" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie043Gen.msgCorrId(2), ie044Gen.msgCorrId(3), ie058Gen.msgCorrId(3)) {
        case (ie007, ie043, ie044, ie058) =>
          val messages = NonEmptyList.of(ie007, ie043, ie044, ie058)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingPermissionR(arrival).value

              message mustEqual ie043
              messageId mustEqual MessageId.fromMessageIdValue(2).value
          }
      }

    }

  }

  "unloadingRemarksR" - {

    "must return None when there are none in the movement" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen) {
        ie007 =>
          forAll(arrivalMovement(NonEmptyList.one(ie007))) {
            arrival =>
              service.unloadingRemarksR(arrival) must not be (defined)

          }
      }
    }

    "must return IE044 when there is only one" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen, ie043Gen, ie044Gen) {
        (ie007, ie043, ie044) =>
          val messages = NonEmptyList.of(ie007, ie043, ie044)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingRemarksR(arrival).value

              message mustEqual ie044
              messageId mustEqual MessageId.fromMessageIdValue(3).value
          }
      }

    }

    "must return last IE044 when multiple unloading remarks messages exist" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie043Gen.msgCorrId(2), ie044Gen.msgCorrId(3), ie044Gen.msgCorrId(4)) {
        case (ie007, ie043, ie044Old, ie044) =>
          val messages = NonEmptyList.of(ie007, ie043, ie044Old, ie044)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingRemarksR(arrival).value

              message mustEqual ie044
              messageId mustEqual MessageId.fromMessageIdValue(4).value
          }
      }

    }

    "must return IE044 when unloading rejection exists" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie043Gen.msgCorrId(2), ie044Gen.msgCorrId(3), ie058Gen.msgCorrId(4)) {
        case (ie007, ie043, ie044, ie058) =>
          val messages = NonEmptyList.of(ie007, ie043, ie044, ie058)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingRemarksR(arrival).value

              message mustEqual ie044
              messageId mustEqual MessageId.fromMessageIdValue(3).value
          }
      }

    }

    "must return last IE044 when multiple unloading rejections exist" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie043Gen.msgCorrId(2), ie044Gen.msgCorrId(3), ie058Gen.msgCorrId(4), ie044Gen.msgCorrId(5), ie058Gen.msgCorrId(6)) {
        case (ie007, ie043, ie044Old, ie058Old, ie044, ie058) =>
          val messages = NonEmptyList.of(ie007, ie043, ie044Old, ie058Old, ie044, ie058)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingRemarksR(arrival).value

              message mustEqual ie044
              messageId mustEqual MessageId.fromMessageIdValue(5).value
          }
      }

    }

  }

  "unloadingRemarksRejectionsR" - {

    "must return None when there are none in the movement" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen) {
        ie007 =>
          forAll(arrivalMovement(NonEmptyList.one(ie007))) {
            arrival =>
              service.unloadingRemarksRejectionsR(arrival) must not be (defined)

          }
      }
    }

    "must return IE058 when there is only one" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen, ie043Gen, ie044Gen, ie058Gen) {
        (ie007, ie043, ie044, ie058) =>
          val messages = NonEmptyList.of(ie007, ie043, ie044, ie058)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingRemarksRejectionsR(arrival).value

              message mustEqual ie058
              messageId mustEqual MessageId.fromMessageIdValue(4).value
          }
      }

    }

    "must return last IE058 when multiple unloading remarks rejections messages exist" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie043Gen.msgCorrId(2), ie044Gen.msgCorrId(3), ie058Gen.msgCorrId(4), ie058Gen.msgCorrId(5)) {
        case (ie007, ie043, ie044, ie058Old, ie058) =>
          val messages = NonEmptyList.of(ie007, ie043, ie044, ie058Old, ie058)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val (message, messageId) = service.unloadingRemarksRejectionsR(arrival).value

              message mustEqual ie058
              messageId mustEqual MessageId.fromMessageIdValue(5).value
          }
      }

    }

  }


  //TODO: We need to only return endpoints for a valid action users can complete
  "arrivalMessagesSummary" - {

    "must return the initial IE007 and no IE008 when there only and arrival movement" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen) {
        ie007 =>
          forAll(arrivalMovement(NonEmptyList.one(ie007))) {
            arrival =>
              service.arrivalMessagesSummary(arrival) mustEqual MessagesSummary(arrival, MessageId.fromMessageIdValue(1).value, None)

          }
      }
    }

    "must the latest IE008 when there is only an IE007 and a IE008" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen, ie008Gen) {
        (ie007, ie008) =>
          val messages = NonEmptyList.of(ie007, ie008)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val expectedMessageSummary = MessagesSummary(arrival, MessageId.fromMessageIdValue(1).value, MessageId.fromMessageIdValue(2))

              service.arrivalMessagesSummary(arrival) mustEqual expectedMessageSummary
          }
      }

    }

    "must return None when there has been an rejected arrival and correction arrival" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie008Gen.msgCorrId(1), ie007Gen.msgCorrId(2)) {
        case (ie007Old, ie008Old, ie007) =>
          val messages = NonEmptyList.of(ie007Old, ie008Old, ie007)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val expectedMessageSummary = MessagesSummary(arrival, MessageId.fromMessageIdValue(3).value, None)

              service.arrivalMessagesSummary(arrival) mustEqual expectedMessageSummary
          }
      }

    }

    "must return IE008 when all IE007 have been rejected" in {
      val service = new ArrivalMessageSummaryService

      forAll(ie007Gen.submitted.msgCorrId(1), ie008Gen.msgCorrId(1), ie007Gen.msgCorrId(2), ie008Gen.msgCorrId(2)) {
        case (ie007Old, ie008Old, ie007, ie008) =>
          val messages = NonEmptyList.of(ie007Old, ie008Old, ie007, ie008)

          forAll(arrivalMovement(messages)) {
            arrival =>
              val expectedMessageSummary = MessagesSummary(arrival, MessageId.fromMessageIdValue(3).value, MessageId.fromMessageIdValue(4))

              service.arrivalMessagesSummary(arrival) mustEqual expectedMessageSummary
          }
      }

    }

  }
}

object ArrivalMessageSummaryServiceSpec {

  object MovementMessagesHelpers {

    implicit class SubmittedOps(movementMessageWithStatus: Gen[MovementMessageWithStatus]) {
      def submitted: Gen[MovementMessageWithStatus] = movementMessageWithStatus.map(_.copy(status = SubmissionSucceeded))
    }

    implicit class MessageCorrelationIdOps(movementMessageWithStatus: Gen[MovementMessageWithStatus]) {
      def msgCorrId(value: Int): Gen[MovementMessage] = movementMessageWithStatus.map(_.copy(messageCorrelationId = value))
    }

    implicit class MessageCorrelationIdOps2(movementMessageWithStatus: Gen[MovementMessageWithoutStatus]) {
      def msgCorrId(value: Int): Gen[MovementMessage] = movementMessageWithStatus.map(_.copy(messageCorrelationId = value))
    }

  }

}
