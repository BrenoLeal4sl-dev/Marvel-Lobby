package com.example.marvellobby.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName="account_bindings")
public class AccountBinding {
 @PrimaryKey @NonNull public String localOwner="";
 @NonNull public String remoteOwner="";
 public AccountBinding() {}
}
