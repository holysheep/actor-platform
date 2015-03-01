package im.actor.model.api.updates;
/*
 *  Generated by the Actor API Scheme generator.  DO NOT EDIT!
 */

import im.actor.model.droidkit.bser.Bser;
import im.actor.model.droidkit.bser.BserObject;
import im.actor.model.droidkit.bser.BserValues;
import im.actor.model.droidkit.bser.BserWriter;
import static im.actor.model.droidkit.bser.Utils.*;
import java.io.IOException;
import im.actor.model.network.parser.*;
import java.util.List;
import java.util.ArrayList;
import im.actor.model.api.*;

public class UpdateUserLastSeen extends Update {

    public static final int HEADER = 0x9;
    public static UpdateUserLastSeen fromBytes(byte[] data) throws IOException {
        return Bser.parse(new UpdateUserLastSeen(), data);
    }

    private int uid;
    private long date;

    public UpdateUserLastSeen(int uid, long date) {
        this.uid = uid;
        this.date = date;
    }

    public UpdateUserLastSeen() {

    }

    public int getUid() {
        return this.uid;
    }

    public long getDate() {
        return this.date;
    }

    @Override
    public void parse(BserValues values) throws IOException {
        this.uid = values.getInt(1);
        this.date = values.getLong(2);
    }

    @Override
    public void serialize(BserWriter writer) throws IOException {
        writer.writeInt(1, this.uid);
        writer.writeLong(2, this.date);
    }

    @Override
    public String toString() {
        String res = "update UserLastSeen{";
        res += "uid=" + this.uid;
        res += ", date=" + this.date;
        res += "}";
        return res;
    }

    @Override
    public int getHeaderKey() {
        return HEADER;
    }
}
