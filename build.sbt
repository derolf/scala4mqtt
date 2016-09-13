name := "scala4mqtt"

organization := "com.dero"

version := "1.0"

scalaVersion := "2.11.7"

updateOptions := updateOptions.value.withCachedResolution(true)

resolvers += "MQTT Repository" at "https://repo.eclipse.org/content/repositories/paho-releases/"

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4"

libraryDependencies += "org.quartz-scheduler" % "quartz" % "2.1.3"

libraryDependencies += "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.0.2"

libraryDependencies += "com.typesafe.scala-logging" % "scala-logging-slf4j_2.11" % "2.1.2"
