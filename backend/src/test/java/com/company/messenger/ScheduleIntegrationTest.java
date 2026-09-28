package com.company.messenger;
import com.company.messenger.domain.channel.*;
import com.company.messenger.domain.schedule.ScheduleService;
import com.company.messenger.domain.user.*;
import com.company.messenger.global.auth.*;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.external.InternalAuthClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:schedules;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@ActiveProfiles("test")
class ScheduleIntegrationTest {
 @Autowired ScheduleService schedules;
 @Autowired ChannelService channels;
 @Autowired UserService users;
 @Autowired JdbcTemplate db;
 @MockitoBean InternalAuthClient external;
 @MockitoBean PresenceService presence;
 @MockitoBean SessionRegistry sessions;
 @MockitoBean RefreshTokenStore refresh;
 long channel;
 @BeforeEach void setup() {
  when(external.fetchUsers()).thenReturn(List.of("alice","bob","outsider").stream().map(id -> new InternalAuthClient.ExternalDirectoryUser(id,id,null,"development","user")).toList());
  users.resolveDirectoryUsers(List.of("alice","bob","outsider"));
  channel=channels.createChannel("alice",new CreateChannelRequest("일정 테스트",ChannelType.GROUP,List.of("bob"))).id();
 }
 ScheduleService.Input input(String audience,boolean room,Long revision) {
  long start=System.currentTimeMillis()+300000;
  return new ScheduleService.Input("회의",start,start+3600000,room,room?"7층 중 회의실":null,audience,10,revision);
 }
 @Test void persistsAndDeliversMissedRemindersWithSnoozeAndAck() {
  var item=schedules.save("alice",channel,null,input("ROOM",true,null));
  assertThat(schedules.list("bob",channel)).contains(item);
  var reminder=schedules.pending("bob").stream().filter(r -> r.schedule().id()==item.id()).findFirst().orElseThrow();
  assertThat(reminder.schedule().location()).isEqualTo("7층 중 회의실");
  assertThatThrownBy(() -> schedules.act("outsider",reminder.id(),false)).isInstanceOf(BusinessException.class);
  schedules.act("bob",reminder.id(),true);
  assertThat(schedules.pending("bob")).noneMatch(r -> r.id()==reminder.id());
  db.update("UPDATE schedule_reminders SET due_at=? WHERE id=?",System.currentTimeMillis()-1,reminder.id());
  assertThat(schedules.pending("bob")).anyMatch(r -> r.id()==reminder.id());
  schedules.act("bob",reminder.id(),false);
  assertThat(schedules.pending("bob")).noneMatch(r -> r.id()==reminder.id());
 }
 @Test void protectsPrivateScheduleAndMemberPermissions() {
  var item=schedules.save("alice",channel,null,input("SELF",false,null));
  assertThat(schedules.list("bob",channel)).isEmpty();
  assertThat(schedules.pending("bob")).noneMatch(r -> r.schedule().id()==item.id());
  assertThatThrownBy(() -> schedules.save("bob",channel,item.id(),input("ROOM",false,item.revision()))).isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> schedules.cancel("bob",channel,item.id(),item.revision())).isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> schedules.list("outsider",channel)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> schedules.save("outsider",channel,null,input("ROOM",false,null))).isInstanceOf(BusinessException.class);
 }
 @Test void editsInvalidateStaleRemindersAndCancellationRemovesThem() {
  var item=schedules.save("alice",channel,null,input("ROOM",true,null));
  var old=schedules.pending("bob").stream().filter(r -> r.schedule().id()==item.id()).findFirst().orElseThrow();
  var changed=schedules.save("alice",channel,item.id(),input("SELF",false,item.revision()));
  assertThat(changed.location()).isNull();
  assertThat(changed.revision()).isEqualTo(2);
  assertThat(schedules.pending("bob")).noneMatch(r -> r.schedule().id()==item.id());
  assertThatThrownBy(() -> schedules.act("bob",old.id(),true)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> schedules.save("alice",channel,item.id(),input("ROOM",false,item.revision()))).isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> schedules.cancel("alice",channel,item.id(),item.revision())).isInstanceOf(BusinessException.class);
  schedules.cancel("alice",channel,item.id(),changed.revision());
  assertThat(schedules.list("alice",channel)).isEmpty();
  assertThat(schedules.pending("alice")).noneMatch(r -> r.schedule().id()==item.id());
 }
 @Test void departedMembersCannotReceiveOrActOnReminders() {
  var item=schedules.save("alice",channel,null,input("ROOM",false,null));
  var reminder=schedules.pending("bob").stream().filter(r -> r.schedule().id()==item.id()).findFirst().orElseThrow();
  channels.removeMember("bob",channel,"bob");
  assertThat(schedules.pending("bob")).noneMatch(r -> r.id()==reminder.id());
  assertThatThrownBy(() -> schedules.act("bob",reminder.id(),false)).isInstanceOf(BusinessException.class);
 }
 @Test void validatesEndAndLocationAndClearsUnusedLocation() {
  long start=System.currentTimeMillis()+60000;
  assertThatThrownBy(() -> schedules.save("alice",channel,null,new ScheduleService.Input("회의",start,start,false,null,"ROOM",0,null))).isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> schedules.save("alice",channel,null,new ScheduleService.Input("회의",start,start+60000,true,null,"ROOM",0,null))).isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> schedules.save("alice",channel,null,new ScheduleService.Input("회의",start,start+60000,true,"invalid","ROOM",0,null))).isInstanceOf(BusinessException.class);
  assertThatThrownBy(() -> schedules.save("alice",channel,null,new ScheduleService.Input("회의",start,start+60000,false,null,"ALL",0,null))).isInstanceOf(BusinessException.class);
  var item=schedules.save("alice",channel,null,new ScheduleService.Input("회의",start,start+86400000,false,"7층 중 회의실","ROOM",0,null));
  assertThat(item.location()).isNull();
  assertThat(item.endAt()-item.startAt()).isEqualTo(86400000);
 }
}
