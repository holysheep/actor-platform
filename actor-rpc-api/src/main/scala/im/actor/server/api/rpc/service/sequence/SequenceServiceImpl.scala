package im.actor.server.api.rpc.service.sequence

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

import akka.actor.ActorSystem
import akka.event.Logging
import slick.driver.PostgresDriver.api._

import im.actor.api.rpc._
import im.actor.api.rpc.misc.{ ResponseSeq, ResponseVoid }
import im.actor.api.rpc.peers.{ GroupOutPeer, UserOutPeer }
import im.actor.api.rpc.sequence.{ DifferenceUpdate, ResponseGetDifference, SequenceService }
import im.actor.api.rpc.users.{ Email, Phone }
import im.actor.server.models
import im.actor.server.push.{ SeqUpdatesManager, SeqUpdatesManagerRegion }
import im.actor.server.session.{ SessionMessage, SessionRegion }
import im.actor.server.util.{ AnyRefLogSource, GroupUtils, UserUtils }

class SequenceServiceImpl(
  implicit
  seqUpdManagerRegion: SeqUpdatesManagerRegion,
  sessionRegion:       SessionRegion,
  db:                  Database,
  actorSystem:         ActorSystem
) extends SequenceService {
  import AnyRefLogSource._
  import GroupUtils._
  import SeqUpdatesManager._
  import UserUtils._

  private val log = Logging(actorSystem, this)

  override implicit val ec: ExecutionContext = actorSystem.dispatcher

  override def jhandleGetState(clientData: ClientData): Future[HandlerResult[ResponseSeq]] = {
    val authorizedAction = requireAuth(clientData).map { implicit client ⇒
      for {
        seqstate ← getSeqState(client.authId)
      } yield Ok(ResponseSeq(seqstate._1, seqstate._2))
    }

    db.run(toDBIOAction(authorizedAction))
  }

  override def jhandleGetDifference(seq: Int, state: Array[Byte], clientData: ClientData): Future[HandlerResult[ResponseGetDifference]] = {
    val authorizedAction = requireAuth(clientData).map { implicit client ⇒
      for {
        // FIXME: would new updates between getSeqState and getDifference break client state?
        (updates, needMore, newState) ← getDifference(client.authId, state)
        (diffUpdates, userIds, groupIds) = extractDiff(updates)
        (users, groups) ← getUsersGroups(userIds, groupIds)
      } yield {
        val newSeq = updates.lastOption.map(_.seq).getOrElse(seq)

        log.debug("Requested timestamp {}, {}", bytesToTimestamp(state), clientData)
        log.debug("Updates {}, {}", updates, clientData)
        log.debug("New state {}, {}", bytesToTimestamp(newState), clientData)

        val (phones, emails) = users.foldLeft(Vector.empty[Phone], Vector.empty[Email]) {
          case ((phones, emails), user) ⇒
            val (userPhones, userEmails) = userPhonesEmails(user)

            (phones ++ userPhones, emails ++ userEmails)
        }

        Ok(ResponseGetDifference(
          seq = newSeq,
          state = newState,
          updates = diffUpdates,
          needMore = needMore,
          users = users.toVector,
          phones = phones,
          emails = emails,
          groups = groups.toVector
        ))
      }
    }

    db.run(toDBIOAction(authorizedAction map (_.transactionally)))
  }

  override def jhandleSubscribeToOnline(users: Vector[UserOutPeer], clientData: ClientData): Future[HandlerResult[ResponseVoid]] = {
    val authorizedAction = requireAuth(clientData).map { client ⇒
      DBIO.successful(Ok(ResponseVoid))
    }

    db.run(toDBIOAction(authorizedAction)) andThen {
      case Success(_) ⇒
        // FIXME: #security check access hashes
        val userIds = users.map(_.userId).toSet

        sessionRegion.ref ! SessionMessage.envelope(
          clientData.authId,
          clientData.sessionId,
          SessionMessage.SubscribeToOnline(userIds)
        )
    }
  }

  override def jhandleSubscribeFromOnline(users: Vector[UserOutPeer], clientData: ClientData): Future[HandlerResult[ResponseVoid]] = {
    val authorizedAction = requireAuth(clientData).map { client ⇒
      DBIO.successful(Ok(ResponseVoid))
    }

    db.run(toDBIOAction(authorizedAction)) andThen {
      case Success(_) ⇒
        // FIXME: #security check access hashes
        val userIds = users.map(_.userId).toSet

        sessionRegion.ref ! SessionMessage.envelope(
          clientData.authId,
          clientData.sessionId,
          SessionMessage.SubscribeFromOnline(userIds)
        )
    }
  }

  override def jhandleSubscribeFromGroupOnline(groups: Vector[GroupOutPeer], clientData: ClientData): Future[HandlerResult[ResponseVoid]] = {
    Future.successful(Ok(ResponseVoid)) andThen {
      case _ ⇒
        // FIXME: #security check access hashes
        sessionRegion.ref ! SessionMessage.envelope(clientData.authId, clientData.sessionId, SessionMessage.SubscribeFromGroupOnline(groups.map(_.groupId).toSet))
    }
  }

  override def jhandleSubscribeToGroupOnline(groups: Vector[GroupOutPeer], clientData: ClientData): Future[HandlerResult[ResponseVoid]] = {
    Future.successful(Ok(ResponseVoid)) andThen {
      case _ ⇒
        // FIXME: #security check access hashes
        sessionRegion.ref ! SessionMessage.envelope(clientData.authId, clientData.sessionId, SessionMessage.SubscribeToGroupOnline(groups.map(_.groupId).toSet))
    }
  }

  private def extractDiff(updates: Seq[models.sequence.SeqUpdate]): (Vector[DifferenceUpdate], Set[Int], Set[Int]) = {
    updates.foldLeft[(Vector[DifferenceUpdate], Set[Int], Set[Int])](Vector.empty, Set.empty, Set.empty) {
      case ((updates, userIds, groupIds), update) ⇒
        (updates :+ DifferenceUpdate(update.header, update.serializedData),
          userIds ++ update.userIds,
          groupIds ++ update.groupIds)
    }
  }

  private def getUsersGroups(userIds: Set[Int], groupIds: Set[Int])(implicit client: AuthorizedClientData) = {
    for {
      groups ← getGroupsStructs(groupIds)
      // TODO: #perf optimize collection operations
      allUserIds = userIds ++ groups.foldLeft(Set.empty[Int]) { (ids, g) ⇒ ids ++ g.members.map(m ⇒ Seq(m.userId, m.inviterUserId)).flatten + g.creatorUserId }
      users ← userStructs(allUserIds)
    } yield (users, groups)
  }
}
