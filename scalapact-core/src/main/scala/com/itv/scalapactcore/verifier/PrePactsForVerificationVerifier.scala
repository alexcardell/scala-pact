package com.itv.scalapactcore.verifier

import com.itv.scalapact.shared.ColourOutput.ColouredString
import com.itv.scalapact.shared.typeclasses.{IPactReader, IScalaPactHttpClient}
import com.itv.scalapact.shared.{PactLogger, PrePactsForVerificationSettings, ScalaPactSettings}
import com.itv.scalapactcore.common.PactBrokerClient

import scala.util.Left

private[verifier] class PrePactsForVerificationVerifier(
  brokerClient: PactBrokerClient,
  localVerifierClient: IScalaPactHttpClient)(
  implicit pactReader: IPactReader) {

  def verify(pactVerifySettings: PrePactsForVerificationSettings, scalaPactSettings: ScalaPactSettings): Boolean = {

    val pacts = brokerClient.fetchPactsOldWorld(pactVerifySettings)

    PactLogger.message(
      s"Verifying against '${scalaPactSettings.giveHost}' on port '${scalaPactSettings.givePort}' with a timeout of ${scalaPactSettings.giveClientTimeout.toSeconds.toString} second(s).".white.bold
    )

    val startTime = System.currentTimeMillis()

    val pactVerifyResults = pacts.map(VerificationSteps.runVerificationAgainst(localVerifierClient, scalaPactSettings, pactVerifySettings.providerStates))

    val endTime      = System.currentTimeMillis()
    val testCount    = pactVerifyResults.flatMap(_.results).length
    val failureCount = pactVerifyResults.flatMap(_.results).count(_.result.isLeft)

    VerificationSteps.writeToJUnit(pactVerifyResults, startTime, endTime, testCount, failureCount)

    pactVerifyResults.foreach { result =>
      PactLogger.message(
        ("Results for pact between " + result.pact.consumer.name + " and " + result.pact.provider.name).white.bold
      )
      result.results.foreach { res =>
        res.result match {
          case Right(_) =>
            PactLogger.message((" - [  OK  ] " + res.context).green)

          case Left(l) =>
            PactLogger.error((" - [FAILED] " + res.context + "\n" + l).red)
        }
      }
    }

    VerificationSteps.logVerificationResults(startTime, endTime, testCount - failureCount, failureCount, 0)

    scalaPactSettings.publishResultsEnabled.foreach { publishData =>
      brokerClient.publishVerificationResults(
        pactVerifyResults,
        publishData,
        pactVerifySettings.pactBrokerAuthorization,
        pactVerifySettings.pactBrokerClientTimeout,
        pactVerifySettings.sslContextName
      )
    }

    testCount > 0 && failureCount == 0
  }
}
