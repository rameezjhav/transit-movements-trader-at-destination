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

package helpers

import models.request.LanguageCodeEnglish
import models.request.NormalProcedureFlag
import models.request.SimplifiedProcedureFlag
import org.scalatest.FreeSpec
import org.scalatest.MustMatchers
import org.scalatest.OptionValues

import scala.xml.NodeSeq
import scala.xml.Utility.trim

class XmlBuilderHelperSpec extends FreeSpec with MustMatchers with OptionValues {

  private val xmlBuilderHelper = new XmlBuilderHelper
  private val elementTag       = "testElement"

  "XmlBuilderService" - {

    "buildAndEncodeElem" - {

      "must build and encode String element" in {

        val elementText = "testString"

        val result         = xmlBuilderHelper.buildAndEncodeElem(elementText, elementTag)
        val expectedResult = <testElement>{"testString"}</testElement>

        result mustBe expectedResult
      }

      "must convert and build boolean element with value 1 when true" in {

        val result         = xmlBuilderHelper.buildAndEncodeElem(true, elementTag)
        val expectedResult = <testElement>{"1"}</testElement>

        result mustBe expectedResult
      }

      "must convert and build boolean element with value 0 when false" in {

        val result         = xmlBuilderHelper.buildAndEncodeElem(false, elementTag)
        val expectedResult = <testElement>{"0"}</testElement>

        result mustBe expectedResult
      }

      "must extract and build element with language code" in {

        val result         = xmlBuilderHelper.buildAndEncodeElem(LanguageCodeEnglish, elementTag)
        val expectedResult = <testElement>{"EN"}</testElement>

        result mustBe expectedResult
      }

      "must extract and build element with value 0 with normal procedure flag" in {

        val result         = xmlBuilderHelper.buildAndEncodeElem(NormalProcedureFlag, elementTag)
        val expectedResult = <testElement>{"0"}</testElement>

        result mustBe expectedResult
      }

      "must extract and build element with value 1 with simplified procedure flag" in {

        val result         = xmlBuilderHelper.buildAndEncodeElem(SimplifiedProcedureFlag, elementTag)
        val expectedResult = <testElement>{"1"}</testElement>

        result mustBe expectedResult
      }

    }

    "buildOptionalElem" - {

      "must return an element when given some value" in {

        val someResult = Some("result")

        val result         = xmlBuilderHelper.buildOptionalElem(someResult, elementTag)
        val expectedResult = <testElement>{"result"}</testElement>

        result mustBe expectedResult
      }

      "must return an empty element when given none" in {

        val result = xmlBuilderHelper.buildOptionalElem(None, elementTag)

        result mustBe NodeSeq.Empty
      }
    }

    "addChildrenToRoot" - {

      "must return a parent node with children" in {

        val rootNode     = <parentTag></parentTag>
        val childNodeSeq = <childTag1></childTag1><childTag2></childTag2>

        val result = xmlBuilderHelper.addChildrenToRoot(rootNode, childNodeSeq)
        val expectedResult = {
          <parentTag>
            <childTag1></childTag1>
            <childTag2></childTag2>
          </parentTag>
        }

        trim(result) mustBe trim(expectedResult)

      }
    }
  }

}