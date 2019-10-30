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

import models.messages.request.ArrivalNotificationRequest

import scala.xml.XML._
import scala.xml.{Elem, Node, NodeSeq}

class ConvertToXml {

  def buildXml(arrivalNotificationRequest: ArrivalNotificationRequest): Node = {

    val rootNode: Node = buildStartRoot(arrivalNotificationRequest.rootKey, arrivalNotificationRequest.nameSpace)
    val childNodes: NodeSeq = buildChildNodes(arrivalNotificationRequest)
    val createXml: Node = addChildrenToRoot(childNodes, rootNode)

    createXml
  }

  private def buildStartRoot[A](key: String, nameSpace: Map[String, String]): Node = {

    val concatNameSpace: (String, (String, String)) => String = {
      (accumulatedStrings, keyValue) => s"$accumulatedStrings ${keyValue._1}='${keyValue._2}'"
    }

    val rootWithNameSpace = nameSpace.foldLeft("")(concatNameSpace)

    loadString(s"<$key $rootWithNameSpace></$key>")
  }

  private def addChildrenToRoot(childNodes: NodeSeq, root: Node): Node = {
    Elem(
      root.prefix,
      root.label,
      root.attributes,
      root.scope,
      root.child.isEmpty,
      root.child ++ childNodes: _*
    )
  }

  private def buildOptionalElem[A](value: Option[A], elementTag: String): NodeSeq = value match {
    case Some(result) => loadString(s"<$elementTag>$result</$elementTag>")
    case _ => NodeSeq.Empty
  }

  private def buildChildNodes(arrivalNotificationXml: ArrivalNotificationRequest): NodeSeq = {
        <SynIdeMES1>{arrivalNotificationXml.meta.syntaxIdentifier}</SynIdeMES1>
        <SynVerNumMES2>{arrivalNotificationXml.meta.syntaxVersionNumber}</SynVerNumMES2>
        <MesSenMES3>{arrivalNotificationXml.meta.messageSender.toString}</MesSenMES3> ++
        buildOptionalElem(arrivalNotificationXml.meta.senderIdentificationCodeQualifier, "SenIdeCodQuaMES4") ++
        buildOptionalElem(arrivalNotificationXml.meta.recipientIdentificationCodeQualifier, "RecIdeCodQuaMES7") ++
        <MesRecMES6>{arrivalNotificationXml.meta.messageRecipient}</MesRecMES6> ++
        <DatOfPreMES9>{arrivalNotificationXml.meta.dateOfPreparation}</DatOfPreMES9> ++
        <TimOfPreMES10>{arrivalNotificationXml.meta.timeOfPreparation}</TimOfPreMES10> ++
        <IntConRefMES11>{arrivalNotificationXml.meta.interchangeControlReference.toString}</IntConRefMES11> ++
        buildOptionalElem(arrivalNotificationXml.meta.recipientsReferencePassword, "RecRefMES12") ++
        buildOptionalElem(arrivalNotificationXml.meta.recipientsReferencePasswordQualifier, "RecRefQuaMES13") ++
        <AppRefMES14>{arrivalNotificationXml.meta.applicationReference}</AppRefMES14> ++
        buildOptionalElem(arrivalNotificationXml.meta.priority, "PriMES15") ++
        buildOptionalElem(arrivalNotificationXml.meta.acknowledgementRequest, "AckReqMES16") ++
        buildOptionalElem(arrivalNotificationXml.meta.communicationsAgreementId, "ComAgrIdMES17") ++
        <MesIdeMES18>{arrivalNotificationXml.meta.testIndicator}</MesIdeMES18> ++
        <MesIdeMES19>{arrivalNotificationXml.meta.messageIndication}</MesIdeMES19> ++
        <MesTypMES20>{arrivalNotificationXml.messageCode}</MesTypMES20> ++
        buildOptionalElem(arrivalNotificationXml.meta.commonAccessReference, "ComAccRefMES21") ++
        buildOptionalElem(arrivalNotificationXml.meta.messageSequenceNumber, "MesSeqNumMES22") ++
        buildOptionalElem(arrivalNotificationXml.meta.firstAndLastTransfer, "FirAndLasTraMES23") ++
        <HEAHEA>
          <DocNumHEA5>{arrivalNotificationXml.header.movementReferenceNumber}</DocNumHEA5>
          {
            buildOptionalElem(arrivalNotificationXml.header.customsSubPlace, "CusSubPlaHEA66")
          }
          <ArrNotPlaHEA60>{arrivalNotificationXml.header.arrivalNotificationPlace}</ArrNotPlaHEA60>
          <ArrNotPlaHEA60LNG>{arrivalNotificationXml.header.languageCode}</ArrNotPlaHEA60LNG>
          {
            buildOptionalElem(arrivalNotificationXml.header.arrivalNotificationPlaceLNG, "ArrAgrLocCodHEA62") ++
            buildOptionalElem(arrivalNotificationXml.header.arrivalAgreedLocationOfGoods, "ArrAgrLocOfGooHEA63")
          }
          <ArrAgrLocOfGooHEA63LNG>{arrivalNotificationXml.header.languageCode}</ArrAgrLocOfGooHEA63LNG>
          {
            buildOptionalElem(arrivalNotificationXml.header.arrivalAgreedLocationOfGoodsLNG, "ArrAutLocOfGooHEA65")
          }
          <SimProFlaHEA132>{arrivalNotificationXml.header.simplifiedProcedureFlag}</SimProFlaHEA132>
          <ArrNotDatHEA141>{arrivalNotificationXml.header.arrivalNotificationDate}</ArrNotDatHEA141>
        </HEAHEA>
        <TRADESTRD>
          {
            buildOptionalElem(arrivalNotificationXml.traderDestination.name, "NamTRD7") ++
            buildOptionalElem(arrivalNotificationXml.traderDestination.streetAndNumber, "StrAndNumTRD22") ++
            buildOptionalElem(arrivalNotificationXml.traderDestination.postCode, "PosCodTRD23") ++
            buildOptionalElem(arrivalNotificationXml.traderDestination.city, "CitTRD24") ++
            buildOptionalElem(arrivalNotificationXml.traderDestination.countryCode, "CouTRD25")
          }
          <NADLNGRD>{arrivalNotificationXml.traderDestination.languageCode}</NADLNGRD>
          {
            buildOptionalElem(arrivalNotificationXml.traderDestination.eori, "TINTRD59")
          }
        </TRADESTRD>
        <CUSOFFPREOFFRES>
          <RefNumRES1>{arrivalNotificationXml.customsOfficeOfPresentation.presentationOffice}</RefNumRES1>
        </CUSOFFPREOFFRES>
  }
}
