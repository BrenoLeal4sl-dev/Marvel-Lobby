package com.example.marvellobby.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
@Entity(tableName="cloud_records",primaryKeys={"owner","recordKey"})
public class CloudRecord {
 @NonNull public String owner="";
 @NonNull public String recordKey="";
 public long revision;
 public String payload;
 public String nonce;
 public boolean pending;
}
