package com.example.marvellobby.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;

/** Durable, owner-scoped outbox; recordKey @sharing holds a pending visibility choice. */
@Entity(tableName="public_favorite_changes",primaryKeys={"owner","recordKey"})
public class PublicFavoriteChange {
 @NonNull public String owner="";
 @NonNull public String recordKey="";
 @NonNull public String payload="";
 public boolean favorite;
 @NonNull public String nonce="";
}
