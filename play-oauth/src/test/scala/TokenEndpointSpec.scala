import fr.njin.playoauth.common.domain.BasicOauthClientInfo
import fr.njin.playoauth.common.OAuth
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration._
import com.github.theon.uri.Uri.parse
import Utils._

/**
 * User: bathily
 * Date: 18/09/13
 */
class TokenEndpointSpec extends Specification with NoTimeConversions {

  import Constants._

  "TokenEndPoint" should {

    import ExecutionContext.Implicits.global


    """issues an access token and optional refresh token, and constructs the response
      by adding the following parameters
      to the entity-body of the HTTP response with a 200 (OK) status code""" in new EndPointWithClients {

      Seq(
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithCode,
          OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
          OAuth.OauthCode -> AuthorizationCode
        ),
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> AnotherClientWithCode,
          OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
          OAuth.OauthCode -> AnotherAuthorizationCode,
          OAuth.OauthRedirectUri -> RedirectURI
        )
      ).map { request =>
        val r = token.apply(request)
        status(r) must equalTo(OK)
        (contentAsJson(r) \ OAuth.OauthError).asOpt[String] must beNone
        (contentAsJson(r) \ OAuth.OauthAccessToken).asOpt[String] must beSome[String]
        (contentAsJson(r) \ OAuth.OauthTokenType).asOpt[String] must beSome[String]
        Json.toJson(Await.result(tokenRepository.find((contentAsJson(r) \ OAuth.OauthAccessToken).as[String]), timeout)) must beEqualTo(contentAsJson(r))
      }

    }
  }


  """The authorization server responds with an HTTP 400 (Bad Request)
   status code (unless specified otherwise)""" >> {

    import ExecutionContext.Implicits.global

    "The request is missing a required parameter" in new EndPointWithClients {
      Seq(
        FakeRequest(),
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
          OAuth.OauthCode -> AuthorizationCode
        ),
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithCode,
          OAuth.OauthCode -> AuthorizationCode
        ),
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> ClientWithCode,
          OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode
        ),
        FakeRequest().withFormUrlEncodedBody(
          OAuth.OauthClientId -> AnotherClientWithCode,
          OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
          OAuth.OauthCode -> AnotherAuthorizationCode
        )
      ).map(request => {
        val r = token.apply(request)
        status(r) must equalTo(BAD_REQUEST)
        (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidRequest)
      })
    }

    "includes an unsupported parameter value (other than grant type)" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> "unknown_grant_type",
        OAuth.OauthCode -> AuthorizationCode
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.UnsupportedGrantType)
    }

    "repeats a parameter" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> AuthorizationCode,
        OAuth.OauthCode -> AuthorizationCode
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidRequest)
    }

    "includes multiple credentials" in new EndPointWithClients {
      failure("TODO")
    }

    "utilizes more than one mechanism for authenticating the client" in new EndPointWithClients {
      failure("TODO")
    }

    """Client authentication failed (e.g., unknown client, no
    client authentication included, or unsupported
    authentication method)""" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> "unknown_client",
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> AuthorizationCode
      ))
      status(r) must equalTo(UNAUTHORIZED)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidClient)
    }

    "The provided authorization grant is invalid" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> "unknown_code"
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidGrant)
      (contentAsJson(r) \ OAuth.OauthErrorDescription).as[String] must equalTo(OAuth.ErrorUnknownAuthorizationCode)
    }

    "The provided authorization grant is expired" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> ExpiredAuthorizationCode
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidGrant)
      (contentAsJson(r) \ OAuth.OauthErrorDescription).as[String] must equalTo(OAuth.ErrorExpiredAuthorizationCode)
    }

    "The provided authorization grant is revoked" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> RevokedAuthorizationCode
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidGrant)
      (contentAsJson(r) \ OAuth.OauthErrorDescription).as[String] must equalTo(OAuth.ErrorRevokedAuthorizationCode)
    }

    "The provided authorization grant does not match the redirection URI used in the authorization request" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> AnotherClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> AnotherAuthorizationCode,
        OAuth.OauthRedirectUri -> "http://dummy.com"
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidGrant)
      (contentAsJson(r) \ OAuth.OauthErrorDescription).as[String] must equalTo(OAuth.ErrorRedirectURINotMatch)
    }

    """The provided authorization grant was issued to another client.""" in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.AuthorizationCode,
        OAuth.OauthCode -> AnotherAuthorizationCode
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.InvalidGrant)
      (contentAsJson(r) \ OAuth.OauthErrorDescription).as[String] must equalTo(OAuth.ErrorClientNotMatch)
    }

    "The authenticated client is not authorized to use this authorization grant type." in new EndPointWithClients {
      val r = token.apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> ClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.ClientCredentials,
        OAuth.OauthCode -> AuthorizationCode
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.UnauthorizedClient)
    }

    "The authorization grant type is not supported by the authorization server." in new EndPointWithClients {
      val r = tokenWithOnlyAuthorisationCodeEndpoint.token(tokenWithOnlyAuthorisationCodeEndpoint.perform).apply(FakeRequest().withFormUrlEncodedBody(
        OAuth.OauthClientId -> AnotherClientWithCode,
        OAuth.OauthGrantType -> OAuth.GrantType.ClientCredentials,
        OAuth.OauthCode -> AnotherAuthorizationCode,
        OAuth.OauthRedirectUri -> RedirectURI
      ))
      status(r) must equalTo(BAD_REQUEST)
      (contentAsJson(r) \ OAuth.OauthError).as[String] must equalTo(OAuth.ErrorCode.UnsupportedGrantType)
      (contentAsJson(r) \ OAuth.OauthErrorDescription).as[String] must equalTo(OAuth.ErrorUnsupportedGrantType)
    }

    /*
    "The requested scope is invalid, unknown, malformed, or exceeds the scope granted by the resource owner." in new EndPointWithClients {
      failure("TODO")
    }
    */
  }

}


