package domain.oauth2

import fr.njin.playoauth.common.domain._
import scala.concurrent.{Future, ExecutionContext}
import scalikejdbc.async.AsyncDBSession
import models._
import scala.Some

class AppRepository(implicit val session: AsyncDBSession, val ec: ExecutionContext) extends OauthClientRepository[App] {

  def find(id: String): Future[Option[App]] = App.find(id)

}

class AuthTokenRepository(implicit val session: AsyncDBSession, val ec: ExecutionContext)
  extends OauthTokenRepository[AuthToken, User, App] {

  def find(value: String): Future[Option[AuthToken]] = AuthToken.findForValue(value)

  def findForRefreshToken(value: String): Future[Option[AuthToken]] = AuthToken.findForRefreshToken(value)

  def revoke(value: String): Future[Option[AuthToken]] = {
    (for {
      t <- AuthToken.findForValue(value)
      revoked <- t.get.revoke if t.isDefined
    } yield Some(revoked)).recover{ case _ => None }
  }
}

class AuthCodeRepository(implicit session:AsyncDBSession, ec: ExecutionContext) extends OauthCodeRepository[AuthCode, User, App] {

  def find(value: String): Future[Option[AuthCode]] = AuthCode.find(value, unRevokedOnly = false)

  def revoke(value: String): Future[Option[AuthCode]] = {
    (for {
      t <- AuthCode.find(value)
      revoked <- t.get.revoke if t.isDefined
    } yield Some(revoked)).recover{ case _ => None }
  }
}

class InMemoryOauthScopeRepository[T <: OauthScope](var scopes:Map[String, T] = Map.empty[String, T]) extends OauthScopeRepository[T] {

  def find(id: String*): Future[Map[String, T]] = Future.successful(scopes)

}

