package com.example.handcricket.data

import android.content.Context
import androidx.room.*

@Entity
data class Tournament(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val format: String // "RoundRobin" or "Groups"
)

@Entity
data class Team(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tournamentId: Int,
    val name: String,
    val groupName: String? = null
)

@Entity(tableName = "player_performance")
data class PlayerPerformance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val matchId: Int,
    val tournamentId: Int,
    val teamId: Int,
    val playerName: String,
    val role: String, // "batter" or "bowler"
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val wickets: Int = 0,
    val overs: Float = 0f,
    val runsConceded: Int = 0,
    val economy: Float = 0f,
    val strikeRate: Float = 0f,
    val isOut: Boolean = false,
    val date: String = ""
)

@Entity(tableName = "match_results")
data class MatchResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val matchId: Int,
    val tournamentId: Int,
    val team1Id: Int,
    val team2Id: Int,
    val team1Score: String, // "186/10 (9.1)"
    val team2Score: String, // "188/3 (9.0)"
    val winnerTeamId: Int,
    val playerOfMatch: String,
    val date: String,
    val matchType: String
)

@Entity(tableName = "matches")
data class Match(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tournamentId: Int,
    val teamAId: Int,
    val teamBId: Int,
    val matchType: String, // "Group", "Semi", "Final", "RoundRobin"
    val winnerTeamId: Int? = null
)

@Dao
interface TournamentDao {
    @Insert suspend fun insertTournament(tournament: Tournament): Long
    @Insert suspend fun insertTeams(teams: List<Team>): List<Long>
    @Insert suspend fun insertMatches(matches: List<Match>)

    @Query("SELECT * FROM Tournament")
    suspend fun getAllTournaments(): List<Tournament>

    @Query("SELECT * FROM Team WHERE tournamentId = :tournamentId")
    suspend fun getTeams(tournamentId: Int): List<Team>

    @Query("SELECT * FROM matches WHERE tournamentId = :tournamentId")
    suspend fun getMatches(tournamentId: Int): List<Match>

    @Insert
    suspend fun insertPlayerPerformance(performance: PlayerPerformance): Long

    @Insert
    suspend fun insertPlayerPerformances(performances: List<PlayerPerformance>)

    @Query("SELECT * FROM player_performance WHERE tournamentId = :tournamentId")
    suspend fun getPlayerPerformances(tournamentId: Int): List<PlayerPerformance>

    @Query("SELECT * FROM player_performance WHERE matchId = :matchId")
    suspend fun getPlayerPerformancesByMatch(matchId: Int): List<PlayerPerformance>

    @Insert
    suspend fun insertMatchResult(result: MatchResult): Long

    @Query("SELECT * FROM match_results WHERE matchId = :matchId")
    suspend fun getMatchResult(matchId: Int): MatchResult?

    @Query("SELECT * FROM match_results WHERE tournamentId = :tournamentId")
    suspend fun getMatchResults(tournamentId: Int): List<MatchResult>

    @Query("SELECT * FROM matches WHERE id = :matchId")
    suspend fun getMatchById(matchId: Int): Match?

    @Query("UPDATE matches SET winnerTeamId = :winnerId WHERE id = :matchId")
    suspend fun updateMatchWinner(matchId: Int, winnerId: Int)
}

@Database(
    entities = [
        Tournament::class,
        Team::class,
        Match::class,
        PlayerPerformance::class,
        MatchResult::class  // ADD THIS LINE
    ],
    version = 4  // INCREMENT VERSION
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tournamentDao(): TournamentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hand_cricket_db"
                )
                    .fallbackToDestructiveMigration()  // This will recreate the database with new tables
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}