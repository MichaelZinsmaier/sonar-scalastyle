/*
 * Sonar Scalastyle Plugin
 * Copyright (C) 2014 All contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.ncredinburgh.sonar.scalastyle

import java.io.File
import java.nio.charset.StandardCharsets

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList

import org.junit.runner.RunWith
import org.mockito.Matchers.any
import org.mockito.Matchers.anyListOf
import org.mockito.Matchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalastyle.FileSpec
import org.scalastyle.RealFileSpec
import org.scalastyle.StyleError
import org.scalastyle.WarningLevel
import org.scalastyle.file.FileLengthChecker
import org.scalastyle.scalariform.ForBraceChecker
import org.scalastyle.scalariform.IfBraceChecker
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.PrivateMethodTester
import org.scalatest.junit.JUnitRunner
import org.scalatest.mockito.MockitoSugar
import org.sonar.api.batch.fs.internal.DefaultInputFile
import org.sonar.api.batch.rule.ActiveRule
import org.sonar.api.batch.rule.ActiveRules
import org.sonar.api.batch.sensor.internal.SensorContextTester
import org.sonar.api.rule.RuleKey
import java.nio.file.Path
import org.sonar.api.batch.fs.internal.DefaultIndexedFile
import java.nio.file.FileSystems
import org.sonar.api.batch.fs.internal.TestInputFileBuilder

@RunWith(classOf[JUnitRunner])
class ScalastyleSensorSpec extends FlatSpec with Matchers with MockitoSugar with PrivateMethodTester {

  trait Fixture {
    val runner = mock[ScalastyleRunner]

    val testee = new ScalastyleSensor(runner)
    
    val context = spy(SensorContextTester.create(new File("src/test/resources")))
    val modulePath = FileSystems.getDefault().getPath("src/test/resources");
    
    // files   
    val offset = Array[Int](1, 5, 10, 16, 20, 25, 39, 50)
    
    def fileBuilder(file: String) =
      new TestInputFileBuilder("testProject", file)
        .setLanguage("scala")
        .setLines(8)
        .setOriginalLineOffsets(offset)        
           
    context.fileSystem()
      .add(fileBuilder("ScalaFile1.scala").build())
      .add(fileBuilder("ScalaFile2.scala").build())
      .setEncoding(StandardCharsets.UTF_8)
    
    val scalaFiles = context.fileSystem().inputFiles().map { inputFile => inputFile.file() }.toList  
      
    // rules
    val activeRule = mock[ActiveRule]
    when(activeRule.ruleKey).thenReturn(mock[RuleKey])

    val activeRules = mock[ActiveRules]
    when(activeRules.findByInternalKey(any[String], any[String])).thenReturn(activeRule)

    context.setActiveRules(activeRules)
  }

  it should "analyse all scala source files in project" in new Fixture {
    when(runner.run(anyString, anyListOf(classOf[File]))).thenReturn(List())
    testee.execute(context)
    
    verify(runner).run(StandardCharsets.UTF_8.name(), scalaFiles)
  }

  it should "not create SonarQube issues when there isn't any scalastyle errors" in new Fixture {
    when(runner.run(anyString, anyListOf(classOf[File]))).thenReturn(List())
    testee.execute(context)

    verify(context, never).newIssue()
  }

  it should "report a scalastyle error as a SonarQube issue" in new Fixture {
    val error = new StyleError[FileSpec](
      new RealFileSpec("ScalaFile1.scala", None),
      classOf[ForBraceChecker],
      "org.scalastyle.scalariform.ForBraceChecker",
      WarningLevel,
      List(),
      Some(7))

    when(runner.run(anyString, anyListOf(classOf[File]))).thenReturn(List(error))
    testee.execute(context)

    verify(context, times(1)).newIssue()
  }

  it should "report scalastyle errors as SonarQube issues" in new Fixture {
    val error1 = new StyleError[FileSpec](new RealFileSpec("ScalaFile1.scala", None), classOf[FileLengthChecker],
      "org.scalastyle.file.FileLengthChecker", WarningLevel, List(), Some(6))
    val error2 = new StyleError[FileSpec](new RealFileSpec("ScalaFile2.scala", None), classOf[IfBraceChecker],
      "org.scalastyle.scalariform.IfBraceChecker", WarningLevel, List(), Some(4))

    when(runner.run(anyString, anyListOf(classOf[File]))).thenReturn(List(error1, error2))
    testee.execute(context)

    verify(context, times(2)).newIssue()
  }

}
