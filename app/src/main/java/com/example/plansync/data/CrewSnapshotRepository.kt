package com.example.plansync.data

import com.example.plansync.model.CrewSnapshot
import com.example.plansync.model.Friend
import com.example.plansync.model.Group
import com.example.plansync.model.User
import com.google.firebase.auth.FirebaseAuth

class CrewSnapshotRepository(
    private val dao: CrewDao,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    suspend fun getSnapshot(): Result<CrewSnapshot> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            val members = dao.getMembers(uid)
            CrewSnapshot(
                groups = dao.getGroups(uid).map { Group(it.id, it.name, it.description) },
                friends = dao.getFriends(uid).map {
                    Friend(it.id, it.name, it.email, it.avatarColorIndex, it.photoUrl)
                },
                memberIdsByGroup = members.groupBy({ it.groupId }, { it.userId }),
                groupMembers = members
                    .filter { it.hasProfile }
                    .associate { it.userId to User(id = it.userId, name = it.name, email = "", lastName = it.lastName, photoUrl = it.photoUrl) }
            )
        }
    }

    suspend fun saveGroups(groups: List<Group>): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            dao.replaceGroups(uid, groups.mapIndexed { i, g -> CrewGroupEntity(uid, g.id, g.name, g.description, i) })
        }
    }

    suspend fun saveFriends(friends: List<Friend>): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            dao.replaceFriends(uid, friends.mapIndexed { i, f ->
                CrewFriendEntity(uid, f.id, f.name, f.email, f.avatarColorIndex, f.photoUrl, i)
            })
        }
    }

    suspend fun saveMembers(memberIdsByGroup: Map<String, List<String>>, usersById: Map<String, User>): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            val rows = memberIdsByGroup.flatMap { (groupId, ids) ->
                ids.mapIndexed { i, userId ->
                    val user = if (i < 3) usersById[userId] else null
                    CrewMemberEntity(
                        cachedFor = uid,
                        groupId = groupId,
                        userId = userId,
                        position = i,
                        hasProfile = user != null,
                        name = user?.name.orEmpty(),
                        lastName = user?.lastName.orEmpty(),
                        photoUrl = user?.photoUrl.orEmpty()
                    )
                }
            }
            dao.replaceMembers(uid, rows)
        }
    }
}
