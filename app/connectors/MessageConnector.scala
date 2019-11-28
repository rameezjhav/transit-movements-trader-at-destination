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

package connectors

import java.util.UUID

import com.google.inject.Inject
import config.AppConfig
import models.messages.MessageCode
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class MessageConnectorImpl @Inject()(config: AppConfig, http: HttpClient) extends MessageConnector {

  def post(xml: String, messageCode: MessageCode)
          (implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {

    val url = config.eisUrl
    val messageSender ="mdtp-userseori"

    val customHeaders: Seq[(String, String)] = Seq(
      "Content-Type" -> "application/xml",
      "X-Message-Type" -> messageCode.code,
      "X-Correlation-ID" -> {
        headerCarrier.sessionId.map(_.value)
          .getOrElse(UUID.randomUUID().toString)
      },
      "X-Message-Sender" -> messageSender,
      "X-Forwarded-Host" -> "mdtp"
    )

    http.POSTString(url, xml, customHeaders)
  }
}

trait MessageConnector {
  def post(xml: String, messageCode: MessageCode)
          (implicit headerCarrier: HeaderCarrier): Future[HttpResponse]
}
