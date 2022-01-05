/*
 * Copyright 2022 HM Revenue & Customs
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

package bankaccountverification.web

import bankaccountverification.connector.PartialsConnector
import bankaccountverification.web.views.html.ErrorTemplate
import bankaccountverification.{AppConfig, AuthProviderId, Journey, JourneyRepository}
import org.bson.types.ObjectId
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc._
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider
import uk.gov.hmrc.play.partials.HtmlPartial

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class RequestWithCustomisations[A](request: Request[A],
                                   val journey: Journey,
                                   val headerBlock: Option[Html],
                                   val beforeContentBlock: Option[Html],
                                   val footerBlock: Option[Html]) extends WrappedRequest[A](request)

class ActionWithCustomisationsProvider @Inject()(val messagesApi: MessagesApi,
                                                 val authConnector: AuthConnector,
                                                 implicit val appConfig: AppConfig,
                                                 journeyRepository: JourneyRepository,
                                                 partialsConnector: PartialsConnector, bodyParser: BodyParsers.Default,
                                                 errorTemplate: ErrorTemplate)

  extends FrontendHeaderCarrierProvider with I18nSupport with AuthorisedFunctions {

  def action(journeyId: String)(implicit ec: ExecutionContext): ActionBuilder[RequestWithCustomisations, AnyContent] =
    new ActionBuilder[RequestWithCustomisations, AnyContent] with ActionRefiner[Request, RequestWithCustomisations] {

      def parser = bodyParser

      def executionContext = ec

      override protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithCustomisations[A]]] = {
        implicit val headerCarrier: HeaderCarrier = hc(request)

        authorised().retrieve(AuthProviderId.retrieval) {
          authProviderId =>

            Try(new ObjectId(journeyId)) match {
              case Success(id) =>
                journeyRepository.findById(id) flatMap {
                  case Some(journey) if journey.authProviderId.isEmpty || journey.authProviderId.get == authProviderId =>
                    getCustomisations(journey)(ec, request) map {
                      case (headerBlock, beforeContentBlock, footerBlock) =>
                        Right(new RequestWithCustomisations(request, journey, headerBlock, beforeContentBlock, footerBlock))
                    }
                  case _ => Future.successful(Left(NotFound(journeyIdError(request))))
                }

              case Failure(_) => Future.successful(Left(BadRequest))
            }
        } recoverWith { case _ => Future.successful(Left(Unauthorized(unauthorisedError(request)))) }
      }
    }

  private def getCustomisations(journey: Journey)(implicit ec: ExecutionContext,
                                                  request: Request[_]): Future[(Option[Html], Option[Html], Option[Html])] =
    for {
      header <- partialsConnector.header(journey.customisationsUrl)
      beforeContent <- partialsConnector.beforeContent(journey.customisationsUrl)
      footer <- partialsConnector.footer(journey.customisationsUrl)
    } yield {

      val headerBlock = header match {
        case HtmlPartial.Success(_, content) => Some(content)
        case _ => None
      }

      val beforeContentBlock = beforeContent match {
        case HtmlPartial.Success(_, content) => Some(content)
        case _ => None
      }

      val footerBlock = footer match {
        case HtmlPartial.Success(_, content) => Some(content)
        case _ => None
      }

      (headerBlock, beforeContentBlock, footerBlock)
    }

  def journeyIdError(implicit request: Request[_]) = {
    val messages = implicitly[Messages]
    errorTemplate(messages("error.pageTitle"), messages("error.journeyId.heading"), messages("error.journeyId.message"))
  }

  def unauthorisedError(implicit request: Request[_]) = {
    val messages = implicitly[Messages]
    errorTemplate(messages("error.pageTitle"), messages("error.unauthorised.heading"), messages("error.unauthorised.message"))
  }
}
