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

import java.io._
import java.net.URL

import javax.xml.parsers.SAXParserFactory
import javax.xml.validation.Schema
import models.MessageReceived
import models.MessageType
import models.XSDFile.GoodsReleasedXSD
import models.XSDFile.UnloadingPermissionXSD
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

import scala.util.Try
import scala.xml.Elem
import scala.xml.SAXParseException
import scala.xml.SAXParser
import scala.xml.factory.XMLLoader

class XmlValidationService {

  private val schemaLang = javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI

  private def saxParser(schema: Schema): SAXParser = {
    val saxParser: SAXParserFactory = javax.xml.parsers.SAXParserFactory.newInstance()
    saxParser.setNamespaceAware(true)
    saxParser.setSchema(schema)
    saxParser.newSAXParser()
  }

  def validate(xml: String, messageType: MessageReceived): Try[Unit] =
    Try {

      val xsdFile = messageType match {
        case MessageReceived.GoodsReleased       => GoodsReleasedXSD
        case MessageReceived.UnloadingPermission => UnloadingPermissionXSD
      }

      val url: URL = getClass.getResource(xsdFile.filePath)

      val schema: Schema = javax.xml.validation.SchemaFactory.newInstance(schemaLang).newSchema(url)

      class CustomParseHandler extends DefaultHandler {
        override def error(e: SAXParseException): Unit =
          throw new SAXParseException(e.getMessage, e.getPublicId, e.getSystemId, e.getLineNumber, e.getColumnNumber)
      }

      val xmlResponse: XMLLoader[Elem] = new scala.xml.factory.XMLLoader[scala.xml.Elem] {
        override def parser: SAXParser = saxParser(schema)
      }

      xmlResponse.parser.parse(new InputSource(new StringReader(xml)), new CustomParseHandler())
    }
}
