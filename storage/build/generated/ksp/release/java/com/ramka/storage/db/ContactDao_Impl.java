package com.ramka.storage.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ContactDao_Impl implements ContactDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ContactEntity> __insertionAdapterOfContactEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateAlias;

  private final SharedSQLiteStatement __preparedStmtOfUpdateAddress;

  private final SharedSQLiteStatement __preparedStmtOfUpdateKey;

  private final SharedSQLiteStatement __preparedStmtOfIncrementUnreadCount;

  private final SharedSQLiteStatement __preparedStmtOfResetUnreadCount;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  public ContactDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfContactEntity = new EntityInsertionAdapter<ContactEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `contacts` (`localId`,`alias`,`publicKeyB64`,`signingPublicKeyB64`,`addressType`,`addressHost`,`addressPort`,`verificationStatus`,`addedAtEpochDay`,`unreadCount`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ContactEntity entity) {
        statement.bindString(1, entity.getLocalId());
        statement.bindString(2, entity.getAlias());
        statement.bindString(3, entity.getPublicKeyB64());
        statement.bindString(4, entity.getSigningPublicKeyB64());
        if (entity.getAddressType() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getAddressType());
        }
        if (entity.getAddressHost() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getAddressHost());
        }
        if (entity.getAddressPort() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getAddressPort());
        }
        statement.bindString(8, entity.getVerificationStatus());
        statement.bindLong(9, entity.getAddedAtEpochDay());
        statement.bindLong(10, entity.getUnreadCount());
      }
    };
    this.__preparedStmtOfUpdateAlias = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE contacts SET alias = ? WHERE localId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateAddress = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE contacts SET addressType = ?, addressHost = ?, addressPort = ?\n"
                + "        WHERE localId = ?\n"
                + "    ";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateKey = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE contacts SET publicKeyB64 = ?, verificationStatus = ? WHERE localId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfIncrementUnreadCount = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE contacts SET unreadCount = unreadCount + 1 WHERE localId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfResetUnreadCount = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE contacts SET unreadCount = 0 WHERE localId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM contacts WHERE localId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final ContactEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfContactEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateAlias(final String localId, final String alias,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateAlias.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, alias);
        _argIndex = 2;
        _stmt.bindString(_argIndex, localId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateAlias.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateAddress(final String localId, final String type, final String host,
      final Integer port, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateAddress.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, type);
        _argIndex = 2;
        if (host == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, host);
        }
        _argIndex = 3;
        if (port == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, port);
        }
        _argIndex = 4;
        _stmt.bindString(_argIndex, localId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateAddress.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateKey(final String localId, final String publicKeyB64, final String status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateKey.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, publicKeyB64);
        _argIndex = 2;
        _stmt.bindString(_argIndex, status);
        _argIndex = 3;
        _stmt.bindString(_argIndex, localId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateKey.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementUnreadCount(final String localId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementUnreadCount.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, localId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncrementUnreadCount.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object resetUnreadCount(final String localId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfResetUnreadCount.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, localId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfResetUnreadCount.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String localId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, localId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ContactEntity>> observeAll() {
    final String _sql = "SELECT * FROM contacts ORDER BY (unreadCount > 0) DESC, alias COLLATE NOCASE";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"contacts"}, new Callable<List<ContactEntity>>() {
      @Override
      @NonNull
      public List<ContactEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfLocalId = CursorUtil.getColumnIndexOrThrow(_cursor, "localId");
          final int _cursorIndexOfAlias = CursorUtil.getColumnIndexOrThrow(_cursor, "alias");
          final int _cursorIndexOfPublicKeyB64 = CursorUtil.getColumnIndexOrThrow(_cursor, "publicKeyB64");
          final int _cursorIndexOfSigningPublicKeyB64 = CursorUtil.getColumnIndexOrThrow(_cursor, "signingPublicKeyB64");
          final int _cursorIndexOfAddressType = CursorUtil.getColumnIndexOrThrow(_cursor, "addressType");
          final int _cursorIndexOfAddressHost = CursorUtil.getColumnIndexOrThrow(_cursor, "addressHost");
          final int _cursorIndexOfAddressPort = CursorUtil.getColumnIndexOrThrow(_cursor, "addressPort");
          final int _cursorIndexOfVerificationStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "verificationStatus");
          final int _cursorIndexOfAddedAtEpochDay = CursorUtil.getColumnIndexOrThrow(_cursor, "addedAtEpochDay");
          final int _cursorIndexOfUnreadCount = CursorUtil.getColumnIndexOrThrow(_cursor, "unreadCount");
          final List<ContactEntity> _result = new ArrayList<ContactEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ContactEntity _item;
            final String _tmpLocalId;
            _tmpLocalId = _cursor.getString(_cursorIndexOfLocalId);
            final String _tmpAlias;
            _tmpAlias = _cursor.getString(_cursorIndexOfAlias);
            final String _tmpPublicKeyB64;
            _tmpPublicKeyB64 = _cursor.getString(_cursorIndexOfPublicKeyB64);
            final String _tmpSigningPublicKeyB64;
            _tmpSigningPublicKeyB64 = _cursor.getString(_cursorIndexOfSigningPublicKeyB64);
            final String _tmpAddressType;
            if (_cursor.isNull(_cursorIndexOfAddressType)) {
              _tmpAddressType = null;
            } else {
              _tmpAddressType = _cursor.getString(_cursorIndexOfAddressType);
            }
            final String _tmpAddressHost;
            if (_cursor.isNull(_cursorIndexOfAddressHost)) {
              _tmpAddressHost = null;
            } else {
              _tmpAddressHost = _cursor.getString(_cursorIndexOfAddressHost);
            }
            final Integer _tmpAddressPort;
            if (_cursor.isNull(_cursorIndexOfAddressPort)) {
              _tmpAddressPort = null;
            } else {
              _tmpAddressPort = _cursor.getInt(_cursorIndexOfAddressPort);
            }
            final String _tmpVerificationStatus;
            _tmpVerificationStatus = _cursor.getString(_cursorIndexOfVerificationStatus);
            final long _tmpAddedAtEpochDay;
            _tmpAddedAtEpochDay = _cursor.getLong(_cursorIndexOfAddedAtEpochDay);
            final int _tmpUnreadCount;
            _tmpUnreadCount = _cursor.getInt(_cursorIndexOfUnreadCount);
            _item = new ContactEntity(_tmpLocalId,_tmpAlias,_tmpPublicKeyB64,_tmpSigningPublicKeyB64,_tmpAddressType,_tmpAddressHost,_tmpAddressPort,_tmpVerificationStatus,_tmpAddedAtEpochDay,_tmpUnreadCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final String localId,
      final Continuation<? super ContactEntity> $completion) {
    final String _sql = "SELECT * FROM contacts WHERE localId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, localId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ContactEntity>() {
      @Override
      @Nullable
      public ContactEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfLocalId = CursorUtil.getColumnIndexOrThrow(_cursor, "localId");
          final int _cursorIndexOfAlias = CursorUtil.getColumnIndexOrThrow(_cursor, "alias");
          final int _cursorIndexOfPublicKeyB64 = CursorUtil.getColumnIndexOrThrow(_cursor, "publicKeyB64");
          final int _cursorIndexOfSigningPublicKeyB64 = CursorUtil.getColumnIndexOrThrow(_cursor, "signingPublicKeyB64");
          final int _cursorIndexOfAddressType = CursorUtil.getColumnIndexOrThrow(_cursor, "addressType");
          final int _cursorIndexOfAddressHost = CursorUtil.getColumnIndexOrThrow(_cursor, "addressHost");
          final int _cursorIndexOfAddressPort = CursorUtil.getColumnIndexOrThrow(_cursor, "addressPort");
          final int _cursorIndexOfVerificationStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "verificationStatus");
          final int _cursorIndexOfAddedAtEpochDay = CursorUtil.getColumnIndexOrThrow(_cursor, "addedAtEpochDay");
          final int _cursorIndexOfUnreadCount = CursorUtil.getColumnIndexOrThrow(_cursor, "unreadCount");
          final ContactEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpLocalId;
            _tmpLocalId = _cursor.getString(_cursorIndexOfLocalId);
            final String _tmpAlias;
            _tmpAlias = _cursor.getString(_cursorIndexOfAlias);
            final String _tmpPublicKeyB64;
            _tmpPublicKeyB64 = _cursor.getString(_cursorIndexOfPublicKeyB64);
            final String _tmpSigningPublicKeyB64;
            _tmpSigningPublicKeyB64 = _cursor.getString(_cursorIndexOfSigningPublicKeyB64);
            final String _tmpAddressType;
            if (_cursor.isNull(_cursorIndexOfAddressType)) {
              _tmpAddressType = null;
            } else {
              _tmpAddressType = _cursor.getString(_cursorIndexOfAddressType);
            }
            final String _tmpAddressHost;
            if (_cursor.isNull(_cursorIndexOfAddressHost)) {
              _tmpAddressHost = null;
            } else {
              _tmpAddressHost = _cursor.getString(_cursorIndexOfAddressHost);
            }
            final Integer _tmpAddressPort;
            if (_cursor.isNull(_cursorIndexOfAddressPort)) {
              _tmpAddressPort = null;
            } else {
              _tmpAddressPort = _cursor.getInt(_cursorIndexOfAddressPort);
            }
            final String _tmpVerificationStatus;
            _tmpVerificationStatus = _cursor.getString(_cursorIndexOfVerificationStatus);
            final long _tmpAddedAtEpochDay;
            _tmpAddedAtEpochDay = _cursor.getLong(_cursorIndexOfAddedAtEpochDay);
            final int _tmpUnreadCount;
            _tmpUnreadCount = _cursor.getInt(_cursorIndexOfUnreadCount);
            _result = new ContactEntity(_tmpLocalId,_tmpAlias,_tmpPublicKeyB64,_tmpSigningPublicKeyB64,_tmpAddressType,_tmpAddressHost,_tmpAddressPort,_tmpVerificationStatus,_tmpAddedAtEpochDay,_tmpUnreadCount);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
