package com.company.messenger.domain.schedule;

import com.company.messenger.domain.channel.*;
import com.company.messenger.global.exception.*;
import com.company.messenger.global.response.RealtimeEvents;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.util.*;

/** Epoch milliseconds preserve the same instant across client and database time zones. */
@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ScheduleService {
    public static final List<String> ROOMS = List.of("7층 중 회의실", "8층 중 회의실");
    private final JdbcTemplate db;
    private final ChannelRepository channels;
    private final ChannelService channelService;
    private final ChannelMemberRepository members;
    private final RealtimeEvents events;
    public record Input(@NotBlank @Size(max=200) String title, long startAt, long endAt,
                        boolean hasLocation, String location, @NotNull String audience, int reminderMinutes, Long revision) {}
    public record Schedule(long id, long channelId, String creator, String title, long startAt, long endAt,
                           String location, String audience, int reminderMinutes, long revision) {}
    public record Reminder(long id, long dueAt, Schedule schedule) {}
    private static final RowMapper<Schedule> ROW = (r, n) -> new Schedule(r.getLong("id"), r.getLong("channel_id"),
            r.getString("creator"), r.getString("title"), r.getLong("start_at"), r.getLong("end_at"),
            r.getString("location"), r.getString("audience"), r.getInt("reminder_minutes"), r.getLong("revision"));

    public List<Schedule> list(String user, long channel) {
        channelService.assertMembership(channel, user);
        return db.query("SELECT * FROM channel_schedules WHERE channel_id=? AND (audience='ROOM' OR creator=?) ORDER BY start_at,id", ROW, channel, user);
    }
    private void lock(String user, long channel) {
        channels.findForUpdate(channel).orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
        channelService.assertMembership(channel, user);
    }
    private Schedule owned(String user, long channel, long id) {
        var rows = db.query("SELECT * FROM channel_schedules WHERE id=? AND channel_id=?", ROW, id, channel);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        var row = rows.getFirst();
        if (!row.creator().equals(user)) throw new BusinessException(ErrorCode.SCHEDULE_MANAGEMENT_DENIED);
        return row;
    }
    private void validate(Input input) {
        if (input.title() == null || input.title().isBlank() || input.title().length()>200
                || input.startAt() <= System.currentTimeMillis() || input.endAt() <= input.startAt()
                || input.endAt() > 253402300799000L
                || !List.of("ROOM", "SELF").contains(input.audience() == null ? "" : input.audience())
                || !List.of(0,5,10,30,60).contains(input.reminderMinutes())
                || (input.hasLocation() && (input.location() == null || !ROOMS.contains(input.location()))))
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE);
    }
    public Schedule save(String user, long channel, Long id, Input input) {
        lock(user, channel);
        validate(input);
        long revision = 1;
        if (id != null) {
            var previous = owned(user, channel, id);
            if (!Objects.equals(input.revision(), previous.revision())) throw new BusinessException(ErrorCode.CONFLICT);
            revision = previous.revision()+1;
            db.update("UPDATE channel_schedules SET title=?,start_at=?,end_at=?,location=?,audience=?,reminder_minutes=?,revision=? WHERE id=?",
                    input.title().trim(), input.startAt(), input.endAt(), input.hasLocation()?input.location():null,
                    input.audience(), input.reminderMinutes(), revision, id);
            db.update("DELETE FROM schedule_reminders WHERE schedule_id=?", id);
        } else {
            var key = new GeneratedKeyHolder();
            db.update(connection -> {
                var ps = connection.prepareStatement("INSERT INTO channel_schedules(channel_id,creator,title,start_at,end_at,location,audience,reminder_minutes,revision) VALUES(?,?,?,?,?,?,?,?,1)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1,channel); ps.setString(2,user); ps.setString(3,input.title().trim());
                ps.setLong(4,input.startAt()); ps.setLong(5,input.endAt()); ps.setString(6,input.hasLocation()?input.location():null);
                ps.setString(7,input.audience()); ps.setInt(8,input.reminderMinutes()); return ps;
            }, key);
            id = Objects.requireNonNull(key.getKey()).longValue();
        }
        // Snapshot current participants; departed users are filtered again when reminders are read or acted on.
        for (var member : members.findActiveMembers(channel)) {
            String recipient = member.getUser().getUserId();
            if (input.audience().equals("ROOM") || recipient.equals(user))
                db.update("INSERT INTO schedule_reminders(schedule_id,recipient,due_at,acknowledged) VALUES(?,?,?,FALSE)",
                        id, recipient, input.startAt()-input.reminderMinutes()*60000L);
        }
        changed(channel);
        return db.queryForObject("SELECT * FROM channel_schedules WHERE id=?", ROW, id);
    }
    public void cancel(String user, long channel, long id, long revision) {
        lock(user, channel);
        if (owned(user,channel,id).revision()!=revision) throw new BusinessException(ErrorCode.CONFLICT);
        db.update("DELETE FROM channel_schedules WHERE id=?", id);
        changed(channel);
    }
    public List<Reminder> pending(String user) {
        return db.query("""
                SELECT s.*, r.id AS reminder_id,r.due_at FROM schedule_reminders r
                JOIN channel_schedules s ON s.id=r.schedule_id
                JOIN users u ON u.user_id=r.recipient
                JOIN channel_members cm ON cm.channel_id=s.channel_id AND cm.user_id=u.id
                WHERE r.recipient=? AND r.acknowledged=FALSE AND r.due_at<=? AND cm.left_at IS NULL
                ORDER BY r.due_at,r.id LIMIT 20
                """, (r,n) -> new Reminder(r.getLong("reminder_id"),r.getLong("due_at"),ROW.mapRow(r,n)), user, System.currentTimeMillis());
    }
    public void act(String user, long id, boolean snooze) {
        var found = db.queryForList("SELECT s.channel_id FROM schedule_reminders r JOIN channel_schedules s ON s.id=r.schedule_id WHERE r.id=? AND r.recipient=?", Long.class, id,user);
        if (found.isEmpty()) throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        lock(user,found.getFirst());
        // Recheck after taking the channel lock: edits/cancellation may have replaced this reminder.
        int count = snooze
            ? db.update("UPDATE schedule_reminders SET due_at=? WHERE id=? AND recipient=? AND acknowledged=FALSE AND due_at<=?", System.currentTimeMillis()+300000, id,user,System.currentTimeMillis())
            : db.update("UPDATE schedule_reminders SET acknowledged=TRUE WHERE id=? AND recipient=?", id,user);
        if (count==0) throw new BusinessException(ErrorCode.CONFLICT);
    }
    private void changed(long channel) {
        for (var member : members.findActiveMembers(channel))
            events.user(member.getUser().getUserId(),"/queue/channel-events",Map.of("channelId",channel,"kind","SCHEDULE"));
    }
}
