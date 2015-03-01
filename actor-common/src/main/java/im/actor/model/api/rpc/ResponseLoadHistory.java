package im.actor.model.api.rpc;
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

public class ResponseLoadHistory extends Response {

    public static final int HEADER = 0x77;
    public static ResponseLoadHistory fromBytes(byte[] data) throws IOException {
        return Bser.parse(new ResponseLoadHistory(), data);
    }

    private List<HistoryMessage> history;
    private List<User> users;

    public ResponseLoadHistory(List<HistoryMessage> history, List<User> users) {
        this.history = history;
        this.users = users;
    }

    public ResponseLoadHistory() {

    }

    public List<HistoryMessage> getHistory() {
        return this.history;
    }

    public List<User> getUsers() {
        return this.users;
    }

    @Override
    public void parse(BserValues values) throws IOException {
        List<HistoryMessage> _history = new ArrayList<HistoryMessage>();
        for (int i = 0; i < values.getRepeatedCount(1); i ++) {
            _history.add(new HistoryMessage());
        }
        this.history = values.getRepeatedObj(1, _history);
        List<User> _users = new ArrayList<User>();
        for (int i = 0; i < values.getRepeatedCount(2); i ++) {
            _users.add(new User());
        }
        this.users = values.getRepeatedObj(2, _users);
    }

    @Override
    public void serialize(BserWriter writer) throws IOException {
        writer.writeRepeatedObj(1, this.history);
        writer.writeRepeatedObj(2, this.users);
    }

    @Override
    public String toString() {
        String res = "tuple LoadHistory{";
        res += "}";
        return res;
    }

    @Override
    public int getHeaderKey() {
        return HEADER;
    }
}
