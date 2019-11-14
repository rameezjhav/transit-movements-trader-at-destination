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

package services

import com.google.inject.Inject
import models.messages.ArrivalNotification
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.WriteConcern
import reactivemongo.api.commands.{FindAndModifyCommand, WriteResult}
import reactivemongo.api.commands.FindAndModifyCommand
import reactivemongo.bson.{BSONDocument, BSONElement}
import repositories.CollectionNames
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ArrivalNotificationService @Inject()(mongo: ReactiveMongoApi) {

  private val collectionName = CollectionNames.ArrivalNotificationCollection

  private def collection: Future[JSONCollection] = {
    mongo.database.map(_.collection[JSONCollection](collectionName))
  }

  def persistToMongo(arrivalNotification: ArrivalNotification): Future[WriteResult] = {

    val doc: JsObject = Json.toJson(arrivalNotification).as[JsObject]

    collection.flatMap {
      _.insert(false)
        .one(doc)
    }
  }

  def deleteFromMongo(mrn: String) = {

    val selector: JsObject = Json.obj("movementReferenceNumber" -> "mrn")

    collection.flatMap {
      _.findAndRemove(
        selector = selector,
        sort = None,
        fields = None,
        writeConcern = WriteConcern.Default,
        maxTime = None,
        collation = None,
        arrayFilters = Seq.empty
      )
    }
  }

}
