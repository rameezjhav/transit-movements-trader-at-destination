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

package models

import models.XSDFile.ArrivalRejectedXSD
import models.XSDFile.GoodsReleasedXSD
import models.XSDFile.UnloadingPermissionXSD
import models.XSDFile.UnloadingRemarksRejectedXSD

sealed trait MessageResponse {
  val messageReceived: MessageReceivedEvent
  val messageType: MessageType
  val xsdFile: XSDFile
}

case object GoodsReleasedResponse extends MessageResponse {
  override val messageReceived          = MessageReceivedEvent.GoodsReleased
  override val messageType: MessageType = MessageType.GoodsReleased
  override val xsdFile: XSDFile         = GoodsReleasedXSD
}

case object ArrivalRejectedResponse extends MessageResponse {
  override val messageReceived          = MessageReceivedEvent.ArrivalRejected
  override val messageType: MessageType = MessageType.ArrivalRejection
  override val xsdFile: XSDFile         = ArrivalRejectedXSD
}

case object UnloadingPermissionResponse extends MessageResponse {
  override val messageReceived          = MessageReceivedEvent.UnloadingPermission
  override val messageType: MessageType = MessageType.UnloadingPermission
  override val xsdFile: XSDFile         = UnloadingPermissionXSD
}

case object UnloadingRemarksRejectedResponse extends MessageResponse {
  override val messageReceived          = MessageReceivedEvent.UnloadingRemarksRejected
  override val messageType: MessageType = MessageType.UnloadingRemarksRejection
  override val xsdFile: XSDFile         = UnloadingRemarksRejectedXSD
}
