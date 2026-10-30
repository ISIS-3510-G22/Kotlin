package com.example.plansync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CrewDao {

    @Query("SELECT * FROM crew_groups WHERE cachedFor = :uid ORDER BY position")
    suspend fun getGroups(uid: String): List<CrewGroupEntity>

    @Query("SELECT * FROM crew_friends WHERE cachedFor = :uid ORDER BY position")
    suspend fun getFriends(uid: String): List<CrewFriendEntity>

    @Query("SELECT * FROM crew_members WHERE cachedFor = :uid ORDER BY position")
    suspend fun getMembers(uid: String): List<CrewMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<CrewGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriends(friends: List<CrewFriendEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<CrewMemberEntity>)

    @Query("DELETE FROM crew_groups WHERE cachedFor = :uid")
    suspend fun deleteGroups(uid: String)

    @Query("DELETE FROM crew_friends WHERE cachedFor = :uid")
    suspend fun deleteFriends(uid: String)

    @Query("DELETE FROM crew_members WHERE cachedFor = :uid")
    suspend fun deleteMembers(uid: String)

    @Transaction
    suspend fun replaceGroups(uid: String, groups: List<CrewGroupEntity>) {
        deleteGroups(uid)
        insertGroups(groups)
    }

    @Transaction
    suspend fun replaceFriends(uid: String, friends: List<CrewFriendEntity>) {
        deleteFriends(uid)
        insertFriends(friends)
    }

    @Transaction
    suspend fun replaceMembers(uid: String, members: List<CrewMemberEntity>) {
        deleteMembers(uid)
        insertMembers(members)
    }
}
