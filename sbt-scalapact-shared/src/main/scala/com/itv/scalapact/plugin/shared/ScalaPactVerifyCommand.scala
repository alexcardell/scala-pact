package com.itv.scalapact.plugin.shared

import com.itv.scalapact.shared.ColourOutput._
import com.itv.scalapact.shared._
import com.itv.scalapactcore.common.LocalPactFileLoader
import com.itv.scalapact.shared.typeclasses.IPactReader
import com.itv.scalapact.shared.ProviderStateResult.SetupProviderState
import com.itv.scalapactcore.verifier.Verifier

object ScalaPactVerifyCommand {

  def doPactVerify[F[_]](verifier: Verifier[F])(
      scalaPactSettings: ScalaPactSettings,
      providerStates: Seq[(String, SetupProviderState)],
      providerStateMatcher: PartialFunction[String, ProviderStateResult],
      pactBrokerAddress: String,
      projectVersion: String,
      providerName: String,
      consumerNames: Seq[String],
      versionedConsumerNames: Seq[(String, String)],
      taggedConsumerNames: Seq[(String, Seq[String])],
      consumerVersionSelectors: Seq[(String, Option[String], Option[String], Option[Boolean])],
      providerVersionTags: Seq[String],
      pactBrokerAuthorization: Option[PactBrokerAuthorization]
  )(implicit pactReader: IPactReader): Unit = {
    PactLogger.message("*************************************".white.bold)
    PactLogger.message("** ScalaPact: Running Verifier     **".white.bold)
    PactLogger.message("*************************************".white.bold)

    val combinedPactStates = combineProviderStatesIntoTotalFunction(providerStates, providerStateMatcher)

    val pactVerifySettings = PactVerifySettings(
      combinedPactStates,
      pactBrokerAddress,
      projectVersion,
      providerName,
      consumerNames.toList,
      taggedConsumerNames = taggedConsumerNames.toList
        .map(t => TaggedConsumer(t._1, t._2.toList)),
      versionedConsumerNames = versionedConsumerNames.toList
        .map(t => VersionedConsumer(t._1, t._2)),
      consumerVersionSelectors.toList.map(v => ConsumerVersionSelector(v._1, v._2, v._3, v._4)),
      providerVersionTags.toList,
      pactBrokerAuthorization
    )

    val stringToSettingsToPacts = LocalPactFileLoader.loadPactFiles(pactReader)(true)
    val successfullyVerified = verifier.verify(stringToSettingsToPacts, pactVerifySettings)(scalaPactSettings)

    if (successfullyVerified) sys.exit(0) else sys.exit(1)

  }

  def combineProviderStatesIntoTotalFunction(
      directPactStates: Seq[(String, SetupProviderState)],
      patternMatchedStates: PartialFunction[String, ProviderStateResult]
  ): SetupProviderState = {
    val l = directPactStates
      .map { case (state, config) =>
        { case s: String if s == state => config(state) }: PartialFunction[String, ProviderStateResult]
      }

    l match {
      case Nil =>
        patternMatchedStates orElse { case _: String => ProviderStateResult() }

      case x :: xs =>
        xs.foldLeft(x)(_ orElse _) orElse patternMatchedStates orElse { case _: String => ProviderStateResult() }

    }
  }
}
