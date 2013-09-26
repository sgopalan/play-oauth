package fr.njin.playoauth.as.endpoints

import play.api.data.Forms._
import play.api.data.{FormError, Form}
import fr.njin.playoauth.common.OAuth
import Constraints._
import play.api.data.format.Formatter
import fr.njin.playoauth.common.request._
import fr.njin.playoauth.common.request.PasswordTokenRequest
import fr.njin.playoauth.common.request.AuthzRequest
import play.api.data.FormError
import fr.njin.playoauth.common.request.AuthorizationCodeTokenRequest

/**
 * User: bathily
 * Date: 17/09/13
 */


object Requests {

  val scopeFormatter: Formatter[Seq[String]] = new Formatter[Seq[String]] {
    def unbind(key: String, value: Seq[String]): Map[String, String] = Map((key, value.mkString(" ")))
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Seq[String]] =
      data.get(key).fold[Either[Seq[FormError], Seq[String]]](Left(Seq(FormError(key, "error.required", Nil))))(v => Right(v.split(" ").toSeq))
  }

  val authorizeRequestForm = Form (
    mapping(
      OAuth.OauthResponseType -> nonEmptyText,
      OAuth.OauthClientId -> nonEmptyText,
      OAuth.OauthRedirectUri -> optional(text.verifying(uri)),
      OAuth.OauthScope -> optional(of[Seq[String]](scopeFormatter)),
      OAuth.OauthState -> optional(text)
    )(AuthzRequest.apply)(AuthzRequest.unapply)
  )

  val authorizationCodeTokenRequestForm = Form {
    mapping(
      OAuth.OauthCode -> nonEmptyText,
      OAuth.OauthClientId -> nonEmptyText,
      OAuth.OauthRedirectUri -> optional(text.verifying(uri))
    )(AuthorizationCodeTokenRequest.apply)(AuthorizationCodeTokenRequest.unapply)
  }

  val passwordTokenRequestForm = Form {
    mapping(
      OAuth.OauthUsername -> nonEmptyText,
      OAuth.OauthPassword -> nonEmptyText,
      OAuth.OauthScope -> optional(of[Seq[String]](scopeFormatter))
    )(PasswordTokenRequest.apply)(PasswordTokenRequest.unapply)
  }

  val clientCredentialsTokenRequestForm = Form {
    mapping(
      OAuth.OauthScope -> optional(of[Seq[String]](scopeFormatter))
    )(ClientCredentialsTokenRequest.apply)(ClientCredentialsTokenRequest.unapply)
  }

  val refreshTokenRequestForm = Form {
    mapping(
      OAuth.OauthRefreshToken -> nonEmptyText,
      OAuth.OauthScope -> optional(of[Seq[String]](scopeFormatter))
    )(RefreshTokenRequest.apply)(RefreshTokenRequest.unapply)
  }

  val tokenForms = Map(
    OAuth.GrantType.AuthorizationCode -> authorizationCodeTokenRequestForm,
    OAuth.GrantType.ClientCredentials -> clientCredentialsTokenRequestForm,
    OAuth.GrantType.Password -> passwordTokenRequestForm,
    OAuth.GrantType.RefreshToken -> refreshTokenRequestForm
  )
  

}