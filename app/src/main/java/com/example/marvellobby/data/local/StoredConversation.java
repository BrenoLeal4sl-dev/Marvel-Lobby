package com.example.marvellobby.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
@Entity(tableName="conversations",indices={@Index(value={"owner","updatedAt"})})
public class StoredConversation {
 @PrimaryKey @NonNull public String id="";
 @NonNull public String owner="";
 @NonNull public String title="";
 @NonNull public String payload="";
 public long updatedAt;
}
