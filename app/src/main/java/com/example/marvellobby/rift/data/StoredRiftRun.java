package com.example.marvellobby.rift.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
@Entity(tableName="rift_runs",indices={@Index(value={"owner","endedAt"})})
public class StoredRiftRun {
 @PrimaryKey @NonNull public String id="";
 @NonNull public String owner="";
 @NonNull public String payload="";
 public String sessionId;
 @NonNull public String status="practice";
 public long endedAt;
 public int score;
 public float duration;
 public int kills;
 public int bosses;
}
