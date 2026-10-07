package com.example.marvellobby.rift.data;
import androidx.room.*;
import java.util.List;
@Dao public interface RiftDao {
 @Insert(onConflict=OnConflictStrategy.IGNORE) long insert(StoredRiftRun run);
 @Query("SELECT * FROM rift_runs WHERE owner=:owner ORDER BY endedAt DESC LIMIT 100") List<StoredRiftRun> history(String owner);
 @Query("SELECT count(*) AS runs,coalesce(max(score),0) AS best,coalesce(max(duration),0) AS survival,coalesce(sum(kills),0) AS kills,coalesce(sum(bosses),0) AS bosses FROM rift_runs WHERE owner=:owner") RiftTotals totals(String owner);
 @Query("SELECT * FROM rift_runs WHERE owner=:owner AND status='pending' ORDER BY endedAt LIMIT 20") List<StoredRiftRun> pending(String owner);
 @Query("UPDATE rift_runs SET status=:status WHERE owner=:owner AND id=:id AND status='pending'") void status(String owner,String id,String status);
}
