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

import cats.data.ReaderT
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue

import scala.xml.NodeSeq

sealed trait MessageType extends IeMetadata {
  def code: String
  def rootNode: String
}

object MessageType extends Enumerable.Implicits {

  case object ArrivalNotification extends IeMetadata("IE007", "CC007A") with MessageType
  case object ArrivalRejection    extends IeMetadata("IE008", "CC008A") with MessageType
  case object GoodsReleased       extends IeMetadata("IE025", "CC025A") with MessageType
  case object UnloadingRemarks    extends IeMetadata("IE044", "CC044A") with MessageType

  val values: Seq[MessageType] = Seq(ArrivalNotification, GoodsReleased)

  val supportedMessageTypes = Seq(UnloadingRemarks, ArrivalRejection)

  val supportedMessageTypesFromNCTS = Seq(ArrivalRejection, GoodsReleased)

  def getMessageType: ReaderT[Option, NodeSeq, MessageType] =
    ReaderT[Option, NodeSeq, MessageType] {
      nodeSeq =>
        supportedMessageTypes.find(_.rootNode == nodeSeq.head.label)
    }

  implicit val enumerable: Enumerable[MessageType] =
    Enumerable(values.map(v => v.code -> v): _*)
}
