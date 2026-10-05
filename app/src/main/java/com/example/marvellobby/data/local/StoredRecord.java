package com.example.marvellobby.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
@Entity(tableName="records", primaryKeys={"owner","recordKey"})
public class StoredRecord {
 @NonNull public String owner="";
 @NonNull public String recordKey="";
 @NonNull public String payload="";
 public boolean favorite;
 public long viewedAt;
 public StoredRecord() {}
}
