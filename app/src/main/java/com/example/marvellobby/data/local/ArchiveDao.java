package com.example.marvellobby.data.local;
import androidx.room.*;
import java.util.List;
@Dao public interface ArchiveDao {
 @Query("SELECT * FROM records WHERE owner = :owner ORDER BY viewedAt DESC") List<StoredRecord> records(String owner);
 @Query("SELECT * FROM records WHERE owner = :owner AND recordKey = :key LIMIT 1") StoredRecord record(String owner, String key);
 @Insert(onConflict=OnConflictStrategy.REPLACE) void save(StoredRecord record);
 @Transaction default void updateRecord(String owner,String key,String payload,boolean toggle,long viewedAt) {
  AccountBinding linked=binding(owner);String target=linked==null?owner:linked.remoteOwner;
  StoredRecord record=record(target,key);
  if(record==null){record=new StoredRecord();record.owner=target;record.recordKey=key;}
  record.payload=payload;
  if(toggle)record.favorite=!record.favorite;else record.viewedAt=viewedAt;
  save(record);
  if(toggle && target.startsWith("remote:"))queuePublicFavorite(record);
 }
 @Insert(onConflict=OnConflictStrategy.REPLACE) void queuePublicChange(PublicFavoriteChange change);
 @Query("SELECT * FROM public_favorite_changes WHERE owner=:owner AND recordKey='@sharing' LIMIT 1") PublicFavoriteChange sharingChange(String owner);
 @Query("SELECT * FROM public_favorite_changes WHERE owner=:owner AND recordKey<>'@sharing' ORDER BY recordKey LIMIT 20") List<PublicFavoriteChange> publicChanges(String owner);
 @Query("DELETE FROM public_favorite_changes WHERE owner=:owner AND recordKey=:key AND nonce=:nonce") void acknowledgePublicChange(String owner,String key,String nonce);
 default void queuePublicFavorite(StoredRecord record) {
  if(!record.recordKey.matches("(CHARACTER|POWER|TEAM|STORY_ARC):[1-9][0-9]*"))return;
  PublicFavoriteChange change=new PublicFavoriteChange();change.owner=record.owner;change.recordKey=record.recordKey;
  change.payload=record.payload;change.favorite=record.favorite;change.nonce=java.util.UUID.randomUUID().toString();queuePublicChange(change);
 }
 @Transaction default void queueFavoriteSharing(String owner,boolean enabled) {
  if(!owner.startsWith("remote:"))throw new IllegalArgumentException("Connect an online account first.");
  if(enabled)for(StoredRecord record:records(owner))if(record.favorite)queuePublicFavorite(record);
  PublicFavoriteChange choice=new PublicFavoriteChange();choice.owner=owner;choice.recordKey="@sharing";
  choice.favorite=enabled;choice.nonce=java.util.UUID.randomUUID().toString();queuePublicChange(choice);
 }
 @Query("DELETE FROM records WHERE owner = :owner AND favorite = 0") void deleteHistory(String owner);
 @Query("UPDATE records SET viewedAt = 0 WHERE owner = :owner") void resetHistory(String owner);
 @Query("SELECT * FROM accounts WHERE email = :email LIMIT 1") LocalAccount account(String email);
 @Query("SELECT * FROM accounts WHERE username = :username LIMIT 1") LocalAccount accountByUsername(String username);
 @Query("SELECT * FROM remote_accounts WHERE owner = :owner LIMIT 1") RemoteAccount remoteAccount(String owner);
 @Insert(onConflict=OnConflictStrategy.REPLACE) void saveRemoteAccount(RemoteAccount account);
 @Query("SELECT * FROM account_bindings WHERE localOwner = :owner LIMIT 1") AccountBinding binding(String owner);
 @Insert(onConflict=OnConflictStrategy.ABORT) void insertBinding(AccountBinding binding);
 @Query("INSERT OR REPLACE INTO records(owner,recordKey,payload,favorite,viewedAt) SELECT :target,a.recordKey,CASE WHEN coalesce(b.viewedAt,0)>a.viewedAt THEN b.payload ELSE a.payload END,CASE WHEN a.favorite OR b.favorite THEN 1 ELSE 0 END,max(a.viewedAt,coalesce(b.viewedAt,0)) FROM records a LEFT JOIN records b ON b.owner=:target AND b.recordKey=a.recordKey WHERE a.owner=:source")
 void mergeLibrary(String source,String target);
 @Query("DELETE FROM records WHERE owner=:owner") void removeOwnerRecords(String owner);
 @Transaction default void bindLocalAccount(String source,RemoteAccount target) {
  if(account(source)==null)throw new IllegalStateException("Local account unavailable.");
  AccountBinding existing=binding(source);
  if(existing!=null) {
   if(!existing.remoteOwner.equals(target.owner))throw new IllegalStateException("This local account is already connected to another online account.");
   saveRemoteAccount(target);return;
  }
  saveRemoteAccount(target);
  mergeLibrary(source,target.owner);removeOwnerRecords(source);moveConversations(source,target.owner);
  for(StoredRecord record:records(target.owner))if(record.favorite)queuePublicFavorite(record);
  AccountBinding binding=new AccountBinding();binding.localOwner=source;binding.remoteOwner=target.owner;insertBinding(binding);
 }
 @Insert(onConflict=OnConflictStrategy.ABORT) void insertAccount(LocalAccount account);
 @Update void updateAccount(LocalAccount account);
 @Query("DELETE FROM accounts WHERE email = :email") void deleteAccount(String email);
 @Query("UPDATE records SET owner = :newOwner WHERE owner = :oldOwner") void moveRecords(String oldOwner,String newOwner);
 @Query("UPDATE conversations SET owner = :newOwner WHERE owner = :oldOwner") void moveConversations(String oldOwner,String newOwner);
 @Query("UPDATE accounts SET email=:newEmail,name=:name,passwordHash=:hash,salt=:salt,avatar=:avatar,username=:username,bio=:bio WHERE email=:oldEmail")
 void renameAccount(String oldEmail,String newEmail,String name,String hash,String salt,String avatar,String username,String bio);
 @Transaction default void changeAccountEmail(String oldEmail,LocalAccount replacement) {
  renameAccount(oldEmail,replacement.email,replacement.name,replacement.passwordHash,replacement.salt,replacement.avatar,replacement.username,replacement.bio);
  moveRecords(oldEmail,replacement.email);
  moveConversations(oldEmail,replacement.email);
 }
 @Query("SELECT * FROM conversations WHERE owner = :owner ORDER BY updatedAt DESC") List<StoredConversation> conversations(String owner);
 @Query("SELECT * FROM conversations WHERE owner = :owner AND id = :id LIMIT 1") StoredConversation conversation(String owner,String id);
 @Insert(onConflict=OnConflictStrategy.REPLACE) void insertConversation(StoredConversation conversation);
 @Transaction default void saveConversation(StoredConversation conversation) {
  AccountBinding linked=binding(conversation.owner);
  if(linked!=null)conversation.owner=linked.remoteOwner;
  if(!conversation.owner.equals("guest") && account(conversation.owner)==null && remoteAccount(conversation.owner)==null)return;
  StoredConversation previous=conversation(conversation.owner,conversation.id);
  // A UUID belongs to a single account; never overwrite another owner's chat.
  if(previous==null && conversationOwner(conversation.id)!=null)return;
  insertConversation(conversation);
 }
 @Query("SELECT owner FROM conversations WHERE id = :id LIMIT 1") String conversationOwner(String id);
 @Query("DELETE FROM conversations WHERE owner = :owner AND id = :id") void deleteConversation(String owner,String id);
}
