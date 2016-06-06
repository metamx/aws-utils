/**
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
organization := "com.metamx"

name := "aws-utils"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.6", "2.11.7")

lazy val root = project.in(file("."))

net.virtualvoid.sbt.graph.Plugin.graphSettings

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

homepage := Some(url("https://github.com/metamx/aws-utils"))

publishMavenStyle := true

publishTo := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/")

pomIncludeRepository := { _ => false }

pomExtra := (
  <scm>
    <url>https://github.com/metamx/aws-utils.git</url>
    <connection>scm:git:git@github.com:metamx/aws-utils.git</connection>
  </scm>
)

parallelExecution in Test := false

testOptions += Tests.Argument(TestFrameworks.JUnit, "-Duser.timezone=UTC")

releaseSettings

ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value

val awsVersion = "1.10.12"

libraryDependencies ++= Seq(
  "com.metamx" %% "loglady" % "1.1.0-mmx" force()
)

libraryDependencies ++= Seq(
  "com.metamx" %% "scala-util" % "1.11.9" force()
)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-autoscaling" % awsVersion force(),
  "com.amazonaws" % "aws-java-sdk-ec2" % awsVersion force()
)

//
// Test stuff.
//

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.2" % "test" force(),
  "junit" % "junit" % "4.11" % "test" force(),
  "org.mockito" % "mockito-core" % "1.9.5" % "test" force()
)

libraryDependencies <++= scalaVersion {
  case x if x.startsWith("2.10.") => Seq(
    "com.simple" % "simplespec_2.10.2" % "0.8.4" % "test" exclude("org.mockito", "mockito-all") force()
  )
  case _ => Seq(
    "com.simple" %% "simplespec" % "0.8.4" % "test" exclude("org.mockito", "mockito-all") force()
  )
}

libraryDependencies ++= Seq(
  "com.novocode" % "junit-interface" % "0.11-RC1" % "test" exclude("junit", "junit") force()
)
