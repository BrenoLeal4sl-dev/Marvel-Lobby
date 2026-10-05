package com.example.marvellobby.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName="remote_accounts")
public class RemoteAccount {
 @PrimaryKey @NonNull public String owner="";
 @NonNull public String payload="";
 public RemoteAccount() {}
}
