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

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList

import org.scalastyle.FileSpec
import org.scalastyle.Message
import org.scalastyle.StyleError
import org.scalastyle.StyleException
import org.sonar.api.batch.fs.FileSystem
import org.sonar.api.batch.fs.InputFile
import org.sonar.api.batch.rule.ActiveRule
import org.sonar.api.batch.sensor.Sensor
import org.sonar.api.batch.sensor.SensorContext
import org.sonar.api.batch.sensor.SensorDescriptor
import org.sonar.api.batch.sensor.issue.NewIssueLocation
import org.sonar.api.batch.sensor.issue.internal.DefaultIssueLocation
import org.sonar.api.profiles.RulesProfile
import org.sonar.api.utils.log.Loggers


/**
 * Main sensor for return Scalastyle issues to Sonar.
 */
class ScalastyleSensor(runner: ScalastyleRunner) extends Sensor {

  def this(rulesProfile: RulesProfile, fileSystem: FileSystem) = this(new ScalastyleRunner(rulesProfile))


  private val log = Loggers.get(classOf[ScalastyleSensor])

  private def scalaFilesPredicate(context: SensorContext) = {
    val predicates = context.fileSystem.predicates
    predicates.and(predicates.hasType(InputFile.Type.MAIN), predicates.hasLanguage(Constants.ScalaKey))
  }


  override def execute(context: SensorContext): Unit = {
    val files = context.fileSystem.files(scalaFilesPredicate(context))
    val encoding = context.fileSystem.encoding.name
    val messages = runner.run(encoding, files.toList)

    messages foreach (processMessage(_, context))
  }
  
  override def describe(descriptor: SensorDescriptor): Unit =  {
    descriptor
      .name(Constants.ProfileName)
      .onlyOnLanguage(Constants.ScalaKey)
      .createIssuesForRuleRepositories(Constants.RepositoryKey)
  }  

  private def processMessage(message: Message[FileSpec], context: SensorContext): Unit = message match {
    case error: StyleError[FileSpec] => processError(error, context)
    case exception: StyleException[FileSpec] => processException(exception)
    case _ => Unit
  }

  private def processError(error: StyleError[FileSpec], context: SensorContext): Unit = {
    log.debug("Error message for rule " + error.clazz.getName)

    val rule = findSonarRuleForError(error, context)
    val location = findLocation(error, context)
    
    context.newIssue()
      .forRule(rule.ruleKey())
      .at(location)
      .save()

    log.debug("Matched to sonar rule " + rule)
  }

  private def findLocation(error: StyleError[FileSpec], context: SensorContext): NewIssueLocation = {
    val inputFile = context.fileSystem.inputFile(context.fileSystem.predicates.hasPath(error.fileSpec.name))
    val line = inputFile.selectLine(error.lineNumber.get)
        
    new DefaultIssueLocation().on(inputFile).at(line)
  }

  private def findSonarRuleForError(error: StyleError[FileSpec], context: SensorContext): ActiveRule = {
    val key = error.key // == scalastyle ConfigurationChecker.customId 
    log.debug("Looking for sonar rule for " + key)
    context.activeRules().findByInternalKey(Constants.RepositoryKey, key)
  }

  private def processException(exception: StyleException[FileSpec]): Unit = {
    log.error("Got exception message from Scalastyle. " +
      "Check you have valid parameters configured for all rules. Exception message was: " + exception.message)
  }
}
