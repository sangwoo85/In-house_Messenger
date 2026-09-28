import { useMemo, useState } from 'react'
import { Avatar } from '@/components/Avatar'
import type { Department } from './organizations.api'
import type { DirectoryUser } from './users.api'

interface TreeProps {
  departments: Department[]
  users: DirectoryUser[]
  ascending: boolean
  onOpenChat: (userId: string) => void
}

/** 로그인/자리 비움 상태는 글자와 색을 함께 표시하여 색상만으로 구별하지 않도록 한다. */
function EmployeeRow({ user, onOpenChat }: { user: DirectoryUser; onOpenChat: (id: string) => void }): JSX.Element {
  const status = user.status === 'ONLINE' ? '온라인' : user.status === 'AWAY' ? '자리 비움' : '오프라인'
  return <button type="button" className="messenger-person" onDoubleClick={() => onOpenChat(user.userId)}
    onKeyDown={event => { if (event.key === 'Enter') onOpenChat(user.userId) }} title="더블클릭 또는 Enter로 대화 시작">
    <Avatar name={user.nickname} imageUrl={user.profileImageUrl} />
    <span className="messenger-room-copy"><strong>{user.nickname}</strong><small>{status}</small></span>
    <span className={`messenger-presence ${user.status.toLowerCase()}`} aria-label={status} />
  </button>
}

/** 외부 API의 parentId 관계를 그대로 표시하고, 부서별 접힘 상태를 메모리에 유지한다. */
export function OrganizationTree({ departments, users, ascending, onOpenChat }: TreeProps): JSX.Element {
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())
  const sortedUsers = useMemo(() => [...users].sort((a, b) => {
    const online = Number(b.status === 'ONLINE') - Number(a.status === 'ONLINE')
    return online || a.nickname.localeCompare(b.nickname, 'ko-KR') * (ascending ? 1 : -1)
  }), [users, ascending])
  const knownIds = new Set(departments.map(department => department.id))
  const roots = departments.filter(department => !department.parentId || !knownIds.has(department.parentId))
  const unassigned = sortedUsers.filter(user => !user.departmentId || !knownIds.has(user.departmentId))

  /** 특정 부서만 접거나 펼치고 다른 부서의 상태는 보존한다. */
  function toggle(id: string): void {
    setCollapsed(current => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  /** 잘못된 외부 데이터가 들어와도 순환 관계 때문에 렌더링이 멈추지 않게 한다. */
  function renderDepartment(department: Department, ancestors: string[] = []): JSX.Element | null {
    if (ancestors.includes(department.id) || ancestors.length >= 100) return null
    const members = sortedUsers.filter(user => user.departmentId === department.id)
    const children = departments.filter(child => child.parentId === department.id)
      .sort((a, b) => a.name.localeCompare(b.name, 'ko-KR'))
    const isCollapsed = collapsed.has(department.id)
    return <li key={department.id} className="messenger-organization-node">
      <button type="button" className="messenger-department-title" aria-expanded={!isCollapsed} onClick={() => toggle(department.id)}>
        <span>{isCollapsed ? '›' : '⌄'} {department.name}</span><span>{members.length > 0 ? members.length : ''}</span>
      </button>
      {!isCollapsed && <div className="messenger-organization-children">
        {members.map(user => <EmployeeRow key={user.userId} user={user} onOpenChat={onOpenChat} />)}
        {children.length > 0 && <ul>{children.map(child => renderDepartment(child, [...ancestors, department.id]))}</ul>}
        {!members.length && !children.length && <p className="messenger-sidebar-hint">소속 사용자가 없습니다.</p>}
      </div>}
    </li>
  }

  return <div>
    <ul aria-label="조직 계층">{roots.sort((a, b) => a.name.localeCompare(b.name, 'ko-KR')).map(department => renderDepartment(department))}</ul>
    {unassigned.length > 0 && <section><p className="messenger-list-heading">소속 미지정</p>{unassigned.map(user => <EmployeeRow key={user.userId} user={user} onOpenChat={onOpenChat} />)}</section>}
    {!users.length && !departments.length && <p className="messenger-sidebar-hint">표시할 조직과 사용자가 없습니다.</p>}
  </div>
}
