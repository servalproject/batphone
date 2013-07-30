package org.servalproject.messages;

import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;

public class FlipCursor implements Cursor {
    private final Cursor internal;
    public FlipCursor(Cursor internal){
        this.internal=internal;
    }

    @Override
    public int getCount() {
        return internal.getCount();
    }

    @Override
    public int getPosition() {
        return internal.getCount() - internal.getPosition();
    }

    @Override
    public boolean move(int i) {
        return internal.move(-i);
    }

    @Override
    public boolean moveToPosition(int i) {
        return internal.moveToPosition(internal.getCount() - i -1);
    }

    @Override
    public boolean moveToFirst() {
        return internal.moveToLast();
    }

    @Override
    public boolean moveToLast() {
        return internal.moveToFirst();
    }

    @Override
    public boolean moveToNext() {
        return internal.moveToPrevious();
    }

    @Override
    public boolean moveToPrevious() {
        return internal.moveToNext();
    }

    @Override
    public boolean isFirst() {
        return internal.isLast();
    }

    @Override
    public boolean isLast() {
        return internal.isFirst();
    }

    @Override
    public boolean isBeforeFirst() {
        return internal.isAfterLast();
    }

    @Override
    public boolean isAfterLast() {
        return internal.isBeforeFirst();
    }

    @Override
    public int getColumnIndex(String s) {
        return internal.getColumnIndex(s);
    }

    @Override
    public int getColumnIndexOrThrow(String s) throws IllegalArgumentException {
        return internal.getColumnIndexOrThrow(s);
    }

    @Override
    public String getColumnName(int i) {
        return internal.getColumnName(i);
    }

    @Override
    public String[] getColumnNames() {
        return internal.getColumnNames();
    }

    @Override
    public int getColumnCount() {
        return internal.getColumnCount();
    }

    @Override
    public String getString(int i) {
        return internal.getString(i);
    }

    @Override
    public void copyStringToBuffer(int i, CharArrayBuffer charArrayBuffer) {
        internal.copyStringToBuffer(i, charArrayBuffer);
    }

    @Override
    public short getShort(int i) {
        return internal.getShort(i);
    }

    @Override
    public int getInt(int i) {
        return internal.getInt(i);
    }

    @Override
    public long getLong(int i) {
        return internal.getLong(i);
    }

    @Override
    public float getFloat(int i) {
        return internal.getFloat(i);
    }

    @Override
    public byte[] getBlob(int i) {
        return internal.getBlob(i);
    }

    @Override
    public double getDouble(int i) {
        return internal.getDouble(i);
    }

    @Override
    public boolean isNull(int i) {
        return internal.isNull(i);
    }

    @Override
    public void deactivate() {
        internal.deactivate();
    }

    @Override
    public boolean requery() {
        return internal.requery();
    }

    @Override
    public void close() {
        internal.close();
    }

    @Override
    public boolean isClosed() {
        return internal.isClosed();
    }

    @Override
    public void registerContentObserver(ContentObserver contentObserver) {
        internal.registerContentObserver(contentObserver);
    }

    @Override
    public void unregisterContentObserver(ContentObserver contentObserver) {
        internal.unregisterContentObserver(contentObserver);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver dataSetObserver) {
        internal.registerDataSetObserver(dataSetObserver);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
        internal.unregisterDataSetObserver(dataSetObserver);
    }

    @Override
    public void setNotificationUri(ContentResolver contentResolver, Uri uri) {
        internal.setNotificationUri(contentResolver, uri);
    }

    @Override
    public boolean getWantsAllOnMoveCalls() {
        return internal.getWantsAllOnMoveCalls();
    }

    @Override
    public Bundle getExtras() {
        return internal.getExtras();
    }

    @Override
    public Bundle respond(Bundle bundle) {
        return internal.respond(bundle);
    }
}
