package it.gov.daf.common.authentication

import java.util
import java.util.Date

import com.nimbusds.jwt.JWTClaimsSet
import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator
import org.pac4j.jwt.profile.JwtGenerator
import org.pac4j.play.PlayWebContext
import org.pac4j.play.store.PlaySessionStore
import play.api.Configuration
import play.api.mvc.{RequestHeader, Result, Results}

import scala.collection.convert.decorateAsScala._
import scala.collection.mutable

object Authentication extends Results {

  var configuration: Option[Configuration] = None
  var playSessionStore: Option[PlaySessionStore] = None
  var secret: Option[String] = None

  def apply(configuration: Configuration, playSessionStore: PlaySessionStore) = {
    this.configuration = Some(configuration)
    this.playSessionStore = Some(playSessionStore)
    this.secret = this.configuration.flatMap(_.getOptional[String]("pac4j.jwt_secret"))
  }

  def getClaims(requestHeader: RequestHeader): Option[mutable.Map[String, AnyRef]] = {

    val header: Option[String] = requestHeader.headers.get("Authorization")
    val token: Option[String] = for {
      h <- header
      t <- h.split("Bearer").lastOption
    } yield t.trim

    getClaimsFromToken(token)
  }

  def getClaimsFromToken(token: Option[String]): Option[mutable.Map[String, AnyRef]] = {
    val jwtAuthenticator = new JwtAuthenticator()
    jwtAuthenticator.addSignatureConfiguration(new SecretSignatureConfiguration(secret.getOrElse(throw new Exception("missing secret"))))
    token.map(jwtAuthenticator.validateTokenAndGetClaims(_).asScala)
  }

  def getProfiles(request: RequestHeader): List[CommonProfile] = {
    val webContext = new PlayWebContext(request, playSessionStore.getOrElse(throw new Exception("missing playSessionStore")))
    val profileManager = new ProfileManager[CommonProfile](webContext)
    profileManager.getAll(true).asScala.toList
  }

  def getStringToken: (RequestHeader,Long) => Option[String] = (request: RequestHeader,minutes:Long)  => {
    val generator = new JwtGenerator[CommonProfile](new SecretSignatureConfiguration(secret.getOrElse(throw new Exception("missing secret"))))
    val profiles = getProfiles(request)
    val token: Option[String] = profiles.headOption.map(profile => {
      val expDate = new Date( (new Date).getTime + 1000L*60L*minutes )//*60L*24L
      val claims = new JWTClaimsSet.Builder().expirationTime(expDate).build()
      profile.addAttributes(claims.getClaims)
      generator.generate(profile)
    })
    token
  }

  def getToken: (RequestHeader,Long) => Result = (request: RequestHeader, minutes:Long) => {
    Ok(getStringToken(request,minutes).getOrElse(""))
  }

}
